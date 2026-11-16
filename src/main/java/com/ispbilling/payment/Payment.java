package com.ispbilling.payment;

import com.ispbilling.invoice.Invoice;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Invoice invoice;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private String reference;
    private OffsetDateTime paidAt;
    /** Ödeme kuruluşu / müşteri sistemindeki işlem kimliği. Aynı işlem iki kez kaydedilemez. */
    private String externalRef;

    protected Payment() {
    }

    public Payment(Invoice invoice, BigDecimal amount, PaymentMethod method, String reference,
                   OffsetDateTime paidAt) {
        this.invoice = invoice;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.paidAt = paidAt;
    }

    void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public String getReference() { return reference; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public String getExternalRef() { return externalRef; }
}
