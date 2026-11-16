package com.ispbilling.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(String invoicePrefix, int paymentTermDays, Admin admin) {

    public record Admin(String username, String password) {
    }
}
