package com.ispbilling.web;

import com.ispbilling.TestClockConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestClockConfig.class)
class PanelIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Nested
    class Security {

        @Test
        void panelRedirectsToLoginWhenNotLoggedIn() throws Exception {
            mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
            mvc.perform(get("/login")).andExpect(status().isOk());
        }

        @Test
        void apiRequiresAuthentication() throws Exception {
            mvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
        }

        @Test
        void adminCanLogIn() throws Exception {
            mvc.perform(formLogin("/login").user("admin").password("test-sifre"))
                    .andExpect(authenticated().withUsername("admin"))
                    .andExpect(redirectedUrl("/"));
            mvc.perform(formLogin("/login").user("admin").password("yanlis"))
                    .andExpect(unauthenticated());
        }

        @Test
        @WithMockUser
        void formsRequireCsrfToken() throws Exception {
            mvc.perform(post("/plans").param("code", "X")).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser
    void fullFlowThroughPanel() throws Exception {
        // Tarife: Türkçe tutar biçimi kabul edilmeli
        mvc.perform(post("/plans").with(csrf())
                        .param("code", "fiber100").param("name", "Fiber 100 Mbps")
                        .param("monthlyFee", "300,00").param("oivRate", "10").param("vatRate", "20"))
                .andExpect(redirectedUrl("/plans"))
                .andExpect(flash().attribute("success", "Tarife eklendi: FIBER100"));
        mvc.perform(get("/plans"))
                .andExpect(content().string(containsString("FIBER100")))
                .andExpect(content().string(containsString("396,00 ₺")));   // vergiler dahil
        String planId = idFromPage("/plans", "/plans/(\\d+)/active");

        // Abone
        String location = mvc.perform(post("/customers").with(csrf())
                        .param("type", "INDIVIDUAL").param("name", "Ayşe Yılmaz").param("taxId", "12345678901")
                        .param("phone", "05551112233"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ayşe Yılmaz")))
                .andExpect(content().string(containsString("Bu abonenin aboneliği yok")));

        // Abonelik
        mvc.perform(post(location + "/subscriptions").with(csrf())
                        .param("planId", planId).param("startDate", "2026-10-01"))
                .andExpect(redirectedUrl(location))
                .andExpect(flash().attribute("success", "Abonelik başlatıldı"));

        // Fatura kes
        mvc.perform(post("/bill-runs").with(csrf()).param("period", "2026-11"))
                .andExpect(redirectedUrl("/bill-runs"))
                .andExpect(flash().attribute("success", "Kasım 2026 dönemi faturalandı: 1 fatura"));
        mvc.perform(post("/bill-runs").with(csrf()).param("period", "2026-11"))
                .andExpect(flash().attribute("error", "Kasım 2026 dönemi zaten faturalandı"));

        mvc.perform(get("/invoices"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ayşe Yılmaz")));
        String invoiceUrl = "/invoices/" + idFromPage(location, "/invoices/(\\d+)\"");
        mvc.perform(get(invoiceUrl))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kasım 2026 faturası")))
                .andExpect(content().string(containsString("396,00 ₺")))
                .andExpect(content().string(containsString("value=\"396,00\"")));  // tahsilat formu kalan tutarla dolu

        // Fazla ödeme reddedilir, Türkçe tutarla tam ödeme kabul edilir
        mvc.perform(post(invoiceUrl + "/payments").with(csrf()).param("amount", "1.000,00").param("method", "CASH"))
                .andExpect(flash().attribute("error", startsWith("Ödeme tutarı açık bakiyeden fazla")));
        mvc.perform(post(invoiceUrl + "/payments").with(csrf()).param("amount", "396,00").param("method", "CASH")
                        .param("reference", "Makbuz 1"))
                .andExpect(flash().attribute("success", "Tahsilat kaydedildi"));
        mvc.perform(get(invoiceUrl))
                .andExpect(content().string(containsString("Ödendi")))
                .andExpect(content().string(containsString("Makbuz 1")))
                .andExpect(content().string(not(containsString("Tahsilat kaydet"))));

        // Kısıtla → aç → sonlandır
        String subId = idFromPage(location, "/subscriptions/(\\d+)/suspend");
        mvc.perform(post("/subscriptions/" + subId + "/suspend").with(csrf()))
                .andExpect(flash().attribute("success", "Abonelik kısıtlandı"));
        mvc.perform(post("/subscriptions/" + subId + "/resume").with(csrf()))
                .andExpect(flash().attribute("success", "Abonelik yeniden açıldı"));
        mvc.perform(post("/subscriptions/" + subId + "/terminate").with(csrf()).param("endDate", "2026-12-15"))
                .andExpect(flash().attribute("success", "Abonelik sonlandırıldı"));

        // Diğer sayfalar açılıyor
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aylık tekrarlayan gelir")));
        mvc.perform(get("/overdue")).andExpect(status().isOk());
        mvc.perform(get("/bill-runs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Kasım 2026")));
        mvc.perform(get("/customers").param("q", "ayşe"))
                .andExpect(content().string(containsString("Ayşe Yılmaz")));
    }

    @Test
    @WithMockUser
    void invalidCustomerFormShowsErrorsAndKeepsInput() throws Exception {
        mvc.perform(post("/customers").with(csrf())
                        .param("type", "CORPORATE").param("name", "Örnek Ltd").param("taxId", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/new"))
                .andExpect(content().string(containsString("TCKN 11, VKN 10 haneli olmalı")))
                .andExpect(content().string(containsString("value=\"Örnek Ltd\"")))
                .andExpect(content().string(containsString("value=\"CORPORATE\" selected")));
    }

    @Test
    @WithMockUser
    void unknownRecordShows404Page() throws Exception {
        mvc.perform(get("/customers/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Kayıt bulunamadı")));
    }

    /** Sayfadaki ilk eşleşmeden kayıt id'sini çeker. */
    private String idFromPage(String url, String regex) throws Exception {
        String html = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
        var m = java.util.regex.Pattern.compile(regex).matcher(html);
        if (!m.find()) {
            throw new AssertionError("Sayfada bulunamadı: " + regex + " (" + url + ")");
        }
        return m.group(1);
    }
}
