package com.ispbilling.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Object id) {
        super(entity + " bulunamadı: " + id);
    }
}
