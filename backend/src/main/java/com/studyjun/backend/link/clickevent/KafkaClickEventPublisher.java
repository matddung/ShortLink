package com.studyjun.backend.link.clickevent;

import org.springframework.kafka.core.KafkaTemplate;

public class KafkaClickEventPublisher implements ClickEventPublisher {

    private final KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate;
    private final String topic;

    public KafkaClickEventPublisher(KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(RedirectClickEventMessage message) {
        kafkaTemplate.send(topic, message.shortCode(), message);
    }
}