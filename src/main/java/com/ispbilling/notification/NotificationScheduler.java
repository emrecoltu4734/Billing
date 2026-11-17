package com.ispbilling.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Bekleyen e-posta/SMS kayıtlarını belirli aralıklarla gönderir. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "billing.notification.dispatch-enabled", havingValue = "true", matchIfMissing = true)
class NotificationScheduler {

    private final NotificationService service;
    private final Clock clock;

    NotificationScheduler(NotificationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${billing.notification.interval:PT1M}")
    void run() {
        service.sendDue(OffsetDateTime.now(clock));
    }
}
