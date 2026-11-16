package com.ispbilling.subscription;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import com.ispbilling.customer.Customer;
import com.ispbilling.customer.CustomerRepository;
import com.ispbilling.plan.Plan;
import com.ispbilling.plan.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final PlanRepository plans;

    public SubscriptionService(SubscriptionRepository subscriptions, CustomerRepository customers,
                               PlanRepository plans) {
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.plans = plans;
    }

    public SubscriptionView create(Long customerId, Long planId, LocalDate startDate) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Abone", customerId));
        Plan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Tarife", planId));
        if (!plan.isActive()) {
            throw new BusinessException("Tarife satışa kapalı: " + plan.getCode());
        }
        return SubscriptionView.of(subscriptions.save(new Subscription(customer, plan, startDate)));
    }

    public SubscriptionView suspend(Long id) {
        Subscription s = find(id);
        s.suspend();
        return SubscriptionView.of(s);
    }

    public SubscriptionView resume(Long id) {
        Subscription s = find(id);
        s.resume();
        return SubscriptionView.of(s);
    }

    public SubscriptionView terminate(Long id, LocalDate endDate) {
        Subscription s = find(id);
        s.terminate(endDate);
        return SubscriptionView.of(s);
    }

    @Transactional(readOnly = true)
    public SubscriptionView get(Long id) {
        return SubscriptionView.of(find(id));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> listByCustomer(Long customerId) {
        return subscriptions.findByCustomerId(customerId).stream().map(SubscriptionView::of).toList();
    }

    private Subscription find(Long id) {
        return subscriptions.findById(id).orElseThrow(() -> new NotFoundException("Abonelik", id));
    }
}
