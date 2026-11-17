package com.ispbilling.notification;

import com.ispbilling.common.BillingProperties;
import com.ispbilling.customer.Customer;
import com.ispbilling.invoice.Invoice;
import com.ispbilling.invoice.InvoiceDocument;
import com.ispbilling.invoice.InvoiceDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fatura gönderim kuyruğu (outbox).
 *
 * <ul>
 *   <li>{@link #enqueue}: fatura kesinleşirken <b>aynı transaction'da</b> çağrılır; fatura kesinleşip
 *       gönderim kaydının kaybolması mümkün değildir.</li>
 *   <li>{@link #sendDue}: arka plan işi; her kaydı ayrı transaction'da gönderir, hatada artan aralıklarla
 *       yeniden dener, {@value #MAX_ATTEMPTS} denemeden sonra FAILED yapar.</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    static final int MAX_ATTEMPTS = 5;
    private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2));
    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JdbcClient jdbc;
    private final NotificationProperties props;
    private final BillingProperties billing;
    private final EmailGateway email;
    private final SmsGateway sms;
    private final InvoiceDocumentService documents;
    private final SpringTemplateEngine templates;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final org.springframework.context.ApplicationContext applicationContext;

    public NotificationService(JdbcClient jdbc, NotificationProperties props, BillingProperties billing,
                               EmailGateway email, SmsGateway sms, InvoiceDocumentService documents,
                               SpringTemplateEngine templates, Clock clock, PlatformTransactionManager txManager,
                               org.springframework.context.ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.jdbc = jdbc;
        this.props = props;
        this.billing = billing;
        this.email = email;
        this.sms = sms;
        this.documents = documents;
        this.templates = templates;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Kesinleşen fatura için açık kanallara gönderim kaydı açar. Çağıranın transaction'ında çalışır. */
    public void enqueue(Invoice invoice) {
        Customer c = invoice.getCustomer();
        if (props.emailEnabled() && StringUtils.hasText(c.getEmail())) {
            insert(invoice.getId(), "EMAIL", c.getEmail().trim());
        }
        String phone = PhoneNumbers.normalizeTrMobile(c.getPhone());
        if (props.smsEnabled() && phone != null) {
            insert(invoice.getId(), "SMS", phone);
        }
    }

    /**
     * Faturayı yeniden gönderir: mevcut kayıtlar baştan kuyruğa alınır, abonenin sonradan eklenen
     * iletişim bilgileri için yeni kayıt açılır.
     *
     * @return kuyruğa alınan kayıt sayısı
     */
    public int resend(Invoice invoice) {
        return tx.execute(s -> {
            enqueue(invoice);
            return jdbc.sql("""
                            update notification set status = 'PENDING', attempts = 0, last_error = null,
                                   next_attempt_at = :now, sent_at = null
                             where invoice_id = :id
                            """)
                    .param("now", OffsetDateTime.now(clock)).param("id", invoice.getId()).update();
        });
    }

    public List<NotificationView> listForInvoice(Long invoiceId) {
        return jdbc.sql("select * from notification where invoice_id = :id order by channel")
                .param("id", invoiceId)
                .query((rs, n) -> new NotificationView(rs.getLong("id"), rs.getString("channel"), rs.getString("recipient"),
                        rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                        rs.getObject("next_attempt_at", OffsetDateTime.class), rs.getObject("sent_at", OffsetDateTime.class)))
                .list();
    }

    /** Bir fatura çalıştırmasının gönderim durumu: SENT / PENDING / FAILED sayıları. */
    public Map<String, Long> summaryForRun(Long billRunId) {
        var counts = new java.util.LinkedHashMap<String, Long>(Map.of("SENT", 0L, "PENDING", 0L, "FAILED", 0L));
        jdbc.sql("""
                        select n.status, count(*) from notification n join invoice i on i.id = n.invoice_id
                         where i.bill_run_id = :id group by n.status
                        """)
                .param("id", billRunId)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getLong(2))).list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    /** Zamanı gelen kayıtları gönderir. @return gönderilmeye çalışılan kayıt sayısı */
    public int sendDue(OffsetDateTime now) {
        List<Long> ids = jdbc.sql("""
                        select id from notification
                         where status = 'PENDING' and next_attempt_at <= :now
                         order by id limit 200
                        """)
                .param("now", now).query(Long.class).list();
        for (Long id : ids) {
            tx.executeWithoutResult(s -> sendOne(id, now));
        }
        return ids.size();
    }

    private void sendOne(Long id, OffsetDateTime now) {
        var rows = jdbc.sql("select * from notification where id = :id and status = 'PENDING' for update skip locked")
                .param("id", id).query().listOfRows();
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        String channel = (String) row.get("channel");
        String recipient = (String) row.get("recipient");
        int attempts = ((Number) row.get("attempts")).intValue() + 1;
        try {
            InvoiceDocument doc = documents.byId(((Number) row.get("invoice_id")).longValue());
            if ("EMAIL".equals(channel)) {
                email.send(recipient, emailSubject(doc), emailBody(doc));
            } else {
                sms.send(recipient, smsText(doc));
            }
            jdbc.sql("update notification set status = 'SENT', attempts = :a, sent_at = :now, last_error = null where id = :id")
                    .param("a", attempts).param("now", now).param("id", id).update();
        } catch (RuntimeException e) {
            String error = e instanceof DeliveryException ? e.getMessage() : "Beklenmeyen hata: " + e.getClass().getSimpleName();
            if (!(e instanceof DeliveryException)) {
                log.error("Gönderim hatası: notification={}", id, e);
            }
            boolean giveUp = attempts >= MAX_ATTEMPTS;
            jdbc.sql("""
                            update notification set status = :status, attempts = :a, last_error = :err,
                                   next_attempt_at = :next where id = :id
                            """)
                    .param("status", giveUp ? "FAILED" : "PENDING").param("a", attempts)
                    .param("err", error.length() > 1000 ? error.substring(0, 1000) : error)
                    .param("next", now.plus(BACKOFF.get(Math.min(attempts - 1, BACKOFF.size() - 1))))
                    .param("id", id).update();
        }
    }

    // ---------- içerik ----------

    String emailSubject(InvoiceDocument doc) {
        return "%s - %s faturanız: %s".formatted(billing.company().name(), periodName(doc), money(doc.invoice().totalAmount()));
    }

    String emailBody(InvoiceDocument doc) {
        Context ctx = new Context(TR);
        ctx.setVariable("doc", doc);
        ctx.setVariable("periodName", periodName(doc));
        // Web isteği dışında @fmt gibi bean referanslarının çözülmesi için
        ctx.setVariable(org.thymeleaf.spring6.expression.ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new org.thymeleaf.spring6.expression.ThymeleafEvaluationContext(applicationContext, null));
        return templates.process("mail/invoice", ctx);
    }

    /** Tek SMS'e sığacak kadar kısa tutulur; bağlantı ayrıntıyı taşır. */
    String smsText(InvoiceDocument doc) {
        var inv = doc.invoice();
        String text = "%s: %s faturaniz %s TL, son odeme %s.".formatted(billing.company().name(), periodName(doc),
                plain(inv.totalAmount()), inv.dueDate().format(DATE));
        if (doc.totalDue().compareTo(inv.openAmount()) > 0) {
            text += " Toplam borc %s TL.".formatted(plain(doc.totalDue()));
        }
        return text + " Fatura: " + inv.viewUrl();
    }

    private static String periodName(InvoiceDocument doc) {
        var start = doc.invoice().periodStart();
        return start.getMonth().getDisplayName(TextStyle.FULL, TR) + " " + start.getYear();
    }

    private static String money(BigDecimal v) {
        return plain(v) + " ₺";
    }

    private static String plain(BigDecimal v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(TR)).format(v);
    }

    private void insert(Long invoiceId, String channel, String recipient) {
        jdbc.sql("""
                        insert into notification (invoice_id, channel, recipient, next_attempt_at)
                        values (:i, :c, :r, :now) on conflict (invoice_id, channel) do nothing
                        """)
                .param("i", invoiceId).param("c", channel).param("r", recipient)
                .param("now", OffsetDateTime.now(clock)).update();
    }
}
