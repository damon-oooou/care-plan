# Care Plan Generator

专科药房 Care Plan 自动生成系统。医疗助理填写患者信息，系统调用 LLM 自动生成 Care Plan。

## 技术栈

- Java 17 + Spring Boot 3.3
- Spring Data JPA + PostgreSQL
- Redis（消息队列）
- Docker Compose（Redis 容器）
- Anthropic Claude API (claude-haiku-4-5-20251001)
- 前端：原生 HTML/CSS/JS

## 项目结构

```
care-plan/
├── docker-compose.yml                             # Redis 容器
├── sql/
│   ├── 01_schema.sql                              # 建表语句（4 张表）
│   └── 02_mock_data.sql                           # Mock 数据
├── src/main/java/com/careplan/
│   ├── CarePlanApplication.java                   # 主程序 + REST API
│   ├── entity/                                    # JPA 实体
│   │   ├── Patient.java
│   │   ├── Provider.java
│   │   ├── CareOrder.java
│   │   └── CarePlan.java
│   └── repository/                                # JPA Repository
│       ├── PatientRepository.java
│       ├── ProviderRepository.java
│       ├── CareOrderRepository.java
│       └── CarePlanRepository.java
├── src/main/resources/
│   ├── application.properties
│   └── static/
│       └── index.html                             # 前端页面
├── docs/
│   └── care-plan-design-doc.md
├── .env                                           # 环境变量（不上传 GitHub）
├── .env.example                                   # 环境变量模板
├── .gitignore
├── pom.xml
└── README.md
```

## 架构

```
用户提交表单
    ↓
POST /api/orders
    ↓
存数据库（CarePlan status = pending）
    ↓
推入 Redis 队列（careplan:queue）
    ↓
立刻返回 202 "已收到"
    ↓
（TODO: Worker 从队列取任务 → 调用 LLM → 更新数据库 status = completed）
```

## 数据库设计

4 张表：

- **patient** — 患者（first_name, last_name, mrn, date_of_birth）
- **provider** — 处方医生（name, npi）
- **care_order** — 订单（关联 patient + provider，含诊断、用药信息）
- **care_plan** — LLM 生成的 Care Plan（关联 order，含 status 状态跟踪）

Care Plan 状态流转：`pending → processing → completed / failed`

## 快速启动

### 1. 前提条件

- Java 17+
- Maven 3.8+
- PostgreSQL 16+
- Docker Desktop

### 2. 创建数据库并导入数据

```bash
psql -U postgres -c "CREATE DATABASE careplan;"
psql -U postgres -d careplan -f sql/01_schema.sql
psql -U postgres -d careplan -f sql/02_mock_data.sql
```

### 3. 配置环境变量

复制 `.env.example` 为 `.env`，填入真实值：

```
ANTHROPIC_API_KEY=sk-ant-your-key-here
DB_NAME=careplan
DB_USERNAME=postgres
DB_PASSWORD=your-password
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 4. 启动 Redis

```bash
docker-compose up -d
```

### 5. 启动应用

```bash
mvn spring-boot:run
```

打开 http://localhost:8080

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/orders | 创建订单，存 pending，推入 Redis 队列，立刻返回 |
| GET  | /api/orders | 查看所有订单（按时间倒序） |
| GET  | /api/orders/{id} | 查看单个订单及 Care Plan |
| GET  | /api/careplan/{id}/status | 轮询用：返回 status 和 content |
| GET  | /api/patients | 查看所有患者 |
| GET  | /api/providers | 查看所有 Provider |

### POST /api/orders 返回示例

```json
{
  "message": "已收到，Care Plan 正在生成中",
  "orderId": 17,
  "carePlanId": 13,
  "status": "pending"
}
```

## 版本历史

## 版本历史
- **v5** — 前端 Polling：每 3 秒轮询状态 API，自动显示 care plan
- **v4** — Worker 消费 Redis 队列，调用 LLM，写回数据库，失败重试（最多 3 次，指数退避）
- **v3** — 异步架构：Redis 队列，提交后立刻返回
- **v2** — PostgreSQL + JPA，Care Plan 状态跟踪
- **v1** — MVP，内存存储（HashMap），同步 LLM 调用
