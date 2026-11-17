package com.ispbilling.billing;

import com.ispbilling.billing.ChargeCalculator.Charge;
import com.ispbilling.common.BillingProperties;
import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import com.ispbilling.customer.Customer;
import com.ispbilling.customer.CustomerRepository;
import com.ispbilling.invoice.Invoice;
import com.ispbilling.invoice.InvoiceNumberGenerator;
import com.ispbilling.invoice.InvoiceRepository;
import com.ispbilling.plan.Plan;
import com.ispbilling.subscription.Subscription;
import com.ispbilling.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Faturalandırma batch'i (bill run).
 *
 * <p>Akış: cycle ve dönem belirlenir → run kaydı açılır (veya yarım kalan run sürdürülür) → dönemde
 * faturalanacak aboneliği olan ve bu dönem için henüz faturası olmayan aboneler bulunur → her abone
 * <b>kendi transaction'ında</b> faturalanır → hatalı abone {@code bill_run_error}'a yazılır, run devam eder
 * → sayılar veritabanından hesaplanıp run kapatılır.
 *
 * <p>Mükerrer faturaya karşı üç koruma vardır: (cycle, dönem) başına tek run; (abone, dönem) başına tek
 * fatura; aboneliğin {@code billedThrough} tarihi (cycle değişse bile aynı gün iki kez faturalanmaz).
 */
@Service
public class BillRunService {

    private static final Logger log = LoggerFactory.getLogger(BillRunService.class);
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BillRunRepository billRuns;
    private final BillCycleService cycles;
    private final SubscriptionRepository subscriptions;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final InvoiceNumberGenerator invoiceNumbers;
    private final JdbcClient jdbc;
    private final BillingProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BillRunService(BillRunRepository billRuns, BillCycleService cycles, SubscriptionRepository subscriptions,
                          CustomerRepository customers, InvoiceRepository invoices,
                          InvoiceNumberGenerator invoiceNumbers, JdbcClient jdbc, BillingProperties props,
                          Clock clock, PlatformTransactionManager txManager) {
        this.billRuns = billRuns;
        this.cycles = cycles;
        this.subscriptions = subscriptions;
        this.customers = customers;
        this.invoices = invoices;
        this.invoiceNumbers = invoiceNumbers;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Varsayılan cycle için. */
    public BillRunView run(YearMonth period) {
        return run(null, period);
    }

    /**
     * Bir cycle'ın bir dönemini faturalar. Dönem daha önce hatalarla tamamlandıysa veya yarım kaldıysa
     * kaldığı yerden devam eder; tamamen faturalandıysa reddedilir.
     *
     * @param cycleCode boşsa varsayılan cycle
     */
    public BillRunView run(String cycleCode, YearMonth period) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("Şu anda başka bir faturalandırma çalışıyor, bitince tekrar deneyin");
        }
        try {
            return doRun(cycles.resolve(cycleCode), period);
        } finally {
            running.set(false);
        }
    }

    /**
     * Bugün fatura günü olan ve otomatik çalışması açık cycle'ların biten dönemini faturalar.
     * Zamanlanmış iş tarafından her gece çağrılır.
     */
    public List<BillRunView> runDueCycles(LocalDate today) {
        List<BillRunView> results = new ArrayList<>();
        for (BillCycle cycle : cycles.autoRunCycles()) {
            if (!cycle.isRunDay(today)) continue;
            YearMonth period = cycle.latestEndedPeriod(today);
            boolean done = billRuns.findByBillCycleIdAndPeriodStart(cycle.getId(), cycle.periodStart(period))
                    .map(r -> r.getStatus() == BillRun.Status.COMPLETED).orElse(false);
            if (done) continue;
            try {
                results.add(run(cycle.getCode(), period));
            } catch (BusinessException e) {
                log.warn("Otomatik faturalandırma yapılamadı: {} {}: {}", cycle.getCode(), period, e.getMessage());
            }
        }
        return results;
    }

    public BillRunView get(Long id) {
        BillRun run = billRuns.findWithCycle(id).orElseThrow(() -> new NotFoundException("Fatura çalıştırması", id));
        return BillRunView.of(run, errors(id));
    }

    public List<BillRunView> list() {
        return billRuns.findAllWithCycle().stream().map(r -> BillRunView.of(r, null)).toList();
    }

    // ------------------------------------------------------------------

    private BillRunView doRun(BillCycle cycle, YearMonth period) {
        LocalDate periodStart = cycle.periodStart(period);
        LocalDate periodEnd = cycle.periodEnd(period);
        LocalDate today = LocalDate.now(clock);
        String label = periodName(period) + " dönemi (" + cycle.getName() + ")";
        if (!periodEnd.isBefore(today)) {
            throw new BusinessException("%s henüz bitmedi: %s – %s. En erken %s tarihinde faturalanabilir."
                    .formatted(label, periodStart.format(DATE), periodEnd.format(DATE), periodEnd.plusDays(1).format(DATE)));
        }

        Long runId = tx.execute(s -> {
            var existing = billRuns.findByBillCycleIdAndPeriodStart(cycle.getId(), periodStart);
            if (existing.isPresent()) {
                BillRun run = existing.get();
                if (run.getStatus() == BillRun.Status.COMPLETED) {
                    throw new BusinessException(label + " zaten faturalandı");
                }
                log.info("Yarım kalan fatura çalıştırması sürdürülüyor: {} {}", cycle.getCode(), period);
                run.restart(OffsetDateTime.now(clock));
                jdbc.sql("delete from bill_run_error where bill_run_id = :id").param("id", run.getId()).update();
                return run.getId();
            }
            return billRuns.save(new BillRun(cycle, periodStart, periodEnd, OffsetDateTime.now(clock))).getId();
        });

        List<Long> pending = pendingCustomers(cycle.getId(), periodStart, periodEnd);
        LocalDate issueDate = today;
        LocalDate dueDate = issueDate.plusDays(props.paymentTermDays());
        for (Long customerId : pending) {
            try {
                tx.executeWithoutResult(s -> billCustomer(runId, customerId, periodStart, periodEnd, issueDate, dueDate));
            } catch (RuntimeException e) {
                String message = e instanceof BusinessException || e instanceof NotFoundException
                        ? e.getMessage()
                        : e.getClass().getSimpleName() + ": " + rootMessage(e);
                log.error("Abone faturalanamadı: run={} customer={}", runId, customerId, e);
                recordError(runId, customerId, message);
            }
        }

        BillRunView result = tx.execute(s -> {
            BillRun run = billRuns.findWithCycle(runId).orElseThrow();
            var totals = jdbc.sql("select count(*) as n, coalesce(sum(total_amount), 0) as total from invoice where bill_run_id = :id")
                    .param("id", runId)
                    .query((rs, n) -> new Object[]{rs.getInt("n"), rs.getBigDecimal("total")}).single();
            int errorCount = jdbc.sql("select count(*) from bill_run_error where bill_run_id = :id")
                    .param("id", runId).query(Integer.class).single();
            run.finish((int) totals[0], (BigDecimal) totals[1], errorCount, OffsetDateTime.now(clock));
            return BillRunView.of(run, null);
        });
        log.info("Fatura çalıştırması bitti: {} {} → {} fatura, {} TL, {} hata", cycle.getCode(), period,
                result.invoiceCount(), result.totalAmount(), result.errorCount());
        return result;
    }

    /** Cycle'daki, dönemde faturalanacak günü kalmış ve bu dönem faturası olmayan aboneler. */
    private List<Long> pendingCustomers(Long cycleId, LocalDate periodStart, LocalDate periodEnd) {
        return jdbc.sql("""
                        select distinct c.id
                          from customer c
                          join subscription s on s.customer_id = c.id
                         where c.bill_cycle_id = :cycle
                           and s.start_date <= :pe
                           and (s.end_date is null or s.end_date >= :ps)
                           and (s.billed_through is null or s.billed_through < least(:pe, coalesce(s.end_date, :pe)))
                           and not exists (select 1 from invoice i where i.customer_id = c.id and i.period_start = :ps)
                         order by c.id
                        """)
                .param("cycle", cycleId).param("ps", periodStart).param("pe", periodEnd)
                .query(Long.class).list();
    }

    /** Tek abonenin faturası. Çağıran transaction içinde çalışır. */
    private void billCustomer(Long runId, Long customerId, LocalDate periodStart, LocalDate periodEnd,
                              LocalDate issueDate, LocalDate dueDate) {
        Customer customer = customers.findById(customerId).orElseThrow(() -> new NotFoundException("Abone", customerId));
        Invoice invoice = new Invoice(invoiceNumbers.next(issueDate), customer, billRuns.getReferenceById(runId),
                periodStart, periodEnd, issueDate, dueDate);
        for (Subscription s : subscriptions.findBillableForCustomer(customerId, periodStart, periodEnd)) {
            // Daha önce faturalanan günler atlanır (ör. cycle değişikliği sonrası)
            LocalDate from = s.getStartDate();
            if (s.getBilledThrough() != null && !s.getBilledThrough().isBefore(from)) {
                from = s.getBilledThrough().plusDays(1);
            }
            Plan plan = s.getPlan();
            Charge charge = ChargeCalculator.calculate(plan.getMonthlyFee(), plan.getOivRate(), plan.getVatRate(),
                    from, s.getEndDate(), periodStart, periodEnd);
            if (charge.isEmpty()) continue;
            invoice.addLine(s.getId(), describe(plan, charge), plan.getMonthlyFee(), plan.getOivRate(),
                    plan.getVatRate(), charge);
            LocalDate billedTo = s.getEndDate() != null && s.getEndDate().isBefore(periodEnd) ? s.getEndDate() : periodEnd;
            s.markBilledThrough(billedTo);
        }
        if (!invoice.getLines().isEmpty()) {
            invoices.save(invoice);
        }
    }

    private void recordError(Long runId, Long customerId, String message) {
        String msg = message == null ? "Bilinmeyen hata" : message.length() > 1000 ? message.substring(0, 1000) : message;
        tx.executeWithoutResult(s -> jdbc.sql("insert into bill_run_error (bill_run_id, customer_id, message) values (:r, :c, :m)")
                .param("r", runId).param("c", customerId).param("m", msg).update());
    }

    private List<BillRunView.ErrorView> errors(Long runId) {
        return jdbc.sql("""
                        select c.id, c.customer_no, c.name, e.message, e.created_at
                          from bill_run_error e join customer c on c.id = e.customer_id
                         where e.bill_run_id = :id order by e.id
                        """)
                .param("id", runId)
                .query((rs, n) -> new BillRunView.ErrorView(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5, OffsetDateTime.class)))
                .list();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage();
    }

    static String periodName(YearMonth period) {
        return period.getMonth().getDisplayName(TextStyle.FULL, TR) + " " + period.getYear();
    }

    private static String describe(Plan plan, Charge charge) {
        String text = plan.getName();
        if (charge.billedDays() < charge.periodDays()) {
            text += " (" + charge.billedDays() + "/" + charge.periodDays() + " gün kıst)";
        }
        return text;
    }
}
