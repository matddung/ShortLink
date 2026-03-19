package com.studyjun.backend.link.clickevent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public class KafkaClickEventPublisher implements ClickEventPublisher {

    private final KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate;
    private final String topic;

    public KafkaClickEventPublisher(KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(RedirectClickEventMessage message) {
        try {
            kafkaTemplate.send(topic, message.shortCode(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error(
                                    "Failed to publish click event. eventId={}, shortCode={}, requestId={}",
                                    message.eventId(),
                                    message.shortCode(),
                                    message.requestId(),
                                    ex
                            );
                        } else {
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
            log.error(
                    "Kafka send threw before async completion. eventId={}, shortCode={}, requestId={}",
                    message.eventId(),
                    message.shortCode(),
                    message.requestId(),
                    e
            );
        }
    }
}