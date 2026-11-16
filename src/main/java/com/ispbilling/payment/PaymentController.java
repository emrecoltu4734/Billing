package com.ispbilling.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    public record PaymentRequest(@NotNull Long invoiceId,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
                                 @NotNull PaymentMethod method,
                                 String reference) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView pay(@Valid @RequestBody PaymentRequest r) {
        return service.pay(r.invoiceId(), r.amount(), r.method(), r.reference());
    }
}
