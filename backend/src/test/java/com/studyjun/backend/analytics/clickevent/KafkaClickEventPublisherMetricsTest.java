package com.studyjun.backend.analytics.clickevent;

import com.studyjun.backend.common.observability.ShortLinkMetrics;
import com.studyjun.backend.infrastructure.optional.kafka.KafkaAvailability;
import com.studyjun.backend.link.application.redirect.ClickEventPublisher;
import com.studyjun.backend.link.application.redirect.RedirectClickEventMessage;
import com.studyjun.backend.link.infrastructure.optional.kafka.KafkaClickEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
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
    void incrementsFailureMetricOnPublishFailure(CapturedOutput output) {
        CompletableFuture<SendResult<String, RedirectClickEventMessage>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(String.class), any(String.class), any(RedirectClickEventMessage.class))).thenReturn(future);

        publisher.publish(sampleMessage());
        future.completeExceptionally(new RuntimeException("boom"));

        assertThat(meterRegistry.get("shortlink.kafka.publish.success.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.kafka.publish.failure.total").counter().count()).isEqualTo(1.0);
        assertThat(output).contains("cause=RuntimeException: boom");
        assertThat(output).doesNotContain("\tat ");
    }

    @Test
    void skipsKafkaSendWhenAvailabilityIsInCooldown() {
        KafkaAvailability kafkaAvailability = mock(KafkaAvailability.class);
        ClickEventPublisher fallbackPublisher = mock(ClickEventPublisher.class);
        RedirectClickEventMessage message = sampleMessage();
        when(kafkaAvailability.isAvailable()).thenReturn(false);

        KafkaClickEventPublisher guardedPublisher = new KafkaClickEventPublisher(
                kafkaTemplate,
                "shortlink.redirect.click.v1",
                new ShortLinkMetrics(meterRegistry),
                kafkaAvailability,
                fallbackPublisher
        );

        guardedPublisher.publish(message);

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(RedirectClickEventMessage.class));
        verify(fallbackPublisher).publish(message);
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
