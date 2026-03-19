package com.studyjun.backend.link.clickevent;

public class NoopClickEventPublisher implements ClickEventPublisher {

    @Override
    public void publish(RedirectClickEventMessage message) {

    }
}