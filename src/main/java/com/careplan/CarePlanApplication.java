package com.careplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
public class CarePlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarePlanApplication.class, args);
    }

    // ============================================================
    // 内存存储（代替数据库，HashMap 就够了）
    // ============================================================
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    // ============================================================
    // 数据结构（全部用内部类，不分文件）
    // ============================================================

    // --- 前端提交的请求体 ---
    public static class OrderRequest {
        public String patientFirstName;
        public String patientLastName;
        public String mrn;                      // 6位数字
        public String referringProvider;
        public String referringProviderNpi;      // 10位数字
        public String primaryDiagnosis;          // ICD-10
        public List<String> additionalDiagnoses; // ICD-10 列表
        public String medicationName;
        public List<String> medicationHistory;
        public String patientRecords;            // 纯文本，MVP 不搞文件上传
    }

    // --- 存储的订单 ---
    public static class Order {
        public String id;
        public OrderRequest request;
        public String carePlan;                  // LLM 生成的 care plan 文本
        public LocalDateTime createdAt;
    }

    // --- 返回给前端的响应 ---
    public static class OrderResponse {
        public String id;
        public String carePlan;
        public String createdAt;

        public OrderResponse(Order order) {
            this.id = order.id;
            this.carePlan = order.carePlan;
            this.createdAt = order.createdAt.toString();
        }
    }

    // ============================================================
    // API 接口
    // ============================================================

    /**
     * POST /api/orders
     * 接收表单数据 → 调用 LLM 生成 care plan → 存内存 → 返回结果
     * 同步调用，用户提交后等 LLM 返回，直接显示 care plan
     */
    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        // 1. 组装 prompt
        String prompt = buildPrompt(request);

        // 2. 调用 LLM 生成 care plan
        String carePlan;
        try {
            carePlan = callLLM(prompt);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "LLM 调用失败: " + e.getMessage()));
        }

        // 3. 存到内存
        Order order = new Order();
        order.id = UUID.randomUUID().toString().substring(0, 8);
        order.request = request;
        order.carePlan = carePlan;
        order.createdAt = LocalDateTime.now();
        orders.put(order.id, order);

        // 4. 返回
        return ResponseEntity.ok(new OrderResponse(order));
    }

    /**
     * GET /api/orders
     * 查看所有订单（调试用）
     */
    @GetMapping("/api/orders")
    public List<OrderResponse> listOrders() {
        return orders.values().stream()
                .map(OrderResponse::new)
                .toList();
    }

    // ============================================================
    // LLM 调用（用 Anthropic Claude API）
    // ============================================================

    // Anthropic API 的请求/响应结构
    public static class ClaudeMessage {
        public String role;
        public String content;
        public ClaudeMessage() {}
        public ClaudeMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class ClaudeRequest {
        public String model = "claude-haiku-4-5-20251001";
        @JsonProperty("max_tokens")
        public int maxTokens = 2000;
        public List<ClaudeMessage> messages;
        public String system;
    }

    public static class ClaudeContentBlock {
        public String type;
        public String text;
    }

    public static class ClaudeResponse {
        public List<ClaudeContentBlock> content;
    }

    @org.springframework.beans.factory.annotation.Value("${anthropic.api-key}")
    private String apiKey;
    
    private String callLLM(String prompt) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        ClaudeRequest claudeRequest = new ClaudeRequest();
        claudeRequest.system = "You are a clinical pharmacist. Generate a professional care plan based on the patient information provided. The care plan must include these 4 sections:\n1. Problem List\n2. Goals\n3. Pharmacist Interventions\n4. Monitoring Plan\n\nBe specific, clinically accurate, and concise.";
        claudeRequest.messages = List.of(new ClaudeMessage("user", prompt));

        HttpEntity<ClaudeRequest> entity = new HttpEntity<>(claudeRequest, headers);

        ResponseEntity<ClaudeResponse> response = restTemplate.exchange(
                "https://api.anthropic.com/v1/messages",
                HttpMethod.POST,
                entity,
                ClaudeResponse.class
        );

        ClaudeResponse body = response.getBody();
        if (body != null && body.content != null && !body.content.isEmpty()) {
            return body.content.stream()
                    .filter(block -> "text".equals(block.type))
                    .map(block -> block.text)
                    .reduce("", (a, b) -> a + b);
        }
        throw new RuntimeException("LLM 返回为空");
    }

    // ============================================================
    // Prompt 组装
    // ============================================================

    private String buildPrompt(OrderRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please generate a care plan for the following patient:\n\n");
        sb.append("Patient: ").append(req.patientFirstName).append(" ").append(req.patientLastName).append("\n");
        sb.append("MRN: ").append(req.mrn).append("\n");
        sb.append("Referring Provider: ").append(req.referringProvider).append("\n");
        sb.append("Provider NPI: ").append(req.referringProviderNpi).append("\n");
        sb.append("Primary Diagnosis (ICD-10): ").append(req.primaryDiagnosis).append("\n");
        sb.append("Medication: ").append(req.medicationName).append("\n");

        if (req.additionalDiagnoses != null && !req.additionalDiagnoses.isEmpty()) {
            sb.append("Additional Diagnoses: ").append(String.join(", ", req.additionalDiagnoses)).append("\n");
        }
        if (req.medicationHistory != null && !req.medicationHistory.isEmpty()) {
            sb.append("Medication History: ").append(String.join(", ", req.medicationHistory)).append("\n");
        }
        if (req.patientRecords != null && !req.patientRecords.isBlank()) {
            sb.append("Patient Records:\n").append(req.patientRecords).append("\n");
        }

        return sb.toString();
    }
}
