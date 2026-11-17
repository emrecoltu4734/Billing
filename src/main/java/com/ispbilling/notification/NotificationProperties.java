package com.ispbilling.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Fatura gönderim ayarları ({@code billing.notification.*}).
 *
 * <ul>
 *   <li>{@code email.mode}: OFF (gönderme) · LOG (yalnızca loga yaz, demo/test) · SMTP (spring.mail.* ile)</li>
 *   <li>{@code sms.mode}: OFF · LOG · HTTP (sağlayıcının HTTP API'si, şablonla yapılandırılır)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "billing.notification")
public record NotificationProperties(Boolean dispatchEnabled, Duration interval, Email email, Sms sms) {

    public enum Mode { OFF, LOG, SMTP, HTTP }

    public record Email(Mode mode, String from) {
        public Mode mode() {
            return mode == null ? Mode.OFF : mode;
        }
    }

    public record Sms(Mode mode, Http http) {
        public Mode mode() {
            return mode == null ? Mode.OFF : mode;
        }
    }

    /**
     * Sağlayıcıdan bağımsız HTTP SMS ayarı. {@code url} ve {@code body} içinde şu yer tutucular kullanılabilir:
     * {phone} (905XXXXXXXXX), {message}, {username}, {password}, {sender}. Değerler içerik türüne göre
     * kodlanır (URL, form, JSON, XML).
     *
     * @param successPattern yanıt gövdesinde aranacak düzenli ifade; boşsa HTTP 2xx başarı sayılır
     */
    public record Http(String url, String method, String body, String contentType, String successPattern,
                       String username, String password, String sender) {
    }

    public Email email() {
        return email != null ? email : new Email(Mode.OFF, null);
    }

    public Sms sms() {
        return sms != null ? sms : new Sms(Mode.OFF, null);
    }

    public boolean emailEnabled() {
        return email().mode() != Mode.OFF;
    }

    public boolean smsEnabled() {
        return sms().mode() != Mode.OFF;
    }
}
