package com.ispbilling.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

public record BillRunView(Long id, String cycleCode, String cycleName, YearMonth period, LocalDate periodStart,
                          LocalDate periodEnd, BillRun.Status status, int invoiceCount, BigDecimal totalAmount,
                          int errorCount, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                          List<ErrorView> errors) {

    public record ErrorView(Long customerId, String customerNo, String customerName, String message,
                            OffsetDateTime createdAt) {
    }

    static BillRunView of(BillRun r, List<ErrorView> errors) {
        return new BillRunView(r.getId(), r.getBillCycle().getCode(), r.getBillCycle().getName(),
                YearMonth.from(r.getPeriodStart()), r.getPeriodStart(), r.getPeriodEnd(), r.getStatus(),
                r.getInvoiceCount(), r.getTotalAmount(), r.getErrorCount(), r.getStartedAt(), r.getFinishedAt(),
                errors);
    }
}
