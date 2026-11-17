package com.ispbilling.invoice;

import com.ispbilling.common.BillingProperties;
import com.ispbilling.common.NotFoundException;
import com.ispbilling.payment.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class InvoiceDocumentService {

    private static final int HISTORY_SIZE = 12;

    private final InvoiceRepository invoices;
    private final PaymentService payments;
    private final BillingProperties props;
    private final Clock clock;

    public InvoiceDocumentService(InvoiceRepository invoices, PaymentService payments, BillingProperties props,
                                  Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.props = props;
        this.clock = clock;
    }

    /** Abone bağlantısından. Taslak ve bilinmeyen anahtar için bulunamadı. */
    public InvoiceDocument byToken(String token) {
        Invoice invoice = invoices.findByViewToken(token)
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Fatura", "bağlantı"));
        return build(invoice);
    }

    public InvoiceDocument byId(Long id) {
        return build(invoices.findById(id).orElseThrow(() -> new NotFoundException("Fatura", id)));
    }

    private InvoiceDocument build(Invoice invoice) {
        LocalDate today = LocalDate.now(clock);
        String baseUrl = props.publicBaseUrl();
        var customer = invoice.getCustomer();

        List<Invoice> all = invoices.findByCustomerExcluding(customer.getId(), InvoiceStatus.DRAFT);
        List<InvoiceDocument.Summary> open = all.stream()
                .filter(i -> InvoiceService.OPEN_STATUSES.contains(i.getStatus()))
                .sorted(java.util.Comparator.comparing(Invoice::getPeriodStart))
                .map(i -> summary(i, today, baseUrl)).toList();
        BigDecimal totalDue = open.stream().map(InvoiceDocument.Summary::openAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<InvoiceDocument.Summary> history = all.stream().limit(HISTORY_SIZE).map(i -> summary(i, today, baseUrl)).toList();
        InvoiceDocument.Summary newer = all.stream()
                .filter(i -> i.getPeriodStart().isAfter(invoice.getPeriodStart()))
                .findFirst().map(i -> summary(i, today, baseUrl)).orElse(null);
        List<InvoiceDocument.Payment> paid = payments.listByInvoice(invoice.getId()).stream()
                .map(p -> new InvoiceDocument.Payment(p.paidAt(), p.method(), p.amount())).toList();

        return new InvoiceDocument(props.company(), InvoiceView.of(invoice, today, true, baseUrl), customer.getCustomerNo(),
                InvoiceDocument.mask(customer.getTaxId()), customer.getTaxOffice(), customer.getAddress(),
                customer.getBillCycleName(), paid, open, totalDue, history, newer, today);
    }

    private static InvoiceDocument.Summary summary(Invoice i, LocalDate today, String baseUrl) {
        boolean overdue = InvoiceService.OPEN_STATUSES.contains(i.getStatus()) && i.getDueDate().isBefore(today);
        return new InvoiceDocument.Summary(i.getInvoiceNo(), i.getPeriodStart(), i.getPeriodEnd(), i.getDueDate(),
                i.getTotalAmount(), i.getOpenAmount(), i.getStatus(), overdue,
                i.getViewToken() == null ? null : baseUrl + "/f/" + i.getViewToken());
    }
}
