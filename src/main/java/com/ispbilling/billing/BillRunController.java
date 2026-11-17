package com.ispbilling.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bill-runs")
public class BillRunController {

    private final BillRunService service;

    public BillRunController(BillRunService service) {
        this.service = service;
    }

    /**
     * Örnek gövde: {"period": "2026-11"} veya {"cycleCode": "C15", "period": "2026-11"}.
     * cycleCode boşsa varsayılan fatura dönemi. Yarım kalmış veya hatalarla bitmiş dönem tekrar
     * gönderilirse kaldığı yerden devam eder.
     */
    public record RunRequest(String cycleCode, @NotNull YearMonth period) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillRunView run(@Valid @RequestBody RunRequest r) {
        return service.run(r.cycleCode(), r.period());
    }

    @GetMapping
    public List<BillRunView> list() {
        return service.list();
    }

    /** Çalıştırma ayrıntısı ve faturalanamayan aboneler. */
    @GetMapping("/{id}")
    public BillRunView get(@PathVariable Long id) {
        return service.get(id);
    }
}
