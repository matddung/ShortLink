package com.studyjun.backend.analytics.clickevent;

public interface ClickEventPublisher {

    void publish(RedirectClickEventMessage message);
}