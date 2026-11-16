package com.ispbilling.inbound;

import com.ispbilling.common.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Giriş tablolarını belirli aralıklarla işler. {@code billing.import.enabled=false} ile kapatılır. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "billing.import.enabled", havingValue = "true", matchIfMissing = true)
class ImportScheduler {

    private final ImportService importService;

    ImportScheduler(ImportService importService) {
        this.importService = importService;
    }

    @Scheduled(initialDelayString = "${billing.import.initial-delay:PT30S}",
               fixedDelayString = "${billing.import.interval:PT1M}")
    void run() {
        try {
            importService.runAll();
        } catch (BusinessException alreadyRunning) {
            // Elle başlatılmış bir aktarım sürüyor; bir sonraki turda tekrar denenir.
        }
    }
}
