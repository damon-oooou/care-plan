package com.careplan.service.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Claude (Anthropic) 实现
 * 原 CarePlanWorker 里的 callLLM() 逻辑原样迁移到这里
 */
public class ClaudeService extends BaseLLMService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${llm.claude.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${llm.claude.max-tokens:2000}")
    private int maxTokens;

    // ============================================================
    // 对外接口
    // ============================================================

    @Override
    public String generateCarePlan(String prompt) {
        log.debug("Calling Claude API, model = {}", model);

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        ClaudeRequest request = new ClaudeRequest();
        request.model = model;
        request.maxTokens = maxTokens;
        request.system = "You are a clinical pharmacist. Generate a professional care plan based on the patient information provided. The care plan must include these 4 sections:\n1. Problem List\n2. Goals\n3. Pharmacist Interventions\n4. Monitoring Plan\n\nBe specific, clinically accurate, and concise.";
        request.messages = List.of(new ClaudeMessage("user", prompt));

        HttpEntity<ClaudeRequest> entity = new HttpEntity<>(request, headers);

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

        throw new RuntimeException("Claude returned empty response");
    }

    @Override
    public String getProviderName() {
        return "claude";
    }

    // ============================================================
    // Claude API 请求/响应结构（从 CarePlanWorker 迁移过来）
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
        public String model;
        @JsonProperty("max_tokens")
        public int maxTokens;
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
}
