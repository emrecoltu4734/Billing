package com.ispbilling.payment;

import com.ispbilling.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentView(Long id, Long invoiceId, BigDecimal amount, PaymentMethod method, String reference,
                          OffsetDateTime paidAt, InvoiceStatus invoiceStatus, BigDecimal invoiceOpenAmount) {
    static PaymentView of(Payment p) {
        return new PaymentView(p.getId(), p.getInvoice().getId(), p.getAmount(), p.getMethod(),
                p.getReference(), p.getPaidAt(), p.getInvoice().getStatus(), p.getInvoice().getOpenAmount());
    }
}
