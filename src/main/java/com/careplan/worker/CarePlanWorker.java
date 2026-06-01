package com.careplan.worker;

import com.careplan.entity.CarePlan;
import com.careplan.entity.CareOrder;
import com.careplan.repository.CarePlanRepository;
import com.careplan.repository.CareOrderRepository;
import com.careplan.service.llm.BaseLLMService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Worker: background thread that pulls tasks from Redis queue, calls LLM, writes back to database.
 *
 * Retry policy: max 3 attempts, exponential backoff (2s -> 4s -> 8s)
 */
@Component
public class CarePlanWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CarePlanWorker.class);

    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private CareOrderRepository orderRepo;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private BaseLLMService llmService;  // 注入抽象，不知道底层是谁

    private static final String QUEUE_NAME = "careplan:queue";
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000;

    @Override
    public void run(String... args) {
        Thread workerThread = new Thread(this::processQueue, "careplan-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("CarePlan Worker started, LLM provider: {}, waiting for queue tasks... Retry policy: max {} attempts, exponential backoff",
                llmService.getProviderName(), MAX_RETRIES);
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
                log.info("Received task: carePlanId = {}", carePlanId);

                processWithRetry(carePlanId);

            } catch (Exception e) {
                log.error("Queue loop error: {}", e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ============================================================
    // 带重试的处理逻辑
    // ============================================================

    private void processWithRetry(Long carePlanId) {
        // 1. Read CarePlan from database
        CarePlan carePlan = carePlanRepo.findById(carePlanId).orElse(null);
        if (carePlan == null) {
            log.error("CarePlan not found: {}", carePlanId);
            return;
        }

        // 2. Update status to processing
        carePlan.setStatus("processing");
        carePlanRepo.save(carePlan);
        log.info("Status updated to processing, carePlanId = {}", carePlanId);

        // 3. Load Order from database (avoid lazy loading issue)
        CareOrder order = orderRepo.findById(carePlan.getOrder().getId()).orElse(null);
        if (order == null) {
            log.error("Order not found: {}", carePlan.getOrder().getId());
            carePlan.setStatus("failed");
            carePlanRepo.save(carePlan);
            return;
        }

        String prompt = buildPromptFromOrder(order);

        // 4. Retry loop
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Attempt {}/{}, calling LLM ({})...", attempt, MAX_RETRIES, llmService.getProviderName());

                String carePlanText = llmService.generateCarePlan(prompt);

                // Success! Write back to database
                carePlan.setContent(carePlanText);
                carePlan.setStatus("completed");
                carePlanRepo.save(carePlan);
                log.info("Done! carePlanId = {} (succeeded on attempt {})", carePlanId, attempt);
                return;

            } catch (Exception e) {
                log.error("Attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (long) Math.pow(2, attempt - 1);
                    log.info("Waiting {} seconds before retry...", delay / 1000);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                } else {
                    carePlan.setStatus("failed");
                    carePlanRepo.save(carePlan);
                    log.error("All {} attempts failed! carePlanId = {}", MAX_RETRIES, carePlanId);
                }
            }
        }
    }

    // ============================================================
    // Prompt 组装（不变）
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
}
