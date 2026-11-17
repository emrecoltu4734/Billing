package com.ispbilling.notification;

public interface EmailGateway {
    void send(String to, String subject, String html);
}
