package com.studyjun.backend.link.clickevent;

public interface ClickEventPublisher {

    void publish(RedirectClickEventMessage message);
}