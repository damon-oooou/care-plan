package com.careplan.service.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM Service 工厂
 *
 * 根据 application.properties 里的 llm.provider 决定注入哪个实现。
 * 切换 LLM 只需改配置，代码不动。
 *
 * 新增 LLM：
 * 1. 新建 XxxService extends BaseLLMService
 * 2. 这里加一个 @Bean + @ConditionalOnProperty
 * 3. application.properties 里改 llm.provider=xxx
 */
@Configuration
public class LLMServiceFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMServiceFactory.class);

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "claude", matchIfMissing = true)
    public BaseLLMService claudeService() {
        log.info("LLM provider: claude");
        return new ClaudeService();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
    public BaseLLMService openAIService() {
        log.info("LLM provider: openai");
        return new OpenAIService();
    }
}
