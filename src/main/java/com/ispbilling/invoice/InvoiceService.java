package com.ispbilling.invoice;

import com.ispbilling.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    public static final Set<InvoiceStatus> OPEN_STATUSES = EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoices;
    private final Clock clock;
    private final String baseUrl;

    public InvoiceService(InvoiceRepository invoices, Clock clock, com.ispbilling.common.BillingProperties props) {
        this.invoices = invoices;
        this.clock = clock;
        this.baseUrl = props.publicBaseUrl();
    }

    public InvoiceView get(Long id) {
        Invoice i = invoices.findById(id).orElseThrow(() -> new NotFoundException("Fatura", id));
        return InvoiceView.of(i, today(), true, baseUrl);
    }

    public java.util.Optional<Long> findIdByInvoiceNo(String invoiceNo) {
        return invoices.findByInvoiceNo(invoiceNo).map(Invoice::getId);
    }

    public List<InvoiceView> listByCustomer(Long customerId) {
        LocalDate today = today();
        return invoices.findByCustomerId(customerId).stream().map(i -> InvoiceView.of(i, today, false, baseUrl)).toList();
    }

    public List<InvoiceView> overdue() {
        LocalDate today = today();
        return invoices.findOverdue(today, OPEN_STATUSES).stream().map(i -> InvoiceView.of(i, today, false, baseUrl)).toList();
    }

    /** statuses boşsa tüm faturalar. */
    public Page<InvoiceView> search(Set<InvoiceStatus> statuses, Pageable pageable) {
        LocalDate today = today();
        Page<Invoice> page = statuses == null || statuses.isEmpty()
                ? invoices.findAllWithCustomer(pageable)
                : invoices.findByStatusIn(statuses, pageable);
        return page.map(i -> InvoiceView.of(i, today, false, baseUrl));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
