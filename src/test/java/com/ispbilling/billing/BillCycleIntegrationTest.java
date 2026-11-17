package com.ispbilling.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispbilling.TestClockConfig;
import com.ispbilling.TestDatabase;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Birden fazla fatura dönemi, dönem değişikliği, hatalı abonede devam ve otomatik faturalama. Bugün: 01.12.2026. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@WithMockUser
class BillCycleIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    BillRunService billRuns;

    long fiber;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.reset(jdbc);
        fiber = create("/api/v1/plans", """
                {"code":"FIBER100","name":"Fiber 100 Mbps","monthlyFee":300.00,"oivRate":10,"vatRate":20}""");
        create("/api/v1/bill-cycles", """
                {"code":"c15","name":"Ayın 15'i","cycleDay":15}""");
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("drop trigger if exists test_fail_invoice on invoice").update();
        TestDatabase.reset(jdbc);
    }

    @Test
    void customerInCycle15IsBilledForItsOwnPeriod() throws Exception {
        long customer = customer("Ayşe Yılmaz", "C15");
        subscribe(customer, "2026-09-01");

        run("C15", "2026-10").andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycleCode").value("C15"))
                .andExpect(jsonPath("$.periodStart").value("2026-10-15"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-14"))
                .andExpect(jsonPath("$.invoiceCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(396.00));   // tam dönem, 31/31 gün

        // Varsayılan (ayın 1'i) çalıştırması bu aboneyi faturalamaz
        run(null, "2026-10").andExpect(jsonPath("$.invoiceCount").value(0));
    }

    @Test
    void periodThatHasNotEndedIsRejected() throws Exception {
        customer("Ayşe Yılmaz", "C15");
        // Kasım dönemi 15.11 – 14.12; bugün 01.12
        run("C15", "2026-11").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(startsWith("Kasım 2026 dönemi (Ayın 15'i) henüz bitmedi")));
    }

    @Test
    void changingCycleNeverBillsTheSameDaysTwice() throws Exception {
        long customer = customer("Ayşe Yılmaz", null);   // varsayılan: ayın 1'i
        long sub = subscribe(customer, "2026-09-01");
        run(null, "2026-10").andExpect(jsonPath("$.totalAmount").value(396.00));   // 1–31 Ekim
        assertThat(billedThrough(sub)).isEqualTo("2026-10-31");

        mvc.perform(put("/api/v1/customers/" + customer + "/bill-cycle").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billCycleCode\":\"C15\"}"))
                .andExpect(jsonPath("$.billCycleCode").value("C15"));

        // 15 Ekim – 14 Kasım dönemi: 15–31 Ekim zaten faturalı, yalnızca 1–14 Kasım (14/31 gün)
        // 300 × 14 / 31 = 135,48 ; ÖİV 13,55 ; KDV (149,03 × %20) = 29,81 ; toplam 178,84
        run("C15", "2026-10").andExpect(jsonPath("$.totalAmount").value(178.84));
        assertThat(billedThrough(sub)).isEqualTo("2026-11-14");
        assertThat(jdbc.sql("select description from invoice_line order by id desc limit 1").query(String.class).single())
                .isEqualTo("Fiber 100 Mbps (14/31 gün kıst)");
    }

    @Test
    void terminatingBeforeAlreadyBilledDateIsRejected() throws Exception {
        long customer = customer("Ayşe Yılmaz", null);
        long sub = subscribe(customer, "2026-09-01");
        run(null, "2026-10");
        mvc.perform(post("/api/v1/subscriptions/" + sub + "/terminate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-10-20\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("31.10.2026 tarihine kadar faturalandı")));
    }

    @Test
    void failingCustomerDoesNotStopRunAndCanBeResumed() throws Exception {
        long ok = customer("Sorunsuz Abone", null);
        long bad = customer("Sorunlu Abone", null);
        subscribe(ok, "2026-09-01");
        subscribe(bad, "2026-09-01");
        // Veritabanı seviyesinde bir hata canlandırılır: yalnızca bu abonenin faturası yazılamaz
        jdbc.sql("""
                create or replace function test_fail_invoice() returns trigger as $$
                begin
                  if new.customer_id = %d then raise exception 'Test: fatura yazılamadı'; end if;
                  return new;
                end $$ language plpgsql""".formatted(bad)).update();
        jdbc.sql("create trigger test_fail_invoice before insert on invoice for each row execute function test_fail_invoice()").update();

        String body = run(null, "2026-10").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.invoiceCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andReturn().getResponse().getContentAsString();
        long runId = json.readTree(body).get("id").asLong();
        mvc.perform(get("/api/v1/bill-runs/" + runId))
                .andExpect(jsonPath("$.errors[0].customerName").value("Sorunlu Abone"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Test: fatura yazılamadı")));

        // Sorun giderilir, aynı dönem tekrar çalıştırılır: yalnızca kalan abone faturalanır
        jdbc.sql("drop trigger test_fail_invoice on invoice").update();
        mvc.perform(post("/bill-runs/" + runId + "/resume").with(csrf()))
                .andExpect(flash().attribute("success", "Kalan aboneler faturalandı. Toplam 2 fatura."));
        mvc.perform(get("/api/v1/bill-runs/" + runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.errors.length()").value(0));
        assertThat(jdbc.sql("select count(*) from invoice").query(Long.class).single()).isEqualTo(2);
        // Hatalı abone fatura numarası sırasında boşluk bırakmaz
        assertThat(jdbc.sql("select invoice_no from invoice order by invoice_no").query(String.class).list())
                .containsExactly("ISP2026000000001", "ISP2026000000002");

        // Artık tamamlandı: tekrar çalıştırılamaz
        run(null, "2026-10").andExpect(status().isConflict());
    }

    @Test
    void autoRunBillsOnlyCyclesWithAutoRunOnTheirDay() throws Exception {
        subscribe(customer("Birinci Abone", null), "2026-09-01");
        subscribe(customer("Onbeşinci Abone", "C15"), "2026-09-01");
        mvc.perform(post("/bill-cycles/C15/auto-run").with(csrf()).param("enabled", "true"))
                .andExpect(flash().attribute("success", "C15: fatura gününde otomatik faturalanacak"));

        assertThat(billRuns.runDueCycles(LocalDate.of(2026, 11, 14))).isEmpty();          // fatura günü değil
        var runs = billRuns.runDueCycles(LocalDate.of(2026, 11, 15));
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().cycleCode()).isEqualTo("C15");
        assertThat(runs.getFirst().periodStart()).isEqualTo("2026-10-15");
        assertThat(billRuns.runDueCycles(LocalDate.of(2026, 11, 15))).isEmpty();          // ikinci kez çalışmaz
        // Ayın 1'i otomatik değil: faturalanmadı
        assertThat(jdbc.sql("select count(*) from bill_run where bill_cycle_id = (select id from bill_cycle where code='C01')")
                .query(Long.class).single()).isZero();
    }

    @Test
    void panelManagesCyclesAndRunsPerCycle() throws Exception {
        long customer = customer("Ayşe Yılmaz", null);
        subscribe(customer, "2026-09-01");

        mvc.perform(post("/bill-cycles").with(csrf()).param("code", "c20").param("name", "Ayın 20'si").param("cycleDay", "20"))
                .andExpect(flash().attribute("success", "Fatura dönemi eklendi: C20"));
        mvc.perform(post("/bill-cycles").with(csrf()).param("code", "C31").param("name", "Hatalı").param("cycleDay", "31"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fatura günü 1-28 arasında olmalı")));
        mvc.perform(post("/bill-cycles/C20/default").with(csrf()))
                .andExpect(flash().attribute("success", "Yeni aboneler artık bu döneme atanacak: C20"));
        assertThat(jdbc.sql("select code from bill_cycle where is_default").query(String.class).single()).isEqualTo("C20");

        mvc.perform(post("/customers/" + customer + "/bill-cycle").with(csrf()).param("billCycleCode", "C15"))
                .andExpect(flash().attribute("success", startsWith("Fatura dönemi değiştirildi")));
        mvc.perform(post("/bill-runs").with(csrf()).param("cycleCode", "C15").param("period", "2026-10"))
                .andExpect(flash().attribute("success", "Ayın 15'i · Ekim 2026 dönemi faturalandı: 1 fatura"));

        mvc.perform(get("/bill-runs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ayın 20&#39;si")))
                .andExpect(content().string(containsString("15.10.2026 – 14.11.2026")));
        mvc.perform(get("/customers/new")).andExpect(content().string(containsString("Ayın 15&#39;i (ayın 15&#39;i)")));
        long runId = jdbc.sql("select id from bill_run").query(Long.class).single();
        mvc.perform(get("/bill-runs/" + runId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tüm aboneler faturalandı.")));
    }

    // ---------- yardımcılar ----------

    private long customer(String name, String cycle) throws Exception {
        String cycleJson = cycle == null ? "" : ",\"billCycleCode\":\"" + cycle + "\"";
        return create("/api/v1/customers", """
                {"type":"INDIVIDUAL","name":"%s","taxId":"12345678901"%s}""".formatted(name, cycleJson));
    }

    private long subscribe(long customer, String start) throws Exception {
        return create("/api/v1/subscriptions", """
                {"customerId":%d,"planId":%d,"startDate":"%s"}""".formatted(customer, fiber, start));
    }

    private ResultActions run(String cycle, String period) throws Exception {
        String cycleJson = cycle == null ? "" : "\"cycleCode\":\"" + cycle + "\",";
        return mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON)
                .content("{" + cycleJson + "\"period\":\"" + period + "\"}"));
    }

    private long create(String url, String body) throws Exception {
        String response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private LocalDate billedThrough(long subscriptionId) {
        return jdbc.sql("select billed_through from subscription where id = ?").param(subscriptionId)
                .query(LocalDate.class).single();
    }
}
