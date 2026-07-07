package com.studyjun.backend.link.clickevent;

import com.studyjun.backend.config.KafkaAvailability;
import com.studyjun.backend.link.ShortLinkMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@ConditionalOnProperty(name = "app.analytics.kafka.producer-enabled", havingValue = "true")
public class KafkaClickEventPublisher implements ClickEventPublisher {

    private final KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate;
    private final String topic;
    private final ShortLinkMetrics shortLinkMetrics;
    private final KafkaAvailability kafkaAvailability;
    private final ClickEventPublisher fallbackPublisher;

    public KafkaClickEventPublisher(KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate,
                                    String topic,
                                    ShortLinkMetrics shortLinkMetrics) {
        this(kafkaTemplate, topic, shortLinkMetrics, null, null);
    }

    public KafkaClickEventPublisher(KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate,
                                    String topic,
                                    ShortLinkMetrics shortLinkMetrics,
                                    KafkaAvailability kafkaAvailability,
                                    ClickEventPublisher fallbackPublisher) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.shortLinkMetrics = shortLinkMetrics;
        this.kafkaAvailability = kafkaAvailability;
        this.fallbackPublisher = fallbackPublisher;
    }

    @Override
    public void publish(RedirectClickEventMessage message) {
        if (kafkaAvailability != null && !kafkaAvailability.isAvailable()) {
            publishFallback(message);
            return;
        }

        try {
            kafkaTemplate.send(topic, message.shortCode(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            shortLinkMetrics.incrementKafkaPublishFailure();
                            if (kafkaAvailability != null) {
                                kafkaAvailability.markUnavailable(ex);
                            }
                            log.error(
                                    "Failed to publish click event. eventId={}, shortCode={}, requestId={}",
                                    message.eventId(),
                                    message.shortCode(),
                                    message.requestId(),
                                    ex
                            );
                            publishFallback(message);
                        } else {
                            shortLinkMetrics.incrementKafkaPublishSuccess();
                            log.info(
                                    "Published click event. eventId={}, shortCode={}, requestId={}, partition={}, offset={}",
                                    message.eventId(),
                                    message.shortCode(),
                                    message.requestId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset()
                            );
                        }
                    });
        } catch (Exception e) {
            shortLinkMetrics.incrementKafkaPublishFailure();
            if (kafkaAvailability != null) {
                kafkaAvailability.markUnavailable(e);
            }
            log.error(
                    "Kafka send threw before async completion. eventId={}, shortCode={}, requestId={}",
                    message.eventId(),
                    message.shortCode(),
                    message.requestId(),
                    e
            );
            publishFallback(message);
        }
    }

    private void publishFallback(RedirectClickEventMessage message) {
        if (fallbackPublisher == null) {
            return;
        }
        fallbackPublisher.publish(message);
    }
}
