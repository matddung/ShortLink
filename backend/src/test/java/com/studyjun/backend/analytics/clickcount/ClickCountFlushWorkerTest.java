package com.studyjun.backend.analytics.clickcount;

import com.studyjun.backend.analytics.clickevent.ClickAggregateUpdater;
import com.studyjun.backend.analytics.infrastructure.optional.redis.ClickCountBufferService;
import com.studyjun.backend.common.observability.ShortLinkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClickCountFlushWorkerTest {

    private final ClickCountBufferService clickCountBufferService = mock(ClickCountBufferService.class);
    private final ClickAggregateUpdater clickAggregateUpdater = mock(ClickAggregateUpdater.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ClickCountFlushWorker clickCountFlushWorker;

    @BeforeEach
    void setUp() {
        clickCountFlushWorker = new ClickCountFlushWorker(
                clickCountBufferService,
                clickAggregateUpdater,
                30_000L,
                new ShortLinkMetrics(meterRegistry)
        );
    }

    @Test
    void flushesBufferedCountsIntoDatabaseAggregate() {
        when(clickCountBufferService.tryAcquireFlushLock(anyString(), any(Duration.class))).thenReturn(true);
        when(clickCountBufferService.findBufferedKeys()).thenReturn(Set.of("analytics:click-count:7"));
        when(clickCountBufferService.extractShortLinkId("analytics:click-count:7")).thenReturn(7L);
        when(clickCountBufferService.consumeBufferedCount("analytics:click-count:7")).thenReturn(3L);
        when(clickAggregateUpdater.incrementTotalClicks(7L, 3L)).thenReturn(1);

        clickCountFlushWorker.flush();

        verify(clickAggregateUpdater).incrementTotalClicks(7L, 3L);
        verify(clickCountBufferService).releaseFlushLock(anyString());
        assertThat(meterRegistry.get("shortlink.flush.execution.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.flush.success.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.flush.failure.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.flush.delta.total").counter().count()).isEqualTo(3.0);
    }

    @Test
    void skipsEmptyBufferedCounts() {
        when(clickCountBufferService.tryAcquireFlushLock(anyString(), any(Duration.class))).thenReturn(true);
        when(clickCountBufferService.findBufferedKeys()).thenReturn(Set.of("analytics:click-count:9"));
        when(clickCountBufferService.extractShortLinkId("analytics:click-count:9")).thenReturn(9L);
        when(clickCountBufferService.consumeBufferedCount("analytics:click-count:9")).thenReturn(null);

        clickCountFlushWorker.flush();

        verify(clickAggregateUpdater, never()).incrementTotalClicks(anyLong(), anyLong());
        verify(clickCountBufferService).releaseFlushLock(anyString());
        assertThat(meterRegistry.get("shortlink.flush.execution.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.flush.success.total").counter().count()).isZero();
    }

    @Test
    void skipsFlushWhenAnotherWorkerAlreadyHoldsTheLock() {
        when(clickCountBufferService.tryAcquireFlushLock(anyString(), any(Duration.class))).thenReturn(false);

        clickCountFlushWorker.flush();

        verify(clickCountBufferService, never()).findBufferedKeys();
        verify(clickAggregateUpdater, never()).incrementTotalClicks(anyLong(), anyLong());
        verify(clickCountBufferService, never()).releaseFlushLock(anyString());
        assertThat(meterRegistry.get("shortlink.flush.execution.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void restoresConsumedCountWhenDatabaseAggregateUpdateFails() {
        when(clickCountBufferService.tryAcquireFlushLock(anyString(), any(Duration.class))).thenReturn(true);
        when(clickCountBufferService.findBufferedKeys()).thenReturn(Set.of("analytics:click-count:7"));
        when(clickCountBufferService.extractShortLinkId("analytics:click-count:7")).thenReturn(7L);
        when(clickCountBufferService.consumeBufferedCount("analytics:click-count:7")).thenReturn(3L);
        when(clickAggregateUpdater.incrementTotalClicks(7L, 3L))
                .thenThrow(new IllegalStateException("database unavailable"));

        clickCountFlushWorker.flush();

        verify(clickAggregateUpdater).incrementTotalClicks(7L, 3L);
        verify(clickCountBufferService).restoreBufferedCount("analytics:click-count:7", 3L);
        verify(clickCountBufferService).releaseFlushLock(anyString());
        assertThat(meterRegistry.get("shortlink.flush.execution.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shortlink.flush.success.total").counter().count()).isZero();
        assertThat(meterRegistry.get("shortlink.flush.failure.total").counter().count()).isEqualTo(1.0);
    }
}
