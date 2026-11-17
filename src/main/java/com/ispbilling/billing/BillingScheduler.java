package com.ispbilling.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Her gece, fatura günü gelen ve otomatik çalışması açık cycle'ları faturalar.
 * {@code billing.auto-run.enabled=false} ile tüm kurulum için kapatılır.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "billing.auto-run.enabled", havingValue = "true", matchIfMissing = true)
class BillingScheduler {

    private final BillRunService service;
    private final Clock clock;

    BillingScheduler(BillRunService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${billing.auto-run.cron:0 30 2 * * *}", zone = "Europe/Istanbul")
    void run() {
        service.runDueCycles(LocalDate.now(clock));
    }
}
