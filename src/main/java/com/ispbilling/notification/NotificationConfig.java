package com.ispbilling.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

@Configuration
class NotificationConfig {

    @Bean
    EmailGateway emailGateway(NotificationProperties props, ObjectProvider<JavaMailSender> mailSender) {
        return switch (props.email().mode()) {
            case OFF -> LoggingGateways.disabledEmail();
            case LOG -> LoggingGateways.email();
            case SMTP -> {
                JavaMailSender sender = mailSender.getIfAvailable();
                if (sender == null) {
                    throw new IllegalStateException("billing.notification.email.mode=SMTP için spring.mail.host tanımlanmalı");
                }
                yield new SmtpEmailGateway(sender, props.email().from());
            }
            case HTTP -> throw new IllegalStateException("E-posta için HTTP modu desteklenmiyor; SMTP kullanın");
        };
    }

    @Bean
    SmsGateway smsGateway(NotificationProperties props, RestClient.Builder restClientBuilder) {
        return switch (props.sms().mode()) {
            case OFF -> LoggingGateways.disabledSms();
            case LOG -> LoggingGateways.sms();
            case HTTP -> new HttpSmsGateway(restClientBuilder, props.sms().http());
            case SMTP -> throw new IllegalStateException("SMS için SMTP modu desteklenmiyor; HTTP kullanın");
        };
    }
}
