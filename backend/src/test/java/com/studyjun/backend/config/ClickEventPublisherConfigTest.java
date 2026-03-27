package com.studyjun.backend.config;

import com.studyjun.backend.link.ShortLinkMetrics;
import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import com.studyjun.backend.link.clickevent.KafkaClickEventPublisher;
import com.studyjun.backend.link.clickevent.NoopClickEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClickEventPublisherConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ClickEventPublisherConfig.class)
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(ShortLinkMetrics.class, () -> new ShortLinkMetrics(new SimpleMeterRegistry()));

    @Test
    void defaultsToNoopPublisher() {
        contextRunner
                .withPropertyValues("app.analytics.kafka.producer-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ClickEventPublisher.class);
                    assertThat(context.getBean(ClickEventPublisher.class)).isInstanceOf(NoopClickEventPublisher.class);
                });
    }

    @Test
    void selectsKafkaPublisherWhenConfigured() {
        contextRunner
                .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withPropertyValues(
                        "app.analytics.kafka.producer-enabled=true",
                        "app.analytics.kafka.topic=test-shortlink.redirect.click.v1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ClickEventPublisher.class);
                    assertThat(context.getBean(ClickEventPublisher.class)).isInstanceOf(KafkaClickEventPublisher.class);
                });
    }
}