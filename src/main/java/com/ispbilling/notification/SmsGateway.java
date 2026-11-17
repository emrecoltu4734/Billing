package com.ispbilling.notification;

public interface SmsGateway {
    /** @param phone 905XXXXXXXXX biçiminde */
    void send(String phone, String text);
}
