package com.ispbilling.payment;

import com.ispbilling.invoice.InvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    public record PaymentResponse(Long id, Long invoiceId, BigDecimal amount, PaymentMethod method,
                                  String reference, OffsetDateTime paidAt, InvoiceStatus invoiceStatus,
                                  BigDecimal invoiceOpenAmount) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@Valid @RequestBody PaymentRequest r) {
        Payment p = service.pay(r.invoiceId(), r.amount(), r.method(), r.reference());
        return new PaymentResponse(p.getId(), p.getInvoice().getId(), p.getAmount(), p.getMethod(),
                p.getReference(), p.getPaidAt(), p.getInvoice().getStatus(), p.getInvoice().getOpenAmount());
    }
}
