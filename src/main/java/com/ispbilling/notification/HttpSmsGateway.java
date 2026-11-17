package com.ispbilling.notification;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Sağlayıcıdan bağımsız HTTP SMS gönderimi. Netgsm, İleti Merkezi, Mutlucell gibi sağlayıcıların
 * HTTP API'leri URL/gövde şablonuyla yapılandırılır; sağlayıcıya özel kod yazılmaz.
 *
 * <p>Hata mesajlarına URL eklenmez (URL şifre içerebilir).
 */
class HttpSmsGateway implements SmsGateway {

    private final RestClient http;
    private final NotificationProperties.Http config;
    private final HttpMethod method;
    private final Pattern successPattern;

    HttpSmsGateway(RestClient.Builder builder, NotificationProperties.Http config) {
        if (config == null || config.url() == null || config.url().isBlank()) {
            throw new IllegalStateException("billing.notification.sms.http.url tanımlanmalı");
        }
        this.http = builder.build();
        this.config = config;
        this.method = HttpMethod.valueOf(config.method() == null ? "GET" : config.method().toUpperCase());
        this.successPattern = config.successPattern() == null || config.successPattern().isBlank()
                ? null : Pattern.compile(config.successPattern());
    }

    @Override
    public void send(String phone, String text) {
        Map<String, String> values = Map.of(
                "phone", phone, "message", text,
                "username", nz(config.username()), "password", nz(config.password()), "sender", nz(config.sender()));
        URI uri = URI.create(expand(config.url(), values, HttpSmsGateway::urlEncode));
        String response;
        try {
            if (method == HttpMethod.GET) {
                response = http.get().uri(uri).retrieve().body(String.class);
            } else {
                String contentType = config.contentType() == null ? MediaType.APPLICATION_FORM_URLENCODED_VALUE : config.contentType();
                String body = expand(nz(config.body()), values, encoderFor(contentType));
                response = http.method(method).uri(uri).contentType(MediaType.parseMediaType(contentType))
                        .body(body).retrieve().body(String.class);
            }
        } catch (RestClientResponseException e) {
            throw new DeliveryException("SMS sağlayıcısı hata döndü: HTTP " + e.getStatusCode().value() + " " + shorten(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new DeliveryException("SMS sağlayıcısına bağlanılamadı");
        }
        if (successPattern != null && (response == null || !successPattern.matcher(response).find())) {
            throw new DeliveryException("SMS sağlayıcısı başarısız yanıt verdi: " + shorten(response));
        }
    }

    static String expand(String template, Map<String, String> values, UnaryOperator<String> encoder) {
        String result = template;
        for (var e : values.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", encoder.apply(e.getValue()));
        }
        return result;
    }

    private static UnaryOperator<String> encoderFor(String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("json")) return HttpSmsGateway::jsonEscape;
        if (ct.contains("xml")) return HttpSmsGateway::xmlEscape;
        if (ct.contains("x-www-form-urlencoded")) return HttpSmsGateway::urlEncode;
        return UnaryOperator.identity();
    }

    static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String jsonEscape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String shorten(String s) {
        if (s == null) return "(boş)";
        s = s.strip();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
