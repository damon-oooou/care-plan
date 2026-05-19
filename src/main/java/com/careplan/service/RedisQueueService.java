package com.careplan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Simple wrapper around Redis queue operations.
 * Easy to mock in unit tests (it's a simple class, not a Spring template).
 */
@Component
public class RedisQueueService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String QUEUE_NAME = "careplan:queue";

    public void pushToQueue(String carePlanId) {
        redisTemplate.opsForList().rightPush(QUEUE_NAME, carePlanId);
    }
}
