package com.ispbilling.invoice;

import com.ispbilling.common.BillingProperties;
import com.ispbilling.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Aboneye gösterilen fatura: kesilen fatura + bugün itibarıyla hesap durumu + geçmiş faturalar.
 * Hem abone fatura sayfası (/f/{anahtar}) hem e-posta/SMS içeriği bundan üretilir.
 *
 * @param openInvoices   bugün itibarıyla ödenmemiş tüm faturalar (bu fatura dahil)
 * @param totalDue       ödenmemiş faturaların toplamı (güncel borç)
 * @param newerInvoice   bu faturadan sonra kesilmiş fatura varsa en yenisi
 */
public record InvoiceDocument(BillingProperties.Company company, InvoiceView invoice, String customerNo,
                              String maskedTaxId, String taxOffice, String address, String billCycleName,
                              List<Payment> payments, List<Summary> openInvoices, BigDecimal totalDue,
                              List<Summary> history, Summary newerInvoice, LocalDate asOf) {

    public record Payment(OffsetDateTime paidAt, PaymentMethod method, BigDecimal amount) {
    }

    public record Summary(String invoiceNo, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
                          BigDecimal totalAmount, BigDecimal openAmount, InvoiceStatus status, boolean overdue,
                          String viewUrl) {
    }

    /** Önceki dönemlerden kalan borç: güncel borcun bu fatura dışındaki kısmı. */
    public BigDecimal previousBalance() {
        return totalDue.subtract(invoice.status() == InvoiceStatus.ISSUED || invoice.status() == InvoiceStatus.PARTIALLY_PAID
                ? invoice.openAmount() : BigDecimal.ZERO);
    }

    /** TCKN/VKN'nin yalnızca baş ve son hanelerini gösterir: 123******01 */
    static String mask(String taxId) {
        if (taxId == null || taxId.length() < 6) return "***";
        return taxId.substring(0, 3) + "*".repeat(taxId.length() - 5) + taxId.substring(taxId.length() - 2);
    }
}
