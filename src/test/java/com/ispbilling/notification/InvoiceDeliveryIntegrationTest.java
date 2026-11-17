package com.ispbilling.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispbilling.TestClockConfig;
import com.ispbilling.TestDatabase;
import com.ispbilling.billing.BillRunService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fatura kesinleştirme → boşluksuz numara → e-posta/SMS kuyruğu → gönderim/yeniden deneme → abone fatura sayfası.
 * Bugün: 01.12.2026 (TestClockConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestClockConfig.class, RecordingGateways.class})
class InvoiceDeliveryIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-12-01T09:00:00+03:00");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    BillRunService billRuns;
    @Autowired
    NotificationService notifications;

    long ayse;   // e-posta + cep telefonu
    long mehmet; // yalnızca cep telefonu
    long ofis;   // iletişim bilgisi yok

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.reset(jdbc);
        RecordingGateways.clear();
        long plan = create("/api/v1/plans", """
                {"code":"FIBER100","name":"Fiber 100 Mbps","monthlyFee":300.00,"oivRate":10,"vatRate":20}""");
        ayse = customer("Ayşe Yılmaz", "12345678901", "0532 111 22 33", "ayse@example.com");
        mehmet = customer("Mehmet Kaya", "10987654321", "5442223344", null);
        ofis = customer("Sabit Hat Ofis", "1234567890", "0212 444 55 66", null);
        for (long c : List.of(ayse, mehmet, ofis)) {
            create("/api/v1/subscriptions", """
                    {"customerId":%d,"planId":%d,"startDate":"2026-09-01"}""".formatted(c, plan));
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("drop trigger if exists test_fail_finalize on invoice").update();
        TestDatabase.reset(jdbc);
        RecordingGateways.clear();
    }

    @Test
    @WithMockUser
    void billingFinalizesInvoicesWithGaplessNumbersAndQueuesDelivery() {
        billRuns.run(YearMonth.of(2026, 10));
        billRuns.run(YearMonth.of(2026, 11));

        assertThat(jdbc.sql("select count(*) from invoice where status = 'DRAFT'").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select invoice_no from invoice order by invoice_no").query(String.class).list())
                .containsExactly("ISP2026000000001", "ISP2026000000002", "ISP2026000000003",
                        "ISP2026000000004", "ISP2026000000005", "ISP2026000000006");
        assertThat(jdbc.sql("select view_token from invoice").query(String.class).list())
                .allMatch(t -> t.matches("[0-9a-f]{64}"));

        // Ayşe: e-posta + SMS; Mehmet: SMS (numara 905... biçiminde); sabit hatlı ofis: hiçbiri
        assertThat(channels(ayse)).containsExactlyInAnyOrder("EMAIL:ayse@example.com", "SMS:905321112233",
                "EMAIL:ayse@example.com", "SMS:905321112233");
        assertThat(channels(mehmet)).containsExactly("SMS:905442223344", "SMS:905442223344");
        assertThat(channels(ofis)).isEmpty();
    }

    @Test
    void sentMessagesContainAmountsTotalDebtAndLink() {
        billRuns.run(YearMonth.of(2026, 10));
        notifications.sendDue(NOW);
        RecordingGateways.clear();
        billRuns.run(YearMonth.of(2026, 11));   // Ekim ödenmedi: Kasım mesajında toplam borç görünür
        assertThat(notifications.sendDue(NOW)).isEqualTo(3);

        var mail = RecordingGateways.EMAILS.getFirst();
        assertThat(mail.to()).isEqualTo("ayse@example.com");
        assertThat(mail.subject()).isEqualTo("Örnek İnternet Hizmetleri - Kasım 2026 faturanız: 396,00 ₺");
        assertThat(mail.body()).contains("Sayın <strong>Ayşe Yılmaz</strong>", "Önceki dönemlerden kalan", "396,00 ₺",
                "792,00 ₺", "16.12.2026", "http://localhost:8080/f/");

        var sms = RecordingGateways.SMS.stream().filter(s -> s.to().equals("905442223344")).findFirst().orElseThrow();
        assertThat(sms.body()).startsWith("Örnek İnternet Hizmetleri: Kasım 2026 faturaniz 396,00 TL, son odeme 16.12.2026. Toplam borc 792,00 TL. Fatura: http://localhost:8080/f/");
        assertThat(jdbc.sql("select count(*) from notification where status = 'SENT'").query(Long.class).single()).isEqualTo(6);
    }

    @Test
    @WithMockUser
    void failedDeliveryIsRetriedWithBackoffThenMarkedFailedAndCanBeResent() throws Exception {
        RecordingGateways.FAILING.add("905442223344");
        billRuns.run(YearMonth.of(2026, 11));

        notifications.sendDue(NOW);
        var row = smsRow(mehmet);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1)
                .containsEntry("last_error", "SMS sağlayıcısı hata döndü: HTTP 503");
        assertThat(notifications.sendDue(NOW.plusSeconds(30))).isZero();          // 1 dakika dolmadan denenmez
        OffsetDateTime t = NOW;
        for (int i = 0; i < 4; i++) {
            t = t.plusHours(3);
            notifications.sendDue(t);
        }
        assertThat(smsRow(mehmet)).containsEntry("status", "FAILED").containsEntry("attempts", 5);

        long invoiceId = jdbc.sql("select id from invoice where customer_id = ?").param(mehmet).query(Long.class).single();
        mvc.perform(get("/invoices/" + invoiceId))
                .andExpect(content().string(containsString("Başarısız")))
                .andExpect(content().string(containsString("SMS sağlayıcısı hata döndü: HTTP 503")));

        RecordingGateways.FAILING.clear();
        mvc.perform(post("/invoices/" + invoiceId + "/notifications/resend").with(csrf()))
                .andExpect(flash().attribute("success", "1 gönderim kuyruğa alındı; birkaç dakika içinde gönderilecek"));
        notifications.sendDue(t.plusMinutes(1));
        assertThat(smsRow(mehmet)).containsEntry("status", "SENT");
        mvc.perform(get("/api/v1/invoices/" + invoiceId + "/notifications"))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    void finalizeFailureLeavesDraftsAndRetryKeepsNumbersGapless() {
        // Kesinleştirme sırasında veritabanı hatası canlandırılır
        jdbc.sql("""
                create or replace function test_fail_finalize() returns trigger as $$
                begin
                  if new.status = 'ISSUED' and old.status = 'DRAFT' then raise exception 'Test: kesinleştirme hatası'; end if;
                  return new;
                end $$ language plpgsql""").update();
        jdbc.sql("create trigger test_fail_finalize before update on invoice for each row execute function test_fail_finalize()").update();

        assertThatThrownBy(() -> billRuns.run(YearMonth.of(2026, 11))).hasMessageContaining("Test: kesinleştirme hatası");
        assertThat(jdbc.sql("select count(*) from invoice where status = 'DRAFT' and invoice_no is null").query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("select count(*) from notification").query(Long.class).single()).isZero();

        jdbc.sql("drop trigger test_fail_finalize on invoice").update();
        var run = billRuns.run(YearMonth.of(2026, 11));   // kaldığı yerden: yalnızca kesinleştirme

        assertThat(run.invoiceCount()).isEqualTo(3);
        assertThat(jdbc.sql("select invoice_no from invoice order by invoice_no").query(String.class).list())
                .containsExactly("ISP2026000000001", "ISP2026000000002", "ISP2026000000003");
    }

    @Test
    void subscriberInvoicePageShowsInvoiceAccountStatusAndHistoryWithoutLogin() throws Exception {
        billRuns.run(YearMonth.of(2026, 10));
        billRuns.run(YearMonth.of(2026, 11));
        String oct = token(ayse, "2026-10-01");
        String nov = token(ayse, "2026-11-01");

        mvc.perform(get("/f/" + nov))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Ayşe Yılmaz")))
                .andExpect(content().string(containsString("123******01")))
                .andExpect(content().string(not(containsString("12345678901"))))
                .andExpect(content().string(containsString("Önceki dönemlerden kalan")))
                .andExpect(content().string(containsString("Toplam borcunuz")))
                .andExpect(content().string(containsString("792,00 ₺")))
                .andExpect(content().string(containsString("/f/" + oct)))      // geçmiş faturaya bağlantı
                .andExpect(content().string(containsString("ISP2026000000004")));

        // Eski fatura daha yenisini işaret eder
        mvc.perform(get("/f/" + oct)).andExpect(content().string(containsString("Bu faturadan sonra kesilmiş bir faturanız var")));

        // Ekim ödenince Kasım sayfası güncel borcu gösterir
        long octId = jdbc.sql("select id from invoice where view_token = ?").param(oct).query(Long.class).single();
        mvc.perform(post("/api/v1/payments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":%d,\"amount\":396.00,\"method\":\"CASH\"}".formatted(octId)))
                .andExpect(status().isCreated());
        mvc.perform(get("/f/" + nov))
                .andExpect(content().string(not(containsString("Önceki dönemlerden kalan"))))
                .andExpect(content().string(containsString("Toplam borcunuz")));
        mvc.perform(get("/f/" + oct)).andExpect(content().string(containsString("Ödendi ✓")));

        mvc.perform(get("/f/" + "0".repeat(64))).andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Fatura bulunamadı")));
        mvc.perform(get("/f/kisa")).andExpect(status().isNotFound());
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());           // panel hâlâ girişe kapalı
    }

    // ---------- yardımcılar ----------

    private List<String> channels(long customerId) {
        return jdbc.sql("select n.channel || ':' || n.recipient from notification n join invoice i on i.id = n.invoice_id "
                        + "where i.customer_id = ? order by n.id")
                .param(customerId).query(String.class).list();
    }

    private Map<String, Object> smsRow(long customerId) {
        return jdbc.sql("select n.* from notification n join invoice i on i.id = n.invoice_id where i.customer_id = ? and n.channel = 'SMS'")
                .param(customerId).query().singleRow();
    }

    private String token(long customerId, String periodStart) {
        return jdbc.sql("select view_token from invoice where customer_id = ? and period_start = ?::date")
                .params(customerId, periodStart).query(String.class).single();
    }

    private long customer(String name, String taxId, String phone, String email) throws Exception {
        String emailJson = email == null ? "" : ",\"email\":\"" + email + "\"";
        String type = taxId.length() == 10 ? "CORPORATE" : "INDIVIDUAL";
        return create("/api/v1/customers", """
                {"type":"%s","name":"%s","taxId":"%s","phone":"%s"%s}""".formatted(type, name, taxId, phone, emailJson));
    }

    private long create(String url, String body) throws Exception {
        String response = mvc.perform(post(url).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }
}
