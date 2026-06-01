package com.careplan.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * OpenAI 实现（占位）
 * 需要接入时在 generateCarePlan() 里填入 OpenAI API 调用逻辑
 */
public class OpenAIService extends BaseLLMService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    @Value("${llm.openai.api-key:}")
    private String apiKey;

    @Value("${llm.openai.model:gpt-4o}")
    private String model;

    @Override
    public String generateCarePlan(String prompt) {
        // TODO: 接入 OpenAI 时在这里实现
        // 参考 ClaudeService 的结构：
        // 1. 构建 HttpHeaders（Authorization: Bearer ${apiKey}）
        // 2. 构建请求体（model, messages, max_tokens）
        // 3. POST https://api.openai.com/v1/chat/completions
        // 4. 解析 choices[0].message.content
        throw new UnsupportedOperationException("OpenAI not implemented yet");
    }

    @Override
    public String getProviderName() {
        return "openai";
    }
}
