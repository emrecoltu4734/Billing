package com.ispbilling.payment;

public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    CREDIT_CARD,
    /** Ödeme linki / sanal POS (iyzico, PayTR vb.) */
    ONLINE
}
