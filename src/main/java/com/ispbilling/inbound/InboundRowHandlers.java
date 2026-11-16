package com.ispbilling.inbound;

import com.ispbilling.customer.CreateCustomerRequest;
import com.ispbilling.customer.CustomerService;
import com.ispbilling.customer.CustomerType;
import com.ispbilling.invoice.InvoiceService;
import com.ispbilling.payment.PaymentMethod;
import com.ispbilling.payment.PaymentService;
import com.ispbilling.plan.Plan;
import com.ispbilling.plan.PlanService;
import com.ispbilling.subscription.SubscriptionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Her giriş tablosunun satırını core servislerine çevirir. Core tablolara yalnızca servisler
 * üzerinden dokunulur; böylece panel, API ve entegrasyon aynı iş kurallarından geçer.
 */
@Component
class InboundRowHandlers {

    /** Satır işlendiğinde sonuç: DONE veya DUPLICATE ve ilgili core kaydının id'si. */
    record Result(String status, Long coreId, String message) {
        static Result done(Long coreId) {
            return new Result("DONE", coreId, null);
        }

        static Result duplicate(String message) {
            return new Result("DUPLICATE", null, message);
        }
    }

    private final CustomerService customers;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final Validator validator;

    InboundRowHandlers(CustomerService customers, PlanService plans, SubscriptionService subscriptions,
                       InvoiceService invoices, PaymentService payments, Validator validator) {
        this.customers = customers;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.payments = payments;
        this.validator = validator;
    }

    Result handle(InboundTable table, InboundRow row) {
        return switch (table) {
            case CUSTOMER -> customer(row);
            case SUBSCRIPTION_EVENT -> subscriptionEvent(row);
            case PAYMENT -> payment(row);
        };
    }

    // ---------- in_customer ----------

    private Result customer(InboundRow row) {
        String ref = row.requiredText("external_ref");
        CreateCustomerRequest request = new CreateCustomerRequest(
                row.enumValue("type", CustomerType.class), row.text("name"), row.text("tax_id"),
                row.text("tax_office"), row.text("phone"), row.text("email"), row.text("address"));
        validate(request);
        return Result.done(customers.upsertByExternalRef(ref, request));
    }

    // ---------- in_subscription_event ----------

    private Result subscriptionEvent(InboundRow row) {
        String ref = row.requiredText("external_ref");
        String operation = row.requiredText("operation");
        return switch (operation) {
            case "ACTIVATE" -> {
                String customerRef = row.requiredText("customer_external_ref");
                Long customerId = customers.findIdByExternalRef(customerRef)
                        .orElseThrow(() -> ImportException.notFound("Abone bulunamadı: " + customerRef));
                String planCode = row.requiredText("plan_code");
                Plan plan = plans.findByCode(planCode)
                        .orElseThrow(() -> ImportException.notFound("Tarife bulunamadı: " + planCode));
                yield Result.done(subscriptions.create(customerId, plan.getId(), row.requiredDate("effective_date"), ref).id());
            }
            case "SUSPEND" -> Result.done(subscriptions.suspend(subscriptionId(ref)).id());
            case "RESUME" -> Result.done(subscriptions.resume(subscriptionId(ref)).id());
            case "TERMINATE" -> Result.done(subscriptions.terminate(subscriptionId(ref), row.requiredDate("effective_date")).id());
            default -> throw ImportException.validation("operation geçersiz: " + operation);
        };
    }

    private Long subscriptionId(String ref) {
        return subscriptions.findIdByExternalRef(ref)
                .orElseThrow(() -> ImportException.notFound("Abonelik bulunamadı: " + ref));
    }

    // ---------- in_payment ----------

    private Result payment(InboundRow row) {
        String ref = row.requiredText("external_ref");
        String invoiceNo = row.text("invoice_no");
        if (invoiceNo == null) {
            throw ImportException.validation("invoice_no zorunlu (faturaya bağlı olmayan ödeme henüz desteklenmiyor)");
        }
        BigDecimal amount = row.decimal("amount");
        if (amount == null || amount.signum() <= 0) {
            throw ImportException.validation("amount sıfırdan büyük olmalı");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ImportException.validation("amount en fazla 2 ondalık hane içerebilir");
        }
        PaymentMethod method = row.enumValue("method", PaymentMethod.class);
        if (method == null) {
            throw ImportException.validation("method zorunlu");
        }
        if (payments.existsByExternalRef(ref)) {
            return Result.duplicate("Bu işlem kimliğiyle ödeme zaten kayıtlı");
        }
        Long invoiceId = invoices.findIdByInvoiceNo(invoiceNo)
                .orElseThrow(() -> ImportException.notFound("Fatura bulunamadı: " + invoiceNo));
        return Result.done(payments.pay(invoiceId, amount, method, row.text("reference"), ref,
                row.timestamp("paid_at")).id());
    }

    private void validate(Object request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw ImportException.validation(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining("; ")));
        }
    }
}
