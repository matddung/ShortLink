package com.studyjun.backend.infrastructure.optional.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class KafkaAvailability {

    private final String bootstrapServers;
    private final long retryCooldownMs;
    private final int checkTimeoutMs;
    private final AtomicLong unavailableUntilMs = new AtomicLong(0);
    private final AtomicBoolean unavailableLogged = new AtomicBoolean(false);

    public KafkaAvailability(@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
                             @Value("${app.kafka.unavailable-retry-cooldown-ms:30000}") long retryCooldownMs,
                             @Value("${app.kafka.availability-check-timeout-ms:300}") int checkTimeoutMs) {
        this.bootstrapServers = bootstrapServers;
        this.retryCooldownMs = retryCooldownMs;
        this.checkTimeoutMs = checkTimeoutMs;
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now < unavailableUntilMs.get()) {
            return false;
        }

        if (!unavailableLogged.get()) {
            return true;
        }

        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(checkTimeoutMs),
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(checkTimeoutMs)
        ))) {
            adminClient.describeCluster().nodes().get(checkTimeoutMs, TimeUnit.MILLISECONDS);
            unavailableLogged.set(false);
            log.info("Kafka is available again; Kafka click-event publishing is enabled.");
            return true;
        } catch (Exception ex) {
            markUnavailable(ex);
            return false;
        }
    }

    public void markUnavailable(Throwable ex) {
        unavailableUntilMs.set(System.currentTimeMillis() + retryCooldownMs);
        if (unavailableLogged.compareAndSet(false, true)) {
            log.warn("Kafka is unavailable; click events will use direct DB fallback temporarily. cause={}", ex.getMessage());
        }
    }
}
