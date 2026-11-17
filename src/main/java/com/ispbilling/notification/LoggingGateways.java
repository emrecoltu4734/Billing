package com.ispbilling.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Demo ve test için: gerçekten göndermez, loga yazar. */
final class LoggingGateways {

    private static final Logger log = LoggerFactory.getLogger(LoggingGateways.class);

    private LoggingGateways() {
    }

    static EmailGateway email() {
        return (to, subject, html) -> log.info("[E-POSTA gönderilmedi, LOG modu] {} · {}", to, subject);
    }

    static SmsGateway sms() {
        return (phone, text) -> log.info("[SMS gönderilmedi, LOG modu] {} · {}", phone, text);
    }

    static EmailGateway disabledEmail() {
        return (to, subject, html) -> {
            throw new DeliveryException("E-posta gönderimi kapalı (billing.notification.email.mode=OFF)");
        };
    }

    static SmsGateway disabledSms() {
        return (phone, text) -> {
            throw new DeliveryException("SMS gönderimi kapalı (billing.notification.sms.mode=OFF)");
        };
    }
}
