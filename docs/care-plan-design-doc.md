# Care Plan 自动生成系统 — 设计文档

## 1. 项目概述

### 1.1 客户
专科药房（Specialty Pharmacy），CVS 医疗工作者使用。

### 1.2 业务背景
药剂师目前需要手动查看患者病历并生成 Care Plan，每位患者耗时 20-40 分钟。此流程是合规要求，直接关系到 Medicare 和药企的报销。由于人手严重不足，积压严重，亟需自动化。

### 1.3 核心目标
构建一个 Web 表单系统，允许医疗助理录入患者临床信息，系统调用 LLM 自动生成 Care Plan，并支持导出报告供药企报告使用。

### 1.4 用户
CVS 的医疗工作者（pharmacist / medical assistant）。患者不直接接触此系统。医疗工作者开药时需要生成 Care Plan，打印后交给患者。

---

## 2. 数据模型

### 2.1 Patient（患者）

| 字段 | 类型 | 约束 |
|---|---|---|
| firstName | String | 必填 |
| lastName | String | 必填 |
| mrn | String (6位数字) | 必填，唯一 |
| dateOfBirth | LocalDate | 用于重复检测 |

### 2.2 Provider（处方医生）

| 字段 | 类型 | 约束 |
|---|---|---|
| name | String | 必填 |
| npi | String (10位数字) | 必填，全局唯一 |

> **规则：NPI 是唯一标识。** 同一 NPI 只能对应一个 Provider 名字，若 NPI 相同但 Provider 名字不同，必须阻止提交（ERROR）。

### 2.3 Order（订单 / Care Plan 请求）

| 字段 | 类型 | 约束 |
|---|---|---|
| id | Long / UUID | 主键 |
| patient | Patient | 外键，必填 |
| referringProvider | Provider | 外键，必填 |
| primaryDiagnosis | String (ICD-10) | 必填，格式校验 |
| additionalDiagnoses | List\<String\> | ICD-10 codes |
| medicationName | String | 必填 |
| medicationHistory | List\<String\> | 可选 |
| patientRecords | String 或 文件(PDF) | 可选 |
| createdAt | LocalDateTime | 系统生成 |

### 2.4 CarePlan（生成结果）

| 字段 | 类型 | 约束 |
|---|---|---|
| id | Long / UUID | 主键 |
| order | Order | 一对一 |
| problemList | String / Text | LLM 生成 |
| goals | String / Text | LLM 生成 |
| pharmacistInterventions | String / Text | LLM 生成 |
| monitoringPlan | String / Text | LLM 生成 |
| generatedAt | LocalDateTime | 系统生成 |

> **一个 Care Plan 对应一个订单（一种药物）。**

---

## 3. 重复检测规则

### 3.1 订单 / 患者重复检测

| 场景 | 处理方式 | 原因 |
|---|---|---|
| 同一患者 + 同一药物 + 同一天 | ❌ ERROR — 阻止提交 | 确定是重复提交 |
| 同一患者 + 同一药物 + 不同天 | ⚠️ WARNING — 可确认继续 | 可能是续方 |
| MRN 相同 + 名字或 DOB 不同 | ⚠️ WARNING — 可确认继续 | 可能是录入错误 |
| 名字 + DOB 相同 + MRN 不同 | ⚠️ WARNING — 可确认继续 | 可能是同一人 |
| NPI 相同 + Provider 名字不同 | ❌ ERROR — 必须修正 | NPI 是唯一标识 |

### 3.2 实现策略
- 在 Service 层进行校验，返回结构化的校验结果（errors / warnings）。
- WARNING 类型：前端展示警告弹窗，用户可确认后继续提交（请求中携带 `confirmWarnings=true`）。
- ERROR 类型：后端直接拒绝，返回 400。

---

## 4. 功能清单

| 功能 | 优先级 | 说明 |
|---|---|---|
| 患者/订单重复检测 | ✅ 必须 | 不能打乱现有工作流 |
| Care Plan 生成（LLM） | ✅ 必须 | 核心价值 |
| Provider 重复检测 | ✅ 必须 | 影响 pharma 报告 |
| 导出报告 | ✅ 必须 | pharma 报告需要 |
| Care Plan 下载 | ✅ 必须 | 用户需要上传到他们的系统 |

---

## 5. 技术架构（Java Spring Boot）

### 5.1 技术栈

| 层级 | 技术选型 |
|---|---|
| 后端框架 | Spring Boot 3.x (Java 17+) |
| Web 层 | Spring MVC (REST API) |
| 数据持久化 | Spring Data JPA + Hibernate |
| 数据库 | PostgreSQL |
| 数据校验 | Jakarta Bean Validation (Hibernate Validator) |
| 文件上传 | Spring MultipartFile |
| LLM 集成 | Spring AI 或 RestTemplate/WebClient 调用 LLM API |
| 导出 | Apache POI (Excel) / OpenPDF (PDF) |
| 测试 | JUnit 5 + Mockito + Spring Boot Test |
| 构建工具 | Maven 或 Gradle |
| 前端 | Thymeleaf / 或独立前端 (React) 通过 API 对接 |

### 5.2 模块划分

```
com.careplanner
├── controller/          # REST Controller
│   ├── OrderController
│   ├── PatientController
│   ├── ProviderController
│   └── ExportController
├── service/             # 业务逻辑
│   ├── OrderService
│   ├── DuplicateDetectionService
│   ├── CarePlanGenerationService  # LLM 调用
│   ├── PatientService
│   ├── ProviderService
│   └── ExportService
├── repository/          # JPA Repository
│   ├── OrderRepository
│   ├── PatientRepository
│   ├── ProviderRepository
│   └── CarePlanRepository
├── model/               # JPA Entity
│   ├── Patient
│   ├── Provider
│   ├── Order
│   └── CarePlan
├── dto/                 # Request / Response DTO
│   ├── OrderRequest
│   ├── OrderResponse
│   ├── ValidationResult
│   └── CarePlanResponse
├── validation/          # 自定义校验器
│   ├── ICD10Validator
│   ├── NPIValidator
│   └── MRNValidator
├── exception/           # 全局异常处理
│   └── GlobalExceptionHandler
└── config/              # 配置
    ├── LLMConfig
    └── AppConfig
```

### 5.3 关键 API 设计

```
POST   /api/orders              创建订单（含重复检测）
POST   /api/orders/{id}/care-plan   触发 Care Plan 生成
GET    /api/orders/{id}/care-plan   获取 Care Plan
GET    /api/orders/{id}/care-plan/download   下载 Care Plan (txt/pdf)
GET    /api/patients?search=xxx  搜索患者
GET    /api/providers?npi=xxx    查询 Provider
POST   /api/export/pharma-report 导出 pharma 报告
```

---

## 6. 核心流程

### 6.1 订单提交流程

```
用户填写表单
    ↓
前端基础校验（必填、格式）
    ↓
POST /api/orders
    ↓
后端 Bean Validation 校验
    ↓
DuplicateDetectionService 重复检测
    ├─ ERROR → 返回 400 + 错误信息
    ├─ WARNING（未确认）→ 返回 409 + 警告列表
    └─ PASS 或 WARNING（已确认）
        ↓
    保存 Patient / Provider / Order
        ↓
    CarePlanGenerationService 调用 LLM
        ↓
    保存 CarePlan，返回结果
```

### 6.2 Care Plan 生成（LLM 调用）

- 将订单中的患者信息、诊断、用药史、病历等组装成 prompt。
- 调用 LLM API（如 Claude / OpenAI），要求输出包含四个部分：
  1. **Problem List** — 患者问题列表
  2. **Goals** — 治疗目标
  3. **Pharmacist Interventions** — 药剂师干预措施
  4. **Monitoring Plan** — 监测计划
- 解析 LLM 响应，结构化存储。
- 提供下载为文本文件 / PDF 的功能。

---

## 7. 数据校验规则

| 字段 | 校验规则 |
|---|---|
| Patient First/Last Name | 非空，仅字母和常见字符 |
| MRN | 恰好 6 位数字，全局唯一 |
| NPI | 恰好 10 位数字，Luhn 校验（可选） |
| Primary Diagnosis | ICD-10 格式（字母 + 数字，如 `E11.65`） |
| Additional Diagnoses | 每项均为合法 ICD-10 |
| Medication Name | 非空 |
| Patient Records (文件) | 仅允许 PDF / TXT，大小限制 |

---

## 8. Production-Ready 要求

| 要求 | 实现方式 |
|---|---|
| 所有输入必须校验 | Jakarta Bean Validation + 自定义 ConstraintValidator |
| 完整性规则保证一致性 | JPA 唯一约束 + Service 层业务校验 |
| 错误安全、清晰、可控 | `@RestControllerAdvice` 全局异常处理，统一错误响应格式 |
| 代码模块化可导航 | 分层架构（Controller → Service → Repository） |
| 关键逻辑有自动化测试覆盖 | JUnit 5 单元测试 + Spring Boot 集成测试 |
| 项目开箱即用 | Docker Compose（App + PostgreSQL），README 包含启动步骤 |

---

## 9. 后续扩展（可选）

- 用户认证（Spring Security + JWT）
- 操作审计日志
- Care Plan 历史版本对比
- 批量导入患者数据
- 对接外部 ICD-10 / NPI 校验 API
