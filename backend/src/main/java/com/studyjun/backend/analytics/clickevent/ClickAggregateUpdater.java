package com.studyjun.backend.analytics.clickevent;

public interface ClickAggregateUpdater {

    int incrementTotalClicks(Long shortLinkId, long delta);

    long getTotalClicks(Long shortLinkId);

    int overwriteTotalClicks(Long shortLinkId, long totalClicks);

    int reconcileTotalClicksFromEvents();
}
