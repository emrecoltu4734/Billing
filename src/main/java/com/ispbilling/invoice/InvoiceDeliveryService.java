package com.ispbilling.invoice;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import com.ispbilling.notification.NotificationService;
import com.ispbilling.notification.NotificationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Kesinleşmiş faturanın aboneye yeniden gönderilmesi. */
@Service
public class InvoiceDeliveryService {

    private final InvoiceRepository invoices;
    private final NotificationService notifications;

    public InvoiceDeliveryService(InvoiceRepository invoices, NotificationService notifications) {
        this.invoices = invoices;
        this.notifications = notifications;
    }

    /** @return kuyruğa alınan gönderim sayısı */
    @Transactional
    public int resend(Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NotFoundException("Fatura", invoiceId));
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BusinessException("Taslak fatura gönderilemez");
        }
        return notifications.resend(invoice);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(Long invoiceId) {
        invoices.findById(invoiceId).orElseThrow(() -> new NotFoundException("Fatura", invoiceId));
        return notifications.listForInvoice(invoiceId);
    }
}
