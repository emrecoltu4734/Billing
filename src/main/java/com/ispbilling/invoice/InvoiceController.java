package com.ispbilling.invoice;

import com.ispbilling.common.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@Transactional(readOnly = true)
public class InvoiceController {

    private final InvoiceRepository invoices;
    private final Clock clock;

    public InvoiceController(InvoiceRepository invoices, Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    public record LineResponse(Long subscriptionId, String description, int billedDays, int periodDays,
                               BigDecimal unitPrice, BigDecimal netAmount, BigDecimal oivRate,
                               BigDecimal oivAmount, BigDecimal vatRate, BigDecimal vatAmount,
                               BigDecimal totalAmount) {
        static LineResponse of(InvoiceLine l) {
            return new LineResponse(l.getSubscriptionId(), l.getDescription(), l.getBilledDays(),
                    l.getPeriodDays(), l.getUnitPrice(), l.getNetAmount(), l.getOivRate(), l.getOivAmount(),
                    l.getVatRate(), l.getVatAmount(), l.getTotalAmount());
        }
    }

    public record InvoiceResponse(Long id, String invoiceNo, Long customerId, String customerName,
                                  LocalDate periodStart, LocalDate periodEnd, LocalDate issueDate,
                                  LocalDate dueDate, BigDecimal netAmount, BigDecimal oivAmount,
                                  BigDecimal vatAmount, BigDecimal totalAmount, BigDecimal paidAmount,
                                  BigDecimal openAmount, InvoiceStatus status, List<LineResponse> lines) {
        static InvoiceResponse of(Invoice i, boolean withLines) {
            return new InvoiceResponse(i.getId(), i.getInvoiceNo(), i.getCustomer().getId(),
                    i.getCustomer().getName(), i.getPeriodStart(), i.getPeriodEnd(), i.getIssueDate(),
                    i.getDueDate(), i.getNetAmount(), i.getOivAmount(), i.getVatAmount(),
                    i.getTotalAmount(), i.getPaidAmount(), i.getOpenAmount(), i.getStatus(),
                    withLines ? i.getLines().stream().map(LineResponse::of).toList() : null);
        }
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return InvoiceResponse.of(invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Fatura", id)), true);
    }

    @GetMapping
    public List<InvoiceResponse> listByCustomer(@RequestParam Long customerId) {
        return invoices.findByCustomerId(customerId).stream().map(i -> InvoiceResponse.of(i, false)).toList();
    }

    /** Borçlu listesi: vadesi geçmiş, ödenmemiş faturalar. Hatırlatma ve kısıtlama buradan beslenecek. */
    @GetMapping("/overdue")
    public List<InvoiceResponse> overdue() {
        return invoices.findOverdue(LocalDate.now(clock),
                        EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID))
                .stream().map(i -> InvoiceResponse.of(i, false)).toList();
    }
}
