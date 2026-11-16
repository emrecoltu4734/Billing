package com.ispbilling.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionView(Long id, Long customerId, Long planId, String planCode, String planName,
                               BigDecimal monthlyFee, SubscriptionStatus status, LocalDate startDate,
                               LocalDate endDate) {
    static SubscriptionView of(Subscription s) {
        return new SubscriptionView(s.getId(), s.getCustomer().getId(), s.getPlan().getId(),
                s.getPlan().getCode(), s.getPlan().getName(), s.getPlan().getMonthlyFee(), s.getStatus(),
                s.getStartDate(), s.getEndDate());
    }
}
