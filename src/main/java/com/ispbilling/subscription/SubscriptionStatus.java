package com.ispbilling.subscription;

public enum SubscriptionStatus {
    ACTIVE,
    /** Borç vb. nedeniyle hizmeti kısıtlanmış; faturalanmaya devam eder. */
    SUSPENDED,
    /** Sonlandırılmış; end_date'e kadar kıst olarak faturalanır. */
    TERMINATED
}
