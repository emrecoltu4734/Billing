package com.ispbilling.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicBaseUrl abonelerin fatura bağlantısını açacağı adres, ör. https://fatura.ornekisp.com.tr
 *                      (yalnızca /f/** yolu internete açılır; panel iç ağda kalır)
 */
@ConfigurationProperties(prefix = "billing")
public record BillingProperties(String invoicePrefix, int paymentTermDays, Admin admin, String publicBaseUrl,
                                Company company) {

    public record Admin(String username, String password) {
    }

    /** Faturada görünen İSS bilgileri. */
    public record Company(String name, String address, String taxOffice, String taxId, String phone, String email,
                          String website, String iban) {
    }

    public Company company() {
        return company != null ? company : new Company("İnternet Servis Sağlayıcı", null, null, null, null, null, null, null);
    }

    /** Sonunda / olmadan. */
    public String publicBaseUrl() {
        String url = publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost:8080" : publicBaseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
