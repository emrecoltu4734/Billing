package com.ispbilling.subscription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    public record CreateSubscriptionRequest(@NotNull Long customerId, @NotNull Long planId,
                                            @NotNull LocalDate startDate) {
    }

    public record TerminateRequest(@NotNull LocalDate endDate) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView create(@Valid @RequestBody CreateSubscriptionRequest r) {
        return service.create(r.customerId(), r.planId(), r.startDate());
    }

    @GetMapping
    public List<SubscriptionView> listByCustomer(@RequestParam Long customerId) {
        return service.listByCustomer(customerId);
    }

    @PostMapping("/{id}/suspend")
    public SubscriptionView suspend(@PathVariable Long id) {
        return service.suspend(id);
    }

    @PostMapping("/{id}/resume")
    public SubscriptionView resume(@PathVariable Long id) {
        return service.resume(id);
    }

    @PostMapping("/{id}/terminate")
    public SubscriptionView terminate(@PathVariable Long id, @Valid @RequestBody TerminateRequest r) {
        return service.terminate(id, r.endDate());
    }
}
