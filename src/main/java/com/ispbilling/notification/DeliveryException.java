package com.ispbilling.notification;

/** Gönderim başarısız. Mesaj panelde görünür ve veritabanına yazılır; şifre/URL içermemelidir. */
public class DeliveryException extends RuntimeException {
    public DeliveryException(String message) {
        super(message);
    }
}
