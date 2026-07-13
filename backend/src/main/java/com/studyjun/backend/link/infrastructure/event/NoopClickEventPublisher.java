package com.studyjun.backend.link.infrastructure.event;

import com.studyjun.backend.link.application.redirect.ClickEventPublisher;
import com.studyjun.backend.link.application.redirect.RedirectClickEventMessage;

public class NoopClickEventPublisher implements ClickEventPublisher {

    @Override
    public void publish(RedirectClickEventMessage message) {

    }
}
