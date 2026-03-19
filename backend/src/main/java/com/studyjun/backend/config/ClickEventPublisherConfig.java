package com.studyjun.backend.config;

import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import com.studyjun.backend.link.clickevent.KafkaClickEventPublisher;
import com.studyjun.backend.link.clickevent.NoopClickEventPublisher;
import com.studyjun.backend.link.clickevent.RedirectClickEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class ClickEventPublisherConfig {

    @Bean
    @ConditionalOnProperty(name = "app.analytics.kafka.enabled", havingValue = "true")
    public ClickEventPublisher kafkaClickEventPublisher(
            KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate,
            @Value("${app.analytics.kafka.topic:shortlink.redirect.click.v1}") String topic
    ) {
        return new KafkaClickEventPublisher(kafkaTemplate, topic);
    }

    @Bean
    @ConditionalOnMissingBean(ClickEventPublisher.class)
    public ClickEventPublisher noopClickEventPublisher() {
        return new NoopClickEventPublisher();
    }
}