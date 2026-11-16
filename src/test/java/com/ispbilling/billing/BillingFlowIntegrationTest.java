package com.ispbilling.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.ispbilling.TestClockConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uçtan uca akış: abone → tarife → abonelik → bill run → fatura → tahsilat.
 * Gerçek PostgreSQL (billing_test) üzerinde çalışır; her test sonunda geri alınır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Import(TestClockConfig.class)
class BillingFlowIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void fullBillingCycle() throws Exception {
        long customerId = create("/api/v1/customers", """
                {"type":"INDIVIDUAL","name":"Ayşe Yılmaz","taxId":"12345678901","phone":"05551112233"}
                """);
        long fiber = create("/api/v1/plans", """
                {"code":"FIBER100","name":"Fiber 100 Mbps","monthlyFee":300.00,"oivRate":10,"vatRate":20}
                """);
        long ip = create("/api/v1/plans", """
                {"code":"STATICIP","name":"Sabit IP","monthlyFee":60.00,"oivRate":10,"vatRate":20}
                """);
        create("/api/v1/subscriptions", """
                {"customerId":%d,"planId":%d,"startDate":"2026-10-01"}
                """.formatted(customerId, fiber));
        create("/api/v1/subscriptions", """
                {"customerId":%d,"planId":%d,"startDate":"2026-11-16"}
                """.formatted(customerId, ip));

        // Kasım dönemini faturala
        mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-11\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.invoiceCount").value(1))
                // Fiber: 300 + 30 ÖİV + 66 KDV = 396 ; Sabit IP 15/30 gün: 30 + 3 + 6,60 = 39,60
                .andExpect(jsonPath("$.totalAmount").value(435.60));

        // Aynı dönem ikinci kez faturalanamaz
        mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-11\"}"))
                .andExpect(status().isConflict());

        JsonNode invoices = read(mvc.perform(get("/api/v1/invoices").param("customerId", "" + customerId))
                .andExpect(status().isOk()));
        long invoiceId = invoices.get(0).get("id").asLong();

        mvc.perform(get("/api/v1/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNo").value(org.hamcrest.Matchers.matchesPattern("ISP2026\\d{9}")))
                .andExpect(jsonPath("$.issueDate").value("2026-12-01"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-16"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].description").value("Sabit IP (15/30 gün kıst)"))
                .andExpect(jsonPath("$.netAmount").value(330.00))
                .andExpect(jsonPath("$.oivAmount").value(33.00))
                .andExpect(jsonPath("$.vatAmount").value(72.60))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        // Kısmi ödeme, fazla ödeme reddi, kalan ödeme
        pay(invoiceId, "200.00", status().isCreated())
                .andExpect(jsonPath("$.invoiceStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.invoiceOpenAmount").value(235.60));
        pay(invoiceId, "500.00", status().isConflict());
        pay(invoiceId, "235.60", status().isCreated())
                .andExpect(jsonPath("$.invoiceStatus").value("PAID"));
    }

    @Test
    void terminatedSubscription_notBilledAfterEndDate() throws Exception {
        long customerId = create("/api/v1/customers", """
                {"type":"CORPORATE","name":"Örnek Ltd. Şti.","taxId":"1234567890","taxOffice":"Kadıköy"}
                """);
        long plan = create("/api/v1/plans", """
                {"code":"FIBER50","name":"Fiber 50 Mbps","monthlyFee":250.00,"oivRate":10,"vatRate":20}
                """);
        long sub = create("/api/v1/subscriptions", """
                {"customerId":%d,"planId":%d,"startDate":"2026-09-01"}
                """.formatted(customerId, plan));
        mvc.perform(post("/api/v1/subscriptions/" + sub + "/terminate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-10-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-11\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceCount").value(0));
    }

    @Test
    void overdueList_showsUnpaidInvoicesPastDueDate() throws Exception {
        long customerId = create("/api/v1/customers", """
                {"type":"INDIVIDUAL","name":"Mehmet Demir","taxId":"10987654321"}
                """);
        long plan = create("/api/v1/plans", """
                {"code":"WISP20","name":"Kablosuz 20 Mbps","monthlyFee":200.00,"oivRate":10,"vatRate":20}
                """);
        create("/api/v1/subscriptions", """
                {"customerId":%d,"planId":%d,"startDate":"2026-09-01"}
                """.formatted(customerId, plan));

        mvc.perform(post("/api/v1/bill-runs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"period\":\"2026-10\"}")).andExpect(status().isCreated());

        // Fatura 1 Aralık'ta kesildi, vadesi 16 Aralık: henüz borçlu listesinde değil
        mvc.perform(get("/api/v1/invoices/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Vadeyi geçmişe çek: artık borçlu listesinde
        jdbc.update("update invoice set due_date = date '2026-11-20' where customer_id = ?", customerId);
        JsonNode overdue = read(mvc.perform(get("/api/v1/invoices/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Mehmet Demir"))
                .andExpect(jsonPath("$[0].openAmount").value(264.00)));

        // Ödeme gelince listeden düşer
        pay(overdue.get(0).get("id").asLong(), "264.00", status().isCreated());
        mvc.perform(get("/api/v1/invoices/overdue"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validationErrors_return400() throws Exception {
        mvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INDIVIDUAL\",\"name\":\"X\",\"taxId\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    private long create(String url, String body) throws Exception {
        return read(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions pay(long invoiceId, String amount,
                                                                  ResultMatcher expected) throws Exception {
        return mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invoiceId":%d,"amount":%s,"method":"ONLINE","reference":"TEST"}
                                """.formatted(invoiceId, amount)))
                .andExpect(expected);
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
