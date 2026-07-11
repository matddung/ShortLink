package com.studyjun.backend.link.infrastructure.event;

import com.studyjun.backend.analytics.clickevent.ClickEventPublisher;
import com.studyjun.backend.analytics.clickevent.RedirectClickEventMessage;

public class NoopClickEventPublisher implements ClickEventPublisher {

    @Override
    public void publish(RedirectClickEventMessage message) {

    }
}
