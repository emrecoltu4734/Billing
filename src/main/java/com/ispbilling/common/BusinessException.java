package com.ispbilling.common;

/** İş kuralı ihlali (ör. aynı dönemin ikinci kez faturalanması). HTTP 409 döner. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
