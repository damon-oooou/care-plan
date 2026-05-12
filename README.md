# Care Plan Generator — MVP

最小可运行版本：前端表单 → Spring Boot API → 调用 Claude LLM → 生成 Care Plan → 下载

## 项目结构

```
care-plan/
├── pom.xml                                          # Maven 配置，只依赖 spring-boot-starter-web
├── src/main/java/com/careplan/
│   └── CarePlanApplication.java                     # 全部后端代码（一个文件搞定）
├── src/main/resources/
│   ├── application.properties                       # 配置文件
│   └── static/
│       └── index.html                               # 前端页面
└── README.md
```

## 快速启动

### 1. 前提条件
- Java 17+
- Maven 3.8+（或者用项目自带的 mvnw）
- 一个 Anthropic API Key

### 2. 设置 API Key

**Windows PowerShell:**
```powershell
$env:ANTHROPIC_API_KEY="sk-ant-your-key-here"
```

**Mac / Linux:**
```bash
export ANTHROPIC_API_KEY="sk-ant-your-key-here"
```

### 3. 启动项目

```bash
mvn spring-boot:run
```

### 4. 打开浏览器

访问 http://localhost:8080 ，填写表单，点击 Generate Care Plan。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/orders | 提交订单，生成 care plan |
| GET  | /api/orders | 查看所有订单（调试用） |

### POST /api/orders 请求体示例

```json
{
  "patientFirstName": "John",
  "patientLastName": "Doe",
  "mrn": "123456",
  "referringProvider": "Dr. Smith",
  "referringProviderNpi": "1234567890",
  "primaryDiagnosis": "E11.65",
  "medicationName": "Metformin",
  "additionalDiagnoses": ["I10", "E78.5"],
  "medicationHistory": ["Lisinopril", "Atorvastatin"],
  "patientRecords": "Patient has a history of..."
}
```

## 当前限制（MVP）

- 数据存在内存里，重启就没了
- 没有输入校验（不检查 MRN 6位、NPI 10位等）
- 没有重复检测
- 没有用户认证
- LLM 调用是同步的，网络慢的时候要等

这些都会在后续版本逐步加上。
