package com.careplan.worker;

import com.careplan.entity.CarePlan;
import com.careplan.entity.CareOrder;
import com.careplan.repository.CarePlanRepository;
import com.careplan.repository.CareOrderRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Worker：后台线程，不断从 Redis 队列取任务，调 LLM，写回数据库。
 *
 * 重试策略：最多 3 次，指数退避（2秒 → 4秒 → 8秒）
 *
 * 流程：
 *   1. BLPOP 从 Redis 队列 "careplan:queue" 取一个 carePlanId
 *   2. 从数据库读 CarePlan + 关联的 Order
 *   3. 组装 prompt → 调用 Claude API
 *   4. 成功 → status = completed，写入 content
 *      失败 → 重试最多 3 次，全部失败 → status = failed
 *   5. 回到第 1 步
 */
@Component
public class CarePlanWorker implements CommandLineRunner {

    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private CareOrderRepository orderRepo;
    @Autowired private StringRedisTemplate redisTemplate;

    @Value("${anthropic.api-key}")
    private String apiKey;

    private static final String QUEUE_NAME = "careplan:queue";
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000;  // 2秒，指数退避基数

    @Override
    public void run(String... args) {
        Thread workerThread = new Thread(this::processQueue, "careplan-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        System.out.println("========================================");
        System.out.println("CarePlan Worker 已启动，等待队列任务...");
        System.out.println("重试策略: 最多 " + MAX_RETRIES + " 次，指数退避");
        System.out.println("========================================");
    }

    // ============================================================
    // 主循环
    // ============================================================

    private void processQueue() {
        while (true) {
            try {
                String carePlanIdStr = redisTemplate.opsForList().leftPop(QUEUE_NAME, 5, TimeUnit.SECONDS);

                if (carePlanIdStr == null) {
                    continue;
                }

                Long carePlanId = Long.parseLong(carePlanIdStr);
                System.out.println("\n[Worker] ========== 收到任务: carePlanId = " + carePlanId + " ==========");

                processWithRetry(carePlanId);

            } catch (Exception e) {
                System.err.println("[Worker] 循环异常: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ============================================================
    // 带重试的处理逻辑
    // ============================================================

    private void processWithRetry(Long carePlanId) {
        // 1. 从数据库读 CarePlan
        CarePlan carePlan = carePlanRepo.findById(carePlanId).orElse(null);
        if (carePlan == null) {
            System.err.println("[Worker] CarePlan 不存在: " + carePlanId);
            return;
        }

        // 2. 改状态为 processing
        carePlan.setStatus("processing");
        carePlanRepo.save(carePlan);

        // 3. 从数据库直接查 Order（避免 lazy loading 问题）
        CareOrder order = orderRepo.findById(carePlan.getOrder().getId()).orElse(null);
        if (order == null) {
            System.err.println("[Worker] Order 不存在: " + carePlan.getOrder().getId());
            carePlan.setStatus("failed");
            carePlanRepo.save(carePlan);
            return;
        }

        String prompt = buildPromptFromOrder(order);

        // 4. 重试循环
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("[Worker] 第 " + attempt + "/" + MAX_RETRIES + " 次尝试，正在调用 LLM...");

                String carePlanText = callLLM(prompt);

                // 成功！写回数据库
                carePlan.setContent(carePlanText);
                carePlan.setStatus("completed");
                carePlanRepo.save(carePlan);
                System.out.println("[Worker] ✅ 完成! carePlanId = " + carePlanId + "（第 " + attempt + " 次尝试成功）");
                return;  // 成功了，直接返回

            } catch (Exception e) {
                System.err.println("[Worker] ❌ 第 " + attempt + "/" + MAX_RETRIES + " 次失败: " + e.getMessage());

                if (attempt < MAX_RETRIES) {
                    // 指数退避：2秒 → 4秒 → 8秒
                    long delay = BASE_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    System.out.println("[Worker] ⏳ 等待 " + (delay / 1000) + " 秒后重试...");
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                } else {
                    // 全部重试都失败了
                    carePlan.setStatus("failed");
                    carePlanRepo.save(carePlan);
                    System.err.println("[Worker] 💀 全部 " + MAX_RETRIES + " 次重试均失败! carePlanId = " + carePlanId);
                }
            }
        }
    }

    // ============================================================
    // Prompt 组装
    // ============================================================

    private String buildPromptFromOrder(CareOrder order) {
        StringBuilder sb = new StringBuilder();
        sb.append("Please generate a care plan for the following patient:\n\n");
        sb.append("Patient: ").append(order.getPatient().getFirstName())
          .append(" ").append(order.getPatient().getLastName()).append("\n");
        sb.append("MRN: ").append(order.getPatient().getMrn()).append("\n");
        sb.append("Referring Provider: ").append(order.getProvider().getName()).append("\n");
        sb.append("Provider NPI: ").append(order.getProvider().getNpi()).append("\n");
        sb.append("Primary Diagnosis (ICD-10): ").append(order.getPrimaryDiagnosis()).append("\n");
        sb.append("Medication: ").append(order.getMedicationName()).append("\n");

        if (order.getAdditionalDiagnoses() != null && !order.getAdditionalDiagnoses().isBlank()) {
            sb.append("Additional Diagnoses: ").append(order.getAdditionalDiagnoses()).append("\n");
        }
        if (order.getMedicationHistory() != null && !order.getMedicationHistory().isBlank()) {
            sb.append("Medication History: ").append(order.getMedicationHistory()).append("\n");
        }
        if (order.getPatientRecords() != null && !order.getPatientRecords().isBlank()) {
            sb.append("Patient Records:\n").append(order.getPatientRecords()).append("\n");
        }

        return sb.toString();
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
}
