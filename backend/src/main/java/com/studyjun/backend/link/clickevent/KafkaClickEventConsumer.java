package com.studyjun.backend.link.clickevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.analytics.kafka.enabled", havingValue = "true")
public class KafkaClickEventConsumer {

    private final ObjectMapper objectMapper;
    private final ClickEventAnalyticsService clickEventAnalyticsService;

    public KafkaClickEventConsumer(ObjectMapper objectMapper,
                                   ClickEventAnalyticsService clickEventAnalyticsService) {
        this.objectMapper = objectMapper;
        this.clickEventAnalyticsService = clickEventAnalyticsService;
    }

    @KafkaListener(
            topics = "${app.analytics.kafka.topic:shortlink.redirect.click.v1}",
            groupId = "${app.analytics.kafka.consumer.group-id:shortlink-click-analytics}"
    )
    public void consume(String payload,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        RedirectClickEventMessage message = deserialize(payload);

        log.info("Consumed click event. topic={}, partition={}, offset={}, key={}, eventId={}, shortCode={}, requestId={}",
                topic, partition, offset, key, message.eventId(), message.shortCode(), message.requestId());

        clickEventAnalyticsService.process(message);
    }

    private RedirectClickEventMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, RedirectClickEventMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize click event payload", ex);
        }
    }
}