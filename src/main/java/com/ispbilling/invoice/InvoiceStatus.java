package com.ispbilling.invoice;

public enum InvoiceStatus {
    /** Faturalandırma sürerken oluşan, numarasız fatura. Kesinleşmeden aboneye gösterilmez, ödeme alınmaz. */
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    CANCELLED
}
