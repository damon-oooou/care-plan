package com.careplan;

import com.careplan.entity.*;
import com.careplan.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.*;

@SpringBootApplication
@RestController
public class CarePlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarePlanApplication.class, args);
    }

    // ============================================================
    // 注入 Repository（代替之前的 HashMap）
    // ============================================================
    @Autowired private PatientRepository patientRepo;
    @Autowired private ProviderRepository providerRepo;
    @Autowired private CareOrderRepository orderRepo;
    @Autowired private CarePlanRepository carePlanRepo;

    @Value("${anthropic.api-key}")
    private String apiKey;

    // ============================================================
    // DTO（请求和响应的数据结构）
    // ============================================================

    public static class OrderRequest {
        public String patientFirstName;
        public String patientLastName;
        public String mrn;
        public String referringProvider;
        public String referringProviderNpi;
        public String primaryDiagnosis;
        public List<String> additionalDiagnoses;
        public String medicationName;
        public List<String> medicationHistory;
        public String patientRecords;
    }

    public static class OrderResponse {
        public Long id;
        public String patientName;
        public String mrn;
        public String providerName;
        public String providerNpi;
        public String primaryDiagnosis;
        public String medicationName;
        public String carePlan;
        public String status;
        public String createdAt;

        public OrderResponse(CareOrder order) {
            this.id = order.getId();
            this.patientName = order.getPatient().getFirstName() + " " + order.getPatient().getLastName();
            this.mrn = order.getPatient().getMrn();
            this.providerName = order.getProvider().getName();
            this.providerNpi = order.getProvider().getNpi();
            this.primaryDiagnosis = order.getPrimaryDiagnosis();
            this.medicationName = order.getMedicationName();
            this.carePlan = order.getCarePlan() != null ? order.getCarePlan().getContent() : null;
            this.status = order.getCarePlan() != null ? order.getCarePlan().getStatus() : "no_plan";
            this.createdAt = order.getCreatedAt().toString();
        }
    }

    // ============================================================
    // API 接口
    // ============================================================

    /**
     * POST /api/orders — 创建订单 + 生成 care plan
     */
    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {

        // 1. 查找或创建 Patient
        Patient patient = patientRepo.findByMrn(request.mrn)
                .orElseGet(() -> {
                    Patient p = new Patient();
                    p.setFirstName(request.patientFirstName);
                    p.setLastName(request.patientLastName);
                    p.setMrn(request.mrn);
                    return patientRepo.save(p);
                });

        // 2. 查找或创建 Provider
        Provider provider = providerRepo.findByNpi(request.referringProviderNpi)
                .orElseGet(() -> {
                    Provider prov = new Provider();
                    prov.setName(request.referringProvider);
                    prov.setNpi(request.referringProviderNpi);
                    return providerRepo.save(prov);
                });

        // 3. 创建 Order
        CareOrder order = new CareOrder();
        order.setPatient(patient);
        order.setProvider(provider);
        order.setPrimaryDiagnosis(request.primaryDiagnosis);
        order.setMedicationName(request.medicationName);
        order.setPatientRecords(request.patientRecords);

        if (request.additionalDiagnoses != null && !request.additionalDiagnoses.isEmpty()) {
            order.setAdditionalDiagnoses(String.join(",", request.additionalDiagnoses));
        }
        if (request.medicationHistory != null && !request.medicationHistory.isEmpty()) {
            order.setMedicationHistory(String.join(",", request.medicationHistory));
        }

        order = orderRepo.save(order);

        // 4. 创建 Care Plan（初始状态 pending）
        CarePlan carePlan = new CarePlan();
        carePlan.setOrder(order);
        carePlan.setStatus("pending");
        carePlanRepo.save(carePlan);

        // 5. 调用 LLM 生成 care plan
        String prompt = buildPrompt(request);
        try {
            carePlan.setStatus("processing");
            carePlanRepo.save(carePlan);

            String carePlanText = callLLM(prompt);

            carePlan.setContent(carePlanText);
            carePlan.setStatus("completed");
            carePlanRepo.save(carePlan);
        } catch (Exception e) {
            carePlan.setStatus("failed");
            carePlanRepo.save(carePlan);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "LLM 调用失败: " + e.getMessage()));
        }

        // 重新加载 order 以包含 carePlan
        order.setCarePlan(carePlan);

        return ResponseEntity.ok(new OrderResponse(order));
    }

    /**
     * GET /api/orders — 查看所有订单
     */
    @GetMapping("/api/orders")
    public List<OrderResponse> listOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponse::new)
                .toList();
    }

    /**
     * GET /api/orders/{id} — 查看单个订单
     */
    @GetMapping("/api/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        return orderRepo.findById(id)
                .map(order -> ResponseEntity.ok(new OrderResponse(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/patients — 查看所有患者
     */
    @GetMapping("/api/patients")
    public List<Patient> listPatients() {
        return patientRepo.findAll();
    }

    /**
     * GET /api/providers — 查看所有 Provider
     */
    @GetMapping("/api/providers")
    public List<Provider> listProviders() {
        return providerRepo.findAll();
    }

    // ============================================================
    // LLM 调用
    // ============================================================

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
