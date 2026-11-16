package com.ispbilling.billing;

import com.ispbilling.billing.ChargeCalculator.Charge;
import com.ispbilling.common.BillingProperties;
import com.ispbilling.common.BusinessException;
import com.ispbilling.customer.Customer;
import com.ispbilling.invoice.Invoice;
import com.ispbilling.invoice.InvoiceNumberGenerator;
import com.ispbilling.invoice.InvoiceRepository;
import com.ispbilling.plan.Plan;
import com.ispbilling.subscription.Subscription;
import com.ispbilling.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aylık fatura üretim motoru (bill run).
 *
 * <p>Akış: dönemle çakışan abonelikleri bul → aboneye göre grupla → her abonelik için kıst ücreti
 * hesapla → abone başına tek fatura kes → çalıştırmayı tamamla.
 *
 * <p>Tüm çalıştırma tek transaction'dır: hata olursa hiçbir fatura oluşmaz, dönem tekrar
 * çalıştırılabilir. Aynı dönemin iki kez faturalanması hem burada hem de veritabanındaki
 * UNIQUE kısıtlarıyla engellenir. Binlerce abone için yeterlidir; çok büyük hacimde parça parça
 * (chunk) işlemeye geçilecek.
 */
@Service
public class BillRunService {

    private static final Logger log = LoggerFactory.getLogger(BillRunService.class);

    private final BillRunRepository billRuns;
    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final InvoiceNumberGenerator invoiceNumbers;
    private final BillingProperties props;
    private final Clock clock;

    public BillRunService(BillRunRepository billRuns, SubscriptionRepository subscriptions,
                          InvoiceRepository invoices, InvoiceNumberGenerator invoiceNumbers,
                          BillingProperties props, Clock clock) {
        this.billRuns = billRuns;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.invoiceNumbers = invoiceNumbers;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public BillRun run(YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        if (billRuns.existsByPeriodStart(periodStart)) {
            throw new BusinessException(period + " dönemi zaten faturalandı");
        }

        BillRun run = billRuns.save(new BillRun(periodStart, periodEnd, OffsetDateTime.now(clock)));
        LocalDate issueDate = LocalDate.now(clock);
        LocalDate dueDate = issueDate.plusDays(props.paymentTermDays());

        Map<Customer, List<Subscription>> byCustomer = new LinkedHashMap<>();
        for (Subscription s : subscriptions.findBillable(periodStart, periodEnd)) {
            byCustomer.computeIfAbsent(s.getCustomer(), c -> new java.util.ArrayList<>()).add(s);
        }

        int invoiceCount = 0;
        BigDecimal runTotal = BigDecimal.ZERO;
        for (var entry : byCustomer.entrySet()) {
            Invoice invoice = new Invoice(invoiceNumbers.next(issueDate), entry.getKey(), run,
                    periodStart, periodEnd, issueDate, dueDate);
            for (Subscription s : entry.getValue()) {
                Plan plan = s.getPlan();
                Charge charge = ChargeCalculator.calculate(plan.getMonthlyFee(), plan.getOivRate(),
                        plan.getVatRate(), s.getStartDate(), s.getEndDate(), periodStart, periodEnd);
                if (!charge.isEmpty()) {
                    invoice.addLine(s.getId(), describe(plan, charge), plan.getMonthlyFee(),
                            plan.getOivRate(), plan.getVatRate(), charge);
                }
            }
            if (!invoice.getLines().isEmpty()) {
                invoices.save(invoice);
                invoiceCount++;
                runTotal = runTotal.add(invoice.getTotalAmount());
            }
        }

        run.complete(invoiceCount, runTotal, OffsetDateTime.now(clock));
        log.info("Bill run {} tamamlandı: {} fatura, toplam {} TL", period, invoiceCount, runTotal);
        return run;
    }

    private static String describe(Plan plan, Charge charge) {
        String text = plan.getName();
        if (charge.billedDays() < charge.periodDays()) {
            text += " (" + charge.billedDays() + "/" + charge.periodDays() + " gün kıst)";
        }
        return text;
    }
}
