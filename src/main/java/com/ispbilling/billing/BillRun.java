package com.ispbilling.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Bir fatura döneminin toplu faturalama çalıştırması. */
@Entity
@Table(name = "bill_run")
public class BillRun {

    public enum Status { RUNNING, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RUNNING;

    private int invoiceCount;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    protected BillRun() {
    }

    public BillRun(LocalDate periodStart, LocalDate periodEnd, OffsetDateTime startedAt) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startedAt = startedAt;
    }

    void complete(int invoiceCount, BigDecimal totalAmount, OffsetDateTime finishedAt) {
        this.invoiceCount = invoiceCount;
        this.totalAmount = totalAmount;
        this.finishedAt = finishedAt;
        this.status = Status.COMPLETED;
    }

    public Long getId() { return id; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Status getStatus() { return status; }
    public int getInvoiceCount() { return invoiceCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
}
