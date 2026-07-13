package com.studyjun.backend.link.application.redirect;

public interface ClickEventPublisher {

    void publish(RedirectClickEventMessage message);
}
