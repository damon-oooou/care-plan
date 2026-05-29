# Care Plan Generator

Specialty pharmacy Care Plan auto-generation system. Medical assistants enter patient information, the system calls an LLM to automatically generate a Care Plan. Supports multi-source intake — hospitals and clinics with different data formats are normalized through the Adapter pattern.

## Tech Stack

- Java 17 + Spring Boot 3.4
- Spring Data JPA + PostgreSQL
- Redis (message queue)
- Docker Compose (Redis container)
- Anthropic Claude API (claude-haiku-4-5-20251001)
- Frontend: vanilla HTML/CSS/JS
- Testing: JUnit 5 + Mockito + H2

## Project Structure

```
care-plan/
├── .github/workflows/
│   └── ci.yml                                     # GitHub Actions CI
├── docker-compose.yml                             # Redis container
├── sql/
│   ├── 01_schema.sql                              # Table creation (4 tables)
│   ├── 02_mock_data.sql                           # Mock data
│   └── 03_add_source_fields.sql                   # Add source tracking columns
├── src/main/java/com/careplan/
│   ├── CarePlanApplication.java                   # Entry point (main method only)
│   ├── adapter/                                   # Multi-source intake (Adapter pattern)
│   │   ├── BaseIntakeAdapter.java                 # Abstract base: parse → transform → validate
│   │   ├── AdapterRouter.java                     # Auto-routes by sourceSystem
│   │   ├── ClinicBAdapter.java                    # Clinic B: JSON, MM/dd/yyyy, SIG abbreviations
│   │   ├── HospitalAAdapter.java                  # Hospital A: JSON, yyyy-MM-dd, full field names
│   │   ├── AdapterParseException.java             # Parse-stage errors
│   │   └── AdapterValidationException.java        # Validation-stage errors
│   ├── controller/
│   │   └── OrderController.java                   # REST API endpoints
│   ├── service/
│   │   ├── OrderService.java                      # Business logic
│   │   └── RedisQueueService.java                 # Redis queue wrapper
│   ├── dto/
│   │   ├── InternalOrder.java                     # Unified internal format (all sources → this)
│   │   ├── ExternalOrderRequest.java              # External intake request wrapper
│   │   ├── OrderRequest.java                      # Frontend form request
│   │   └── OrderResponse.java                     # Response format
│   ├── entity/                                    # JPA entities (database tables)
│   │   ├── Patient.java
│   │   ├── Provider.java
│   │   ├── CareOrder.java
│   │   └── CarePlan.java
│   ├── repository/                                # JPA Repository (database queries)
│   │   ├── PatientRepository.java
│   │   ├── ProviderRepository.java
│   │   ├── CareOrderRepository.java
│   │   └── CarePlanRepository.java
│   ├── exception/                                 # Unified error handling
│   │   ├── BaseAppException.java                  # Base exception class
│   │   ├── ValidationError.java                   # 400 - input format errors
│   │   ├── BlockError.java                        # 409 - business rule blocks
│   │   ├── WarningException.java                  # 200 - warnings (user can confirm)
│   │   └── GlobalExceptionHandler.java            # Catches all exceptions, unified JSON
│   └── worker/
│       └── CarePlanWorker.java                    # Background worker (Redis → LLM → DB)
├── src/main/resources/
│   ├── application.properties                     # App config (PostgreSQL, Redis, logging)
│   └── static/
│       └── index.html                             # Frontend page
├── src/test/java/com/careplan/
│   ├── adapter/
│   │   ├── ClinicBAdapterTest.java                # Clinic B adapter tests (18 tests)
│   │   └── HospitalAAdapterTest.java              # Hospital A adapter tests (13 tests)
│   ├── service/
│   │   └── OrderServiceTest.java                  # Unit tests (17 tests, Mockito)
│   └── controller/
│       └── OrderControllerIntegrationTest.java    # Integration tests (10 tests, H2)
├── src/test/resources/
│   └── application.properties                     # Test config (H2 in-memory DB)
├── docs/
│   └── care-plan-design-doc.md
├── .env                                           # Environment variables (not in Git)
├── .env.example
├── .gitignore
├── pom.xml
└── README.md
```

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Two entry paths, same downstream logic          │
└─────────────────────────────────────────────────┘

Path 1: Frontend form                Path 2: External data sources
    │                                    │
POST /api/orders                    POST /api/external-orders
    │                                    │
OrderRequest                        AdapterRouter
    │                                    │
    │                                ClinicBAdapter / HospitalAAdapter
    │                                    │
    │                                parse → transform → validate
    │                                    │
    │                                InternalOrder
    │                                    │
    └──────────── OrderService ──────────┘
                      │
            Duplicate detection (NPI, MRN, Order)
                ├─ Block (409)
                ├─ Warning (200)
                └─ Pass
                      │
              Save to database
                      │
              Push to Redis queue
                      │
              Return 202 immediately
                      │
            CarePlanWorker (background)
                      │
            BLPOP → Claude API → Save result
                ├─ Success → completed
                └─ Failure → Retry (max 3)
                      │
            Frontend polls GET /api/careplan/{id}/status
```

## Multi-Source Intake

Different hospitals and clinics send data in different formats. The Adapter pattern normalizes everything into `InternalOrder` before hitting business logic.

### How it works

1. External system sends JSON to `POST /api/external-orders` with `sourceSystem` identifier
2. `AdapterRouter` finds the matching Adapter (auto-discovered via Spring `@Component`)
3. Adapter runs: `parse()` → `transform()` → `validate()`
4. Output: `InternalOrder` → reuses existing duplicate detection, DB save, Redis queue

### Adding a new data source

Add one file: `adapter/NewHospitalAdapter.java` with `@Component`. No other code changes needed. Spring auto-discovers it.

### Current adapters

| Source | Adapter | Date Format | Frequency | Field Style |
|--------|---------|-------------|-----------|-------------|
| Clinic B | `ClinicBAdapter` | MM/dd/yyyy | SIG abbreviations (BID → twice daily) | Short (fname, lname, sig) |
| Hospital A | `HospitalAAdapter` | yyyy-MM-dd | Full text (once daily at bedtime) | Verbose (given_name, family_name) |

### Data traceability

Every external order stores: `source_system` (which source), `external_order_id` (their order number), `raw_data` (original JSON for debugging).

## Database Design

4 tables:

- **patient** — first_name, last_name, mrn (unique), date_of_birth
- **provider** — name, npi (unique)
- **care_order** — links patient + provider, contains diagnosis and medication info, plus source_system, external_order_id, raw_data for external orders
- **care_plan** — LLM-generated content, linked to order, with status tracking

Care Plan status flow: `pending → processing → completed / failed`

## Duplicate Detection

| Scenario | Result | HTTP |
|----------|--------|------|
| NPI exists + same name | Reuse provider | — |
| NPI exists + different name | Block | 409 |
| MRN exists + same name + same DOB | Reuse patient | — |
| MRN exists + name or DOB mismatch | Warning | 200 |
| Same name + DOB, different MRN | Warning | 200 |
| Same patient + same medication + same day | Block | 409 |
| Same patient + same medication + different day | Warning | 200 |

Warnings can be skipped by sending `confirmWarnings: true`.

Duplicate detection works identically for both frontend orders and external orders.

## Error Handling

All errors return a unified JSON format:

```json
{
  "success": false,
  "error": {
    "type": "block",
    "code": "npi_conflict",
    "message": "NPI 1234567890 already belongs to Dr. Chen",
    "detail": "NPI is a national license number, one NPI can only belong to one provider"
  },
  "timestamp": "2026-05-19T10:30:00"
}
```

Five exception types:
- `ValidationError` (400) — input format errors
- `BlockError` (409) — business rule blocks
- `WarningException` (200) — warnings, user can confirm
- `AdapterParseException` (400) — external data parse failure
- `AdapterValidationException` (400) — external data validation failure

All handled by `GlobalExceptionHandler`.

## Quick Start

### 1. Prerequisites

- Java 17
- Maven 3.8+
- PostgreSQL 16+
- Docker Desktop

### 2. Create database and import data

```bash
psql -U postgres -c "CREATE DATABASE careplan;"
psql -U postgres -d careplan -f sql/01_schema.sql
psql -U postgres -d careplan -f sql/02_mock_data.sql
psql -U postgres -d careplan -f sql/03_add_source_fields.sql
```

### 3. Configure environment variables

Copy `.env.example` to `.env` and fill in real values:

```
ANTHROPIC_API_KEY=sk-ant-your-key-here
DB_NAME=careplan
DB_USERNAME=postgres
DB_PASSWORD=your-password
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 4. Start Redis

```bash
docker-compose up -d
```

### 5. Start the application

```bash
mvn spring-boot:run
```

Open http://localhost:8080

### 6. Run tests

```bash
mvn clean test
```

58 tests (17 unit + 10 integration + 31 adapter), uses H2 in-memory database, no PostgreSQL or Redis needed.

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/orders | Create order from frontend form |
| POST | /api/external-orders | Create order from external data source (Adapter pattern) |
| GET  | /api/orders | List all orders (newest first) |
| GET  | /api/orders/{id} | Get single order with care plan |
| GET  | /api/careplan/{id}/status | Polling: returns status and content |
| GET  | /api/patients | List all patients |
| GET  | /api/providers | List all providers |

### POST /api/orders response examples

Success (202):
```json
{
  "success": true,
  "message": "Received, Care Plan is being generated",
  "orderId": 17,
  "carePlanId": 13,
  "status": "pending"
}
```

Block (409):
```json
{
  "success": false,
  "error": {
    "type": "block",
    "code": "duplicate_order",
    "message": "Patient already has a Metformin order today"
  },
  "timestamp": "2026-05-19T10:30:00"
}
```

Warning (200):
```json
{
  "success": false,
  "error": {
    "type": "warning",
    "code": "needs_confirmation",
    "message": "Issues detected, please confirm to continue",
    "warnings": ["MRN 123456 already exists, belongs to John Doe..."]
  },
  "timestamp": "2026-05-19T10:30:00"
}
```

### POST /api/external-orders

Request:
```json
{
  "sourceSystem": "clinic_b",
  "rawData": "{\"ref_no\":\"CB-001\",\"doc\":{\"full_name\":\"Dr. Sarah Lin\",\"license\":\"1234567890\"},\"pt\":{\"fname\":\"John\",\"lname\":\"Doe\",\"record_id\":\"998877\",\"born\":\"03/15/1985\"},\"rx\":{\"med\":\"Metformin\",\"strength\":\"500mg\",\"sig\":\"BID\",\"condition\":\"Type 2 Diabetes\"}}"
}
```

Success (202):
```json
{
  "success": true,
  "message": "Received, Care Plan is being generated",
  "orderId": 48,
  "carePlanId": 48,
  "status": "pending"
}
```

## CI/CD

GitHub Actions runs all 58 tests on every push to `main` and on every pull request. Tests must pass before merging.

Config: `.github/workflows/ci.yml`

## Logging

SLF4J Logger with file output. Logs written to `logs/careplan.log`.

Worker logs example:
```
INFO  CarePlanWorker - Received task: carePlanId = 45
INFO  CarePlanWorker - Status updated to processing, carePlanId = 45
INFO  CarePlanWorker - Attempt 1/3, calling LLM...
INFO  CarePlanWorker - Done! carePlanId = 45 (succeeded on attempt 1)
```

Error handling logs:
```
ERROR GlobalExceptionHandler - [npi_conflict] NPI 1234567890 already belongs to Dr. Chen
WARN  GlobalExceptionHandler - [needs_confirmation] Issues detected, please confirm to continue
ERROR GlobalExceptionHandler - [adapter_parse] [clinic_b] Parse failed: Invalid JSON
```

## Version History

- **v11** — Multi-source intake: Adapter pattern, InternalOrder, ClinicBAdapter, HospitalAAdapter, 31 adapter tests
- **v10** — GitHub Actions CI: auto-run all tests on push/PR
- **v9** — Unit tests (17) + Integration tests (10), H2 test database
- **v8** — Unified error handling: BaseAppException, BlockError, WarningException, GlobalExceptionHandler
- **v7** — Duplicate detection: Provider NPI, Patient MRN/DOB, Order same-day
- **v6** — Layered architecture: Controller / Service / DTO separation
- **v5** — Frontend polling: auto-updates when care plan is ready
- **v4** — Worker with retry: consume Redis queue, call LLM, exponential backoff
- **v3** — Async architecture: Redis queue, instant response
- **v2** — PostgreSQL + JPA, Care Plan status tracking
- **v1** — MVP: in-memory HashMap, synchronous LLM call
