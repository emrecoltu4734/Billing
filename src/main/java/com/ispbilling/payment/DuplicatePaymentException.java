package com.ispbilling.payment;

import com.ispbilling.common.BusinessException;

/** Aynı dış işlem kimliğiyle ikinci kez ödeme kaydedilmeye çalışıldı. */
public class DuplicatePaymentException extends BusinessException {
    public DuplicatePaymentException(String externalRef) {
        super("Bu işlem kimliğiyle ödeme zaten kayıtlı: " + externalRef);
    }
}
