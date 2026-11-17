package com.ispbilling.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Testlerde gönderilenleri kaydeden, istenen alıcılarda hata veren sahte e-posta/SMS kapıları. */
@TestConfiguration
public class RecordingGateways {

    public record Sent(String to, String subject, String body) {
    }

    public static final List<Sent> EMAILS = new CopyOnWriteArrayList<>();
    public static final List<Sent> SMS = new CopyOnWriteArrayList<>();
    public static final Set<String> FAILING = ConcurrentHashMap.newKeySet();

    public static void clear() {
        EMAILS.clear();
        SMS.clear();
        FAILING.clear();
    }

    @Bean
    @Primary
    EmailGateway recordingEmail() {
        return (to, subject, html) -> {
            if (FAILING.contains(to)) throw new DeliveryException("SMTP sunucusu yanıt vermedi");
            EMAILS.add(new Sent(to, subject, html));
        };
    }

    @Bean
    @Primary
    SmsGateway recordingSms() {
        return (phone, text) -> {
            if (FAILING.contains(phone)) throw new DeliveryException("SMS sağlayıcısı hata döndü: HTTP 503");
            SMS.add(new Sent(phone, null, text));
        };
    }
}
