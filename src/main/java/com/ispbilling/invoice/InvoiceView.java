package com.ispbilling.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceView(Long id, String invoiceNo, Long customerId, String customerNo, String customerName,
                          LocalDate periodStart, LocalDate periodEnd, LocalDate issueDate, LocalDate dueDate,
                          BigDecimal netAmount, BigDecimal oivAmount, BigDecimal vatAmount,
                          BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal openAmount,
                          InvoiceStatus status, boolean overdue, List<Line> lines) {

    public record Line(Long subscriptionId, String description, int billedDays, int periodDays,
                       BigDecimal unitPrice, BigDecimal netAmount, BigDecimal oivRate, BigDecimal oivAmount,
                       BigDecimal vatRate, BigDecimal vatAmount, BigDecimal totalAmount) {
        static Line of(InvoiceLine l) {
            return new Line(l.getSubscriptionId(), l.getDescription(), l.getBilledDays(), l.getPeriodDays(),
                    l.getUnitPrice(), l.getNetAmount(), l.getOivRate(), l.getOivAmount(), l.getVatRate(),
                    l.getVatAmount(), l.getTotalAmount());
        }
    }

    static InvoiceView of(Invoice i, LocalDate today, boolean withLines) {
        boolean open = i.getStatus() == InvoiceStatus.ISSUED || i.getStatus() == InvoiceStatus.PARTIALLY_PAID;
        return new InvoiceView(i.getId(), i.getInvoiceNo(), i.getCustomer().getId(),
                i.getCustomer().getCustomerNo(), i.getCustomer().getName(), i.getPeriodStart(),
                i.getPeriodEnd(), i.getIssueDate(), i.getDueDate(), i.getNetAmount(), i.getOivAmount(),
                i.getVatAmount(), i.getTotalAmount(), i.getPaidAmount(), i.getOpenAmount(), i.getStatus(),
                open && i.getDueDate().isBefore(today),
                withLines ? i.getLines().stream().map(Line::of).toList() : null);
    }
}
