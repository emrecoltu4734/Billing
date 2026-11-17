package com.ispbilling.notification;

import jakarta.mail.MessagingException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

/** İSS'nin kendi SMTP sunucusu üzerinden gönderim (spring.mail.host, port, username, password). */
class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailGateway(JavaMailSender mailSender, String from) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("billing.notification.email.from tanımlanmalı");
        }
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new DeliveryException("E-posta gönderilemedi: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
        }
    }
}
