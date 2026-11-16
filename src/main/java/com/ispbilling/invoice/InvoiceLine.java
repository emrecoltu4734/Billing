package com.ispbilling.invoice;

import com.ispbilling.billing.ChargeCalculator.Charge;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "invoice_line")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Invoice invoice;

    private Long subscriptionId;
    private String description;
    private int billedDays;
    private int periodDays;
    private BigDecimal unitPrice;
    private BigDecimal netAmount;
    private BigDecimal oivRate;
    private BigDecimal oivAmount;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private LocalDate periodStart;

    protected InvoiceLine() {
    }

    InvoiceLine(Invoice invoice, Long subscriptionId, String description, BigDecimal unitPrice,
                BigDecimal oivRate, BigDecimal vatRate, Charge charge) {
        this.invoice = invoice;
        this.subscriptionId = subscriptionId;
        this.description = description;
        this.billedDays = charge.billedDays();
        this.periodDays = charge.periodDays();
        this.unitPrice = unitPrice;
        this.netAmount = charge.net();
        this.oivRate = oivRate;
        this.oivAmount = charge.oiv();
        this.vatRate = vatRate;
        this.vatAmount = charge.vat();
        this.totalAmount = charge.total();
        this.periodStart = invoice.getPeriodStart();
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public String getDescription() { return description; }
    public int getBilledDays() { return billedDays; }
    public int getPeriodDays() { return periodDays; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getOivRate() { return oivRate; }
    public BigDecimal getOivAmount() { return oivAmount; }
    public BigDecimal getVatRate() { return vatRate; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDate getPeriodStart() { return periodStart; }
}
