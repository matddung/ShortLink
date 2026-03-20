package com.studyjun.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyjun.backend.link.clickevent.ClickEventAnalyticsService;
import com.studyjun.backend.link.clickevent.KafkaClickEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClickEventConsumerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaClickEventConsumer.class, ClickEventConsumerConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ClickEventAnalyticsService.class, () -> mock(ClickEventAnalyticsService.class))
            .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class));

    @Test
    void doesNotRegisterConsumerBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(KafkaClickEventConsumer.class);
            assertThat(context).doesNotHaveBean(CommonErrorHandler.class);
        });
    }

    @Test
    void registersConsumerBeansWhenConsumerEnabledIsTrue() {
        contextRunner
                .withPropertyValues("app.analytics.kafka.consumer-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaClickEventConsumer.class);
                    assertThat(context).hasSingleBean(CommonErrorHandler.class);
                });
    }
}