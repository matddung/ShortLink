package com.studyjun.backend.link.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.analytics.reconciliation.scheduler-enabled", havingValue = "true")
public class ClickCountReconciliationBatch {

    private final ClickCountReconciliationService clickCountReconciliationService;

    public ClickCountReconciliationBatch(ClickCountReconciliationService clickCountReconciliationService) {
        this.clickCountReconciliationService = clickCountReconciliationService;
    }

    @Scheduled(cron = "${app.analytics.reconciliation.cron:0 */30 * * * *}")
    public void run() {
        int updatedRows = clickCountReconciliationService.recalculateAllMismatches();
        log.info("Click-count reconciliation batch executed. updatedRows={}", updatedRows);
    }
}