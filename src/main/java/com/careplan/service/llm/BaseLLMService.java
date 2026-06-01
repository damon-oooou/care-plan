package com.careplan.service.llm;

/**
 * LLM Service 抽象基类
 * 业务代码只依赖这个类，不知道底层是 Claude 还是 OpenAI
 */
public abstract class BaseLLMService {

    /**
     * 生成 Care Plan，唯一对外接口
     *
     * @param prompt 组装好的 prompt
     * @return LLM 返回的文本
     */
    public abstract String generateCarePlan(String prompt);

    /**
     * 返回当前 provider 名字，用于日志
     */
    public abstract String getProviderName();
}
