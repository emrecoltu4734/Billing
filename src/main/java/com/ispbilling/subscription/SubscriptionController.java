package com.ispbilling.subscription;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import com.ispbilling.customer.CustomerRepository;
import com.ispbilling.plan.Plan;
import com.ispbilling.plan.PlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@Transactional
public class SubscriptionController {

    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final PlanRepository plans;

    public SubscriptionController(SubscriptionRepository subscriptions, CustomerRepository customers,
                                  PlanRepository plans) {
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.plans = plans;
    }

    public record CreateSubscriptionRequest(@NotNull Long customerId, @NotNull Long planId,
                                            @NotNull LocalDate startDate) {
    }

    public record TerminateRequest(@NotNull LocalDate endDate) {
    }

    public record SubscriptionResponse(Long id, Long customerId, Long planId, String planCode,
                                       SubscriptionStatus status, LocalDate startDate, LocalDate endDate) {
        static SubscriptionResponse of(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getCustomer().getId(), s.getPlan().getId(),
                    s.getPlan().getCode(), s.getStatus(), s.getStartDate(), s.getEndDate());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@Valid @RequestBody CreateSubscriptionRequest r) {
        var customer = customers.findById(r.customerId())
                .orElseThrow(() -> new NotFoundException("Abone", r.customerId()));
        Plan plan = plans.findById(r.planId())
                .orElseThrow(() -> new NotFoundException("Tarife", r.planId()));
        if (!plan.isActive()) {
            throw new BusinessException("Tarife satışa kapalı: " + plan.getCode());
        }
        return SubscriptionResponse.of(subscriptions.save(new Subscription(customer, plan, r.startDate())));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listByCustomer(@RequestParam Long customerId) {
        return subscriptions.findByCustomerId(customerId).stream().map(SubscriptionResponse::of).toList();
    }

    @PostMapping("/{id}/suspend")
    public SubscriptionResponse suspend(@PathVariable Long id) {
        Subscription s = find(id);
        s.suspend();
        return SubscriptionResponse.of(s);
    }

    @PostMapping("/{id}/resume")
    public SubscriptionResponse resume(@PathVariable Long id) {
        Subscription s = find(id);
        s.resume();
        return SubscriptionResponse.of(s);
    }

    @PostMapping("/{id}/terminate")
    public SubscriptionResponse terminate(@PathVariable Long id, @Valid @RequestBody TerminateRequest r) {
        Subscription s = find(id);
        s.terminate(r.endDate());
        return SubscriptionResponse.of(s);
    }

    private Subscription find(Long id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("Abonelik", id));
    }
}
