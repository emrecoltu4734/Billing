package com.ispbilling.inbound;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Giriş tablolarının API üzerinden yönetimi. */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;
    private final ImportStatusService status;

    public ImportController(ImportService importService, ImportStatusService status) {
        this.importService = importService;
        this.status = status;
    }

    /** Aktarımı beklemeden hemen çalıştırır. */
    @PostMapping("/run")
    public ImportService.RunResult run() {
        return importService.runAll();
    }

    /** Tablo bazında bekleyen / işlenen / hatalı satır sayıları. */
    @GetMapping("/status")
    public Map<InboundTable, ImportStatusService.Counts> status() {
        return status.counts();
    }

    /** Hatalı satırları yeniden işlenmek üzere kuyruğa alır. table: customers | subscription-events | payments */
    @PostMapping("/{table}/retry")
    public Map<String, Integer> retry(@PathVariable String table) {
        return Map.of("requeued", importService.retryErrors(InboundTable.fromSlug(table)));
    }
}
