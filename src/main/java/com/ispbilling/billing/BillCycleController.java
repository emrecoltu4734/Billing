package com.ispbilling.billing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bill-cycles")
public class BillCycleController {

    private final BillCycleService service;

    public BillCycleController(BillCycleService service) {
        this.service = service;
    }

    @GetMapping
    public List<BillCycleView> list() {
        return service.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillCycleView create(@Valid @RequestBody CreateBillCycleRequest r) {
        String code = service.create(r).getCode();
        return service.all().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }
}
