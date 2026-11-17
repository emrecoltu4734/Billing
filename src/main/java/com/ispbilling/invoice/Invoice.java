package com.ispbilling.invoice;

import com.ispbilling.billing.BillRun;
import com.ispbilling.billing.ChargeCalculator.Charge;
import com.ispbilling.common.BusinessException;
import com.ispbilling.customer.Customer;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    private BillRun billRun;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal netAmount = BigDecimal.ZERO;
    private BigDecimal oivAmount = BigDecimal.ZERO;
    private BigDecimal vatAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    /** Kesinleşme zamanı */
    private java.time.OffsetDateTime issuedAt;

    /** Aboneye gönderilen bağlantının tahmin edilemez anahtarı (/f/{viewToken}) */
    private String viewToken;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
    }

    /** Taslak fatura; numara {@link #finalizeWith} ile kesinleşirken verilir. */
    public Invoice(Customer customer, BillRun billRun, LocalDate periodStart,
                   LocalDate periodEnd, LocalDate issueDate, LocalDate dueDate) {
        this.customer = customer;
        this.billRun = billRun;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
    }

    public void addLine(Long subscriptionId, String description, BigDecimal unitPrice,
                        BigDecimal oivRate, BigDecimal vatRate, Charge charge) {
        lines.add(new InvoiceLine(this, subscriptionId, description, unitPrice, oivRate, vatRate, charge));
        netAmount = netAmount.add(charge.net());
        oivAmount = oivAmount.add(charge.oiv());
        vatAmount = vatAmount.add(charge.vat());
        totalAmount = totalAmount.add(charge.total());
    }

    public BigDecimal getOpenAmount() {
        return totalAmount.subtract(paidAmount);
    }

    /** Tahsilatı faturaya uygular. Fazla ödeme kabul edilmez. */
    /** Taslağı kesinleştirir. Kesinleşen fatura bir daha değiştirilmez; düzeltme iade faturasıyla yapılır. */
    public void finalizeWith(String invoiceNo, String viewToken, java.time.OffsetDateTime now) {
        if (status != InvoiceStatus.DRAFT) {
            throw new BusinessException("Fatura zaten kesinleşmiş: " + this.invoiceNo);
        }
        this.invoiceNo = invoiceNo;
        this.viewToken = viewToken;
        this.issuedAt = now;
        this.status = InvoiceStatus.ISSUED;
    }

    public void applyPayment(BigDecimal amount) {
        if (status == InvoiceStatus.DRAFT) {
            throw new BusinessException("Taslak faturaya ödeme alınamaz");
        }
        if (status == InvoiceStatus.CANCELLED || status == InvoiceStatus.PAID) {
            throw new BusinessException("Fatura ödemeye kapalı: " + invoiceNo + " (" + status + ")");
        }
        if (amount.compareTo(getOpenAmount()) > 0) {
            throw new BusinessException("Ödeme tutarı açık bakiyeden fazla. Açık bakiye: " + getOpenAmount());
        }
        paidAmount = paidAmount.add(amount);
        status = getOpenAmount().signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }

    public Long getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public Customer getCustomer() { return customer; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getOivAmount() { return oivAmount; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public InvoiceStatus getStatus() { return status; }
    public java.time.OffsetDateTime getIssuedAt() { return issuedAt; }
    public String getViewToken() { return viewToken; }
    public List<InvoiceLine> getLines() { return lines; }
}
