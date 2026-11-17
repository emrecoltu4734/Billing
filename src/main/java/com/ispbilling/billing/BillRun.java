package com.ispbilling.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Bir bill cycle'ın bir dönemi için toplu faturalandırma çalıştırması. */
@Entity
@Table(name = "bill_run")
public class BillRun {

    public enum Status {
        RUNNING,
        COMPLETED,
        /** Bazı aboneler faturalanamadı; aynı dönem tekrar çalıştırılınca kalanlar işlenir. */
        COMPLETED_WITH_ERRORS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private BillCycle billCycle;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RUNNING;

    private int invoiceCount;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private int errorCount;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    protected BillRun() {
    }

    public BillRun(BillCycle billCycle, LocalDate periodStart, LocalDate periodEnd, OffsetDateTime startedAt) {
        this.billCycle = billCycle;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startedAt = startedAt;
    }

    void restart(OffsetDateTime now) {
        this.status = Status.RUNNING;
        this.startedAt = now;
        this.finishedAt = null;
    }

    void finish(int invoiceCount, BigDecimal totalAmount, int errorCount, OffsetDateTime finishedAt) {
        this.invoiceCount = invoiceCount;
        this.totalAmount = totalAmount;
        this.errorCount = errorCount;
        this.finishedAt = finishedAt;
        this.status = errorCount == 0 ? Status.COMPLETED : Status.COMPLETED_WITH_ERRORS;
    }

    public Long getId() { return id; }
    public BillCycle getBillCycle() { return billCycle; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Status getStatus() { return status; }
    public int getInvoiceCount() { return invoiceCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getErrorCount() { return errorCount; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
}
