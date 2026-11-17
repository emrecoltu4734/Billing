package com.ispbilling.demo;

import com.ispbilling.billing.BillCycle;
import com.ispbilling.billing.BillCycleService;
import com.ispbilling.billing.BillRunService;
import com.ispbilling.billing.BillRunView;
import com.ispbilling.billing.CreateBillCycleRequest;
import com.ispbilling.customer.CreateCustomerRequest;
import com.ispbilling.customer.Customer;
import com.ispbilling.customer.CustomerRepository;
import com.ispbilling.customer.CustomerService;
import com.ispbilling.customer.CustomerType;
import com.ispbilling.invoice.InvoiceService;
import com.ispbilling.invoice.InvoiceView;
import com.ispbilling.payment.PaymentMethod;
import com.ispbilling.payment.PaymentService;
import com.ispbilling.plan.CreatePlanRequest;
import com.ispbilling.plan.Plan;
import com.ispbilling.plan.PlanService;
import com.ispbilling.subscription.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;

/**
 * Satış demoları için örnek veri. Yalnızca "demo" profiliyle ve veritabanı boşken çalışır:
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=demo}
 *
 * <p>Son üç ayı faturalar; en eski iki dönemin faturalarını geçmiş tarihli yapar ve bir kısmını öder,
 * böylece borçlu listesi dolu olur. Son ay (geçen ay) faturalanmadan bırakılır ki demoda canlı
 * olarak "Faturaları kes" gösterilebilsin.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private static final String[] INDIVIDUALS = {
            "Ayşe Yılmaz", "Mehmet Kaya", "Fatma Demir", "Ali Şahin", "Zeynep Çelik", "Mustafa Yıldız",
            "Elif Arslan", "Hüseyin Doğan", "Emine Kılıç", "İbrahim Aslan", "Hatice Çetin", "Hasan Koç",
            "Merve Kurt", "Murat Özdemir", "Esra Aydın", "Osman Öztürk", "Selin Erdoğan", "Burak Polat",
            "Gizem Acar", "Emre Güneş", "Büşra Tekin", "Serkan Yalçın"};
    private static final String[] CORPORATES = {
            "Güneş Eczanesi", "Yıldız Kırtasiye Ltd. Şti.", "Lezzet Pide Salonu", "Mavi Apartman Yönetimi"};

    private final CustomerRepository customerRepository;
    private final CustomerService customers;
    private final BillCycleService cycles;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final BillRunService billRuns;
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final JdbcClient jdbc;
    private final Clock clock;

    public DemoDataLoader(CustomerRepository customerRepository, CustomerService customers, BillCycleService cycles,
                          PlanService plans,
                          SubscriptionService subscriptions, BillRunService billRuns, InvoiceService invoices,
                          PaymentService payments, JdbcClient jdbc, Clock clock) {
        this.customerRepository = customerRepository;
        this.customers = customers;
        this.cycles = cycles;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.billRuns = billRuns;
        this.invoices = invoices;
        this.payments = payments;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (customerRepository.count() > 0) {
            log.info("Demo verisi atlandı: veritabanı boş değil");
            return;
        }
        Random rnd = new Random(42);
        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(4);

        List<Plan> internet = List.of(
                plan("WISP10", "Kablosuz 10 Mbps", "200.00"),
                plan("FIBER25", "Fiber 25 Mbps", "250.00"),
                plan("FIBER50", "Fiber 50 Mbps", "350.00"),
                plan("FIBER100", "Fiber 100 Mbps", "450.00"));
        Plan staticIp = plan("STATICIP", "Sabit IP", "75.00");

        // İki fatura dönemi: ayın 1'i (varsayılan, migration ile gelir) ve ayın 15'i
        BillCycle c01 = cycles.getDefault();
        BillCycle c15 = cycles.create(new CreateBillCycleRequest("C15", "Ayın 15'i", 15, false));

        int taxSeed = 100000000;
        int index = 0;
        for (String name : INDIVIDUALS) {
            String cycle = index++ % 3 == 0 ? c15.getCode() : c01.getCode();
            Customer c = customers.create(new CreateCustomerRequest(CustomerType.INDIVIDUAL, name,
                    "1" + (taxSeed += 7919) + "0", null, phone(rnd), null, "Merkez Mah. No: " + (rnd.nextInt(80) + 1),
                    cycle));
            subscribe(c, internet.get(rnd.nextInt(internet.size())), first, current, rnd);
        }
        for (String name : CORPORATES) {
            Customer c = customers.create(new CreateCustomerRequest(CustomerType.CORPORATE, name,
                    String.valueOf(1000000000L + rnd.nextInt(899999999)), "Merkez", phone(rnd), null,
                    "Çarşı Cad. No: " + (rnd.nextInt(80) + 1), c15.getCode()));
            subscribe(c, internet.get(2 + rnd.nextInt(2)), first, current, rnd);
            subscriptions.create(c.getId(), staticIp.getId(), first.atDay(1));
        }

        // Her cycle'ın son iki bitmiş döneminden öncekileri faturala, geçmiş tarihli yap ve tahsilat ekle.
        // En son biten dönem demoda canlı faturalamak için bırakılır.
        LocalDate today = LocalDate.now(clock);
        for (BillCycle cycle : List.of(c01, c15)) {
            YearMonth latest = cycle.latestEndedPeriod(today);
            for (YearMonth period : List.of(latest.minusMonths(2), latest.minusMonths(1))) {
                BillRunView run = billRuns.run(cycle.getCode(), period);
                LocalDate issue = cycle.runDate(period);
                jdbc.sql("update invoice set issue_date = :issue, due_date = :due where bill_run_id = :run")
                        .param("issue", issue).param("due", issue.plusDays(15)).param("run", run.id()).update();
                boolean older = period.equals(latest.minusMonths(2));
                for (InvoiceView i : invoices.search(InvoiceService.OPEN_STATUSES,
                        org.springframework.data.domain.Pageable.unpaged()).getContent()) {
                    if (!i.periodStart().equals(cycle.periodStart(period))) continue;
                    double roll = rnd.nextDouble();
                    if (roll < (older ? 0.88 : 0.65)) {
                        payments.pay(i.id(), i.openAmount(), method(rnd), null);
                    } else if (roll < (older ? 0.94 : 0.75)) {
                        payments.pay(i.id(), i.openAmount().divide(BigDecimal.TWO, 2, RoundingMode.DOWN), PaymentMethod.CASH, null);
                    }
                }
            }
        }
        jdbc.sql("update payment p set paid_at = i.due_date - (random() * 12)::int * interval '1 day' + interval '10 hours' " +
                 "from invoice i where i.id = p.invoice_id").update();
        log.info("Demo verisi yüklendi: {} abone, 2 fatura dönemi. Son biten dönemler faturalanmayı bekliyor.",
                INDIVIDUALS.length + CORPORATES.length);
    }

    private Plan plan(String code, String name, String fee) {
        return plans.create(new CreatePlanRequest(code, name, new BigDecimal(fee), BigDecimal.TEN, BigDecimal.valueOf(20)));
    }

    private void subscribe(Customer c, Plan plan, YearMonth first, YearMonth current, Random rnd) {
        // Çoğu eski abone, bir kısmı ay ortasında katılmış (kıst hesabı görünsün diye)
        YearMonth m = first.plusMonths(rnd.nextInt(4));
        LocalDate start = rnd.nextDouble() < 0.6 ? m.atDay(1) : m.atDay(2 + rnd.nextInt(26));
        var sub = subscriptions.create(c.getId(), plan.getId(), start);
        double roll = rnd.nextDouble();
        LocalDate endDate = current.minusMonths(2).atDay(10 + rnd.nextInt(15));
        if (roll < 0.08 && start.isBefore(endDate)) {
            subscriptions.terminate(sub.id(), endDate);
        } else if (roll < 0.16) {
            subscriptions.suspend(sub.id());
        }
    }

    private static String phone(Random rnd) {
        return "05" + (30 + rnd.nextInt(30)) + String.format("%07d", rnd.nextInt(10_000_000));
    }

    private static PaymentMethod method(Random rnd) {
        PaymentMethod[] m = {PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER, PaymentMethod.CREDIT_CARD, PaymentMethod.ONLINE};
        return m[rnd.nextInt(m.length)];
    }
}
