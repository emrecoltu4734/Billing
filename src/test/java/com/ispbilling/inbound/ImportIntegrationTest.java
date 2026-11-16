package com.ispbilling.inbound;

import com.ispbilling.TestClockConfig;
import com.ispbilling.billing.BillRunService;
import com.ispbilling.plan.CreatePlanRequest;
import com.ispbilling.plan.PlanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Giriş tabloları → import batch → core tablolar.
 * Aktarım her satırı ayrı transaction'da commit ettiği için bu test geri alma (rollback) kullanmaz;
 * her testten önce tabloları temizler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ImportIntegrationTest {

    @Autowired
    ImportService importService;
    @Autowired
    PlanService plans;
    @Autowired
    BillRunService billRuns;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        truncateAll();
        plans.create(new CreatePlanRequest("FIBER100", "Fiber 100 Mbps", new BigDecimal("300.00"),
                BigDecimal.TEN, BigDecimal.valueOf(20)));
    }

    /** Diğer test sınıfları geri almaya (rollback) güvendiği için arkada veri bırakılmaz. */
    @AfterEach
    void truncateAll() {
        jdbc.sql("""
                truncate in_customer, in_subscription_event, in_payment,
                         payment, invoice_line, invoice, bill_run, subscription, customer, plan
                restart identity cascade
                """).update();
    }

    @Test
    void fullFlowThroughInterfaceTables() {
        insertCustomer("CRM-1", "INDIVIDUAL", "Ayşe Yılmaz", "12345678901");
        insertSubscription("SRV-1", "ACTIVATE", "CRM-1", "FIBER100", "2026-11-16");

        ImportService.RunResult run = importService.runAll();

        assertThat(run.total()).isEqualTo(2);
        assertThat(run.errors()).isZero();
        assertThat(row("in_customer", 1)).containsEntry("status", "DONE").containsEntry("core_id", 1L);
        assertThat(row("in_subscription_event", 1)).containsEntry("status", "DONE");

        // Fatura günü: kıst 15/30 gün → 150 + 15 ÖİV + 33 KDV = 198
        billRuns.run(YearMonth.of(2026, 11));
        String invoiceNo = jdbc.sql("select invoice_no from invoice").query(String.class).single();
        assertThat(jdbc.sql("select total_amount from invoice").query(BigDecimal.class).single())
                .isEqualByComparingTo("198.00");

        // Tahsilat giriş tablosundan gelir; aynı işlem kimliği tekrar gelirse yok sayılır
        insertPayment("PAY-1", invoiceNo, "198.00", "ONLINE");
        insertPayment("PAY-1", invoiceNo, "198.00", "ONLINE");
        run = importService.runAll();

        assertThat(run.tables().get(InboundTable.PAYMENT)).isEqualTo(new ImportService.TableResult(1, 0, 1));
        assertThat(jdbc.sql("select count(*) from payment").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select status from invoice").query(String.class).single()).isEqualTo("PAID");
        assertThat(row("in_payment", 2)).containsEntry("status", "DUPLICATE");
    }

    @Test
    void customerUpsertUpdatesExistingRecord() {
        insertCustomer("CRM-1", "INDIVIDUAL", "Ayse Yilmaz", "12345678901");
        importService.runAll();
        insertCustomer("CRM-1", "INDIVIDUAL", "Ayşe Yılmaz Demir", "12345678901");
        importService.runAll();

        assertThat(jdbc.sql("select count(*) from customer").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select name from customer").query(String.class).single()).isEqualTo("Ayşe Yılmaz Demir");
    }

    @Test
    void badRowsAreMarkedAndDoNotStopOthers() {
        insertCustomer("CRM-1", "INDIVIDUAL", "Geçerli Abone", "12345678901");
        insertCustomer("CRM-2", "INDIVIDUAL", "Hatalı TCKN", "123");
        insertCustomer("CRM-3", "ROBOT", "Hatalı tip", "12345678901");
        insertSubscription("SRV-1", "ACTIVATE", "CRM-1", "YOKBOYLE", "2026-11-01");
        insertSubscription("SRV-2", "ACTIVATE", "CRM-404", "FIBER100", "2026-11-01");
        insertSubscription("SRV-3", "SUSPEND", null, null, null);
        insertSubscription("SRV-4", "ACTIVATE", "CRM-1", "FIBER100", "2026-11-01");
        insertSubscription("SRV-4", "ACTIVATE", "CRM-1", "FIBER100", "2026-11-01");
        insertPayment("PAY-1", "ISP2026999999999", "10.00", "CASH");

        ImportService.RunResult run = importService.runAll();

        assertThat(run.errors()).isEqualTo(7);
        assertThat(row("in_customer", 1)).containsEntry("status", "DONE");
        assertError("in_customer", 2, "VALIDATION", "TCKN 11, VKN 10 haneli olmalı");
        assertError("in_customer", 3, "VALIDATION", "type geçersiz: ROBOT");
        assertError("in_subscription_event", 1, "NOT_FOUND", "Tarife bulunamadı: YOKBOYLE");
        assertError("in_subscription_event", 2, "NOT_FOUND", "Abone bulunamadı: CRM-404");
        assertError("in_subscription_event", 3, "NOT_FOUND", "Abonelik bulunamadı: SRV-3");
        assertThat(row("in_subscription_event", 4)).containsEntry("status", "DONE");
        assertError("in_subscription_event", 5, "BUSINESS_RULE", "Bu dış kimlikle bir abonelik zaten var: SRV-4");
        assertError("in_payment", 1, "NOT_FOUND", "Fatura bulunamadı: ISP2026999999999");
        // Hatalı satırlar core'da iz bırakmaz
        assertThat(jdbc.sql("select count(*) from subscription").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void lifecycleEventsAndRetryAfterFix() {
        insertCustomer("CRM-1", "CORPORATE", "Örnek Ltd. Şti.", "1234567890");
        insertSubscription("SRV-1", "ACTIVATE", "CRM-1", "FIBER50", "2026-10-01");
        importService.runAll();
        assertError("in_subscription_event", 1, "NOT_FOUND", "Tarife bulunamadı: FIBER50");

        // Eksik tarife tanımlanır, hatalı satır yeniden denenir
        plans.create(new CreatePlanRequest("FIBER50", "Fiber 50 Mbps", new BigDecimal("250.00"),
                BigDecimal.TEN, BigDecimal.valueOf(20)));
        assertThat(importService.retryErrors(InboundTable.SUBSCRIPTION_EVENT)).isEqualTo(1);
        importService.runAll();
        assertThat(row("in_subscription_event", 1)).containsEntry("status", "DONE");

        insertSubscription("SRV-1", "SUSPEND", null, null, null);
        insertSubscription("SRV-1", "RESUME", null, null, null);
        insertSubscription("SRV-1", "TERMINATE", null, null, "2026-11-10");
        importService.runAll();

        var sub = jdbc.sql("select status, end_date::text as end_date from subscription").query().singleRow();
        assertThat(sub).containsEntry("status", "TERMINATED").containsEntry("end_date", "2026-11-10");
    }

    @Test
    @WithMockUser
    void panelShowsCountsAndErrors() throws Exception {
        insertCustomer("CRM-2", "INDIVIDUAL", "Hatalı TCKN", "123");
        mvc.perform(post("/imports/run").with(csrf()))
                .andExpect(redirectedUrl("/imports"))
                .andExpect(flash().attribute("error", "1 kayıt işlendi, 1 kayıtta hata var"));
        mvc.perform(get("/imports"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ErrorRow["))))
                .andExpect(content().string(containsString("TCKN 11, VKN 10 haneli olmalı")))
                .andExpect(content().string(containsString("CRM-2")));
        mvc.perform(post("/imports/customers/retry").with(csrf()))
                .andExpect(flash().attribute("success", "Aboneler: 1 hatalı kayıt yeniden kuyruğa alındı"));
    }

    @Test
    @WithMockUser
    void apiRunsImportAndReportsStatus() throws Exception {
        insertCustomer("CRM-1", "INDIVIDUAL", "Ayşe Yılmaz", "12345678901");
        mvc.perform(post("/api/v1/imports/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.CUSTOMER.done").value(1));
        mvc.perform(get("/api/v1/imports/status"))
                .andExpect(jsonPath("$.CUSTOMER.done").value(1))
                .andExpect(jsonPath("$.PAYMENT.pending").value(0));
    }

    // ---------- yardımcılar: müşteri sisteminin yaptığı gibi düz SQL ile ekleme ----------

    private void insertCustomer(String ref, String type, String name, String taxId) {
        jdbc.sql("insert into in_customer (external_ref, type, name, tax_id) values (?, ?, ?, ?)")
                .params(ref, type, name, taxId).update();
    }

    private void insertSubscription(String ref, String op, String customerRef, String plan, String date) {
        jdbc.sql("insert into in_subscription_event (external_ref, operation, customer_external_ref, plan_code, effective_date) "
                        + "values (?, ?, ?, ?, ?::date)")
                .params(ref, op, customerRef, plan, date).update();
    }

    private void insertPayment(String ref, String invoiceNo, String amount, String method) {
        jdbc.sql("insert into in_payment (external_ref, invoice_no, amount, method) values (?, ?, ?::numeric, ?)")
                .params(ref, invoiceNo, amount, method).update();
    }

    private Map<String, Object> row(String table, long id) {
        return jdbc.sql("select * from " + table + " where id = ?").param(id).query().singleRow();
    }

    private void assertError(String table, long id, String code, String message) {
        assertThat(row(table, id))
                .containsEntry("status", "ERROR")
                .containsEntry("error_code", code)
                .containsEntry("error_message", message);
    }
}
