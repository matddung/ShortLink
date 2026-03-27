package com.studyjun.backend.link.clickevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyjun.backend.link.ShortLinkMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.analytics.kafka.consumer-enabled", havingValue = "true")
public class KafkaClickEventConsumer {

    private final ObjectMapper objectMapper;
    private final ClickEventAnalyticsService clickEventAnalyticsService;
    private final ShortLinkMetrics shortLinkMetrics;

    public KafkaClickEventConsumer(ObjectMapper objectMapper,
                                   ClickEventAnalyticsService clickEventAnalyticsService,
                                   ShortLinkMetrics shortLinkMetrics) {
        this.objectMapper = objectMapper;
        this.clickEventAnalyticsService = clickEventAnalyticsService;
        this.shortLinkMetrics = shortLinkMetrics;
    }

    @KafkaListener(
            topics = "${app.analytics.kafka.topic:shortlink.redirect.click.v1}",
            groupId = "${app.analytics.kafka.consumer.group-id:shortlink-click-analytics}"
    )
    public void consume(String payload,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        Consumer<?, ?> consumer) {
        RedirectClickEventMessage message = deserialize(payload);

        log.info("Consumed click event for buffered analytics processing. topic={}, partition={}, offset={}, key={}, eventId={}, shortCode={}, requestId={}",
                topic, partition, offset, key, message.eventId(), message.shortCode(), message.requestId());

        clickEventAnalyticsService.process(message);
        updateConsumerLagMetric(topic, partition, offset, consumer);
    }

    private void updateConsumerLagMetric(String topic, int partition, long currentOffset, Consumer<?, ?> consumer) {
        try {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(java.util.List.of(topicPartition));
            Long endOffset = endOffsets.get(topicPartition);
            if (endOffset == null) {
                return;
            }
            long lag = Math.max(endOffset - (currentOffset + 1), 0L);
            shortLinkMetrics.setKafkaConsumerLag(lag);
        } catch (Exception ex) {
            log.debug("Failed to update kafka consumer lag metric. topic={}, partition={}", topic, partition, ex);
        }
    }

    private RedirectClickEventMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, RedirectClickEventMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize click event payload", ex);
        }
    }
}