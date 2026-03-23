package com.studyjun.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisConfig.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void registersStringRedisTemplateBean() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("clickCountRedisTemplate");
            RedisTemplate<?, ?> redisTemplate = context.getBean("clickCountRedisTemplate", RedisTemplate.class);
            assertThat(redisTemplate.getKeySerializer()).isNotNull();
            assertThat(redisTemplate.getValueSerializer()).isNotNull();
        });
    }
}