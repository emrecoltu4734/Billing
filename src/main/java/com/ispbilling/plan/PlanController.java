package com.ispbilling.plan;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Plan create(@Valid @RequestBody CreatePlanRequest r) {
        return service.create(r);
    }

    @GetMapping
    public List<Plan> list() {
        return service.all();
    }

    @GetMapping("/{id}")
    public Plan get(@PathVariable Long id) {
        return service.get(id);
    }
}
