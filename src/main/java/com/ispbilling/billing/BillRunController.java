package com.ispbilling.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/bill-runs")
public class BillRunController {

    private final BillRunService service;
    private final BillRunRepository billRuns;

    public BillRunController(BillRunService service, BillRunRepository billRuns) {
        this.service = service;
        this.billRuns = billRuns;
    }

    /** Örnek gövde: {"period": "2026-11"} */
    public record RunRequest(@NotNull YearMonth period) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillRun run(@Valid @RequestBody RunRequest r) {
        return service.run(r.period());
    }

    @GetMapping
    public List<BillRun> list() {
        return billRuns.findAllByOrderByPeriodStartDesc();
    }
}
