package com.studyjun.backend.link.clickevent;

import com.studyjun.backend.link.ShortLinkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaClickEventPublisherMetricsTest {

    private KafkaTemplate<String, RedirectClickEventMessage> kafkaTemplate;
    private SimpleMeterRegistry meterRegistry;
    private KafkaClickEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new KafkaClickEventPublisher(kafkaTemplate, "shortlink.redirect.click.v1", new ShortLinkMetrics(meterRegistry));
    }

    @Test
    void incrementsSuccessMetricOnPublishSuccess() {
        CompletableFuture<SendResult<String, RedirectClickEventMessage>> future = new CompletableFuture<>();
        RedirectClickEventMessage message = sampleMessage();
        RecordMetadata recordMetadata = new RecordMetadata(new TopicPartition("shortlink.redirect.click.v1", 0), 10L, 0, System.currentTimeMillis(), 0L, 1, 1);
        SendResult<String, RedirectClickEventMessage> sendResult = new SendResult<>(new ProducerRecord<>("shortlink.redirect.click.v1", message.shortCode(), message), recordMetadata);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(RedirectClickEventMessage.class))).thenReturn(future);

        publisher.publish(message);
        future.complete(sendResult);

        assertThat(meterRegistry.get("shortlink.kafka.publish.success.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.kafka.publish.failure.total").counter().count()).isZero();
    }

    @Test
    void incrementsFailureMetricOnPublishFailure() {
        CompletableFuture<SendResult<String, RedirectClickEventMessage>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(String.class), any(String.class), any(RedirectClickEventMessage.class))).thenReturn(future);

        publisher.publish(sampleMessage());
        future.completeExceptionally(new RuntimeException("boom"));

        assertThat(meterRegistry.get("shortlink.kafka.publish.success.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.kafka.publish.failure.total").counter().count()).isEqualTo(1.0);
    }

    private RedirectClickEventMessage sampleMessage() {
        return new RedirectClickEventMessage(
                UUID.randomUUID(),
                Instant.now().toString(),
                "req-1",
                "test",
                1L,
                "abc123",
                "https://example.com",
                "KR",
                "",
                "visitor"
        );
    }
}