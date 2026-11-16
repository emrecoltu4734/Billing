package com.ispbilling.invoice;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
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
