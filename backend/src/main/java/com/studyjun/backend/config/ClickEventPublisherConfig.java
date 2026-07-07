package com.studyjun.backend.config;

import com.studyjun.backend.link.ShortLinkMetrics;
import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import com.studyjun.backend.link.clickevent.KafkaClickEventPublisher;
import com.studyjun.backend.link.clickevent.NoopClickEventPublisher;
import com.studyjun.backend.link.clickevent.RedirectClickEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class ClickEventPublisherConfig {

    @Bean
    @ConditionalOnProperty(name = "app.analytics.kafka.producer-enabled", havingValue = "true")
    public ClickEventPublisher kafkaClickEventPublisher(
            KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate,
            @Value("${app.analytics.kafka.topic:shortlink.redirect.click.v1}") String topic,
            ShortLinkMetrics shortLinkMetrics,
            ObjectProvider<KafkaAvailability> kafkaAvailability,
            ObjectProvider<com.studyjun.backend.link.clickevent.ClickEventAnalyticsService> clickEventAnalyticsService
    ) {
        com.studyjun.backend.link.clickevent.ClickEventAnalyticsService analyticsService = clickEventAnalyticsService.getIfAvailable();
        com.studyjun.backend.link.clickevent.ClickEventPublisher fallbackPublisher = analyticsService == null
                ? null
                : new com.studyjun.backend.link.clickevent.DirectClickEventPublisher(analyticsService);
        return new KafkaClickEventPublisher(
                kafkaTemplate,
                topic,
                shortLinkMetrics,
                kafkaAvailability.getIfAvailable(),
                fallbackPublisher
        );
    }

    @Bean
    @ConditionalOnProperty(name = "app.analytics.kafka.producer-enabled", havingValue = "false", matchIfMissing = true)
    public ClickEventPublisher noopClickEventPublisher() {
        return new NoopClickEventPublisher();
    }
}
