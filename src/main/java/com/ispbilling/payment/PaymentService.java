package com.ispbilling.payment;

import com.ispbilling.common.NotFoundException;
import com.ispbilling.invoice.Invoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final EntityManager em;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, EntityManager em, Clock clock) {
        this.payments = payments;
        this.em = em;
        this.clock = clock;
    }

    /** Faturayı kilitleyerek tahsilat uygular; aynı anda gelen iki ödeme bakiyeyi aşamaz. */
    @Transactional
    public PaymentView pay(Long invoiceId, BigDecimal amount, PaymentMethod method, String reference) {
        Invoice invoice = em.find(Invoice.class, invoiceId, LockModeType.PESSIMISTIC_WRITE);
        if (invoice == null) {
            throw new NotFoundException("Fatura", invoiceId);
        }
        invoice.applyPayment(amount);
        return PaymentView.of(payments.save(new Payment(invoice, amount, method, reference, OffsetDateTime.now(clock))));
    }

    @Transactional(readOnly = true)
    public List<PaymentView> listByInvoice(Long invoiceId) {
        return payments.findByInvoiceId(invoiceId).stream().map(PaymentView::of).toList();
    }
}
