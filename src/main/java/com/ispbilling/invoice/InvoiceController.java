package com.ispbilling.invoice;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;
    private final InvoiceDeliveryService delivery;

    public InvoiceController(InvoiceService service, InvoiceDeliveryService delivery) {
        this.service = service;
        this.delivery = delivery;
    }

    /** Faturanın e-posta/SMS gönderim durumu. */
    @GetMapping("/{id}/notifications")
    public List<com.ispbilling.notification.NotificationView> notifications(@PathVariable Long id) {
        return delivery.list(id);
    }

    /** Faturayı aboneye yeniden gönderir. */
    @PostMapping("/{id}/notifications/resend")
    public java.util.Map<String, Integer> resend(@PathVariable Long id) {
        return java.util.Map.of("queued", delivery.resend(id));
    }

    @GetMapping("/{id}")
    public InvoiceView get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<InvoiceView> listByCustomer(@RequestParam Long customerId) {
        return service.listByCustomer(customerId);
    }

    /** Borçlu listesi: vadesi geçmiş, ödenmemiş faturalar. Hatırlatma ve kısıtlama buradan beslenecek. */
    @GetMapping("/overdue")
    public List<InvoiceView> overdue() {
        return service.overdue();
    }
}
