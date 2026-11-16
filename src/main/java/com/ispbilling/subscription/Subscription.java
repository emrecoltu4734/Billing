package com.ispbilling.subscription;

import com.ispbilling.common.BusinessException;
import com.ispbilling.customer.Customer;
import com.ispbilling.plan.Plan;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "subscription")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private LocalDate startDate;
    /** Dahil (inclusive): bu gün de faturalanır. */
    private LocalDate endDate;

    protected Subscription() {
    }

    public Subscription(Customer customer, Plan plan, LocalDate startDate) {
        this.customer = customer;
        this.plan = plan;
        this.startDate = startDate;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void suspend() {
        requireStatus(SubscriptionStatus.ACTIVE);
        status = SubscriptionStatus.SUSPENDED;
    }

    public void resume() {
        requireStatus(SubscriptionStatus.SUSPENDED);
        status = SubscriptionStatus.ACTIVE;
    }

    public void terminate(LocalDate endDate) {
        if (status == SubscriptionStatus.TERMINATED) {
            throw new BusinessException("Abonelik zaten sonlandırılmış: " + id);
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("Bitiş tarihi başlangıç tarihinden önce olamaz");
        }
        this.endDate = endDate;
        this.status = SubscriptionStatus.TERMINATED;
    }

    private void requireStatus(SubscriptionStatus expected) {
        if (status != expected) {
            throw new BusinessException("Abonelik durumu " + status + ", beklenen " + expected);
        }
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Plan getPlan() { return plan; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
