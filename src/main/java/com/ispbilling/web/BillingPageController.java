package com.ispbilling.web;

import com.ispbilling.invoice.InvoiceService;
import com.ispbilling.invoice.InvoiceStatus;
import com.ispbilling.payment.PaymentMethod;
import com.ispbilling.payment.PaymentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

@Controller
/** Faturalar, tahsilat ve borçlular sayfaları. */
public class BillingPageController extends PageSupport {

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final Clock clock;
    private final Fmt fmt;

    public BillingPageController(InvoiceService invoices, PaymentService payments, Clock clock, Fmt fmt) {
        this.fmt = fmt;
        this.invoices = invoices;
        this.payments = payments;
        this.clock = clock;
    }

    // ---------- Faturalar ----------

    @GetMapping("/invoices")
    String invoices(@RequestParam(defaultValue = "open") String filter, @RequestParam(defaultValue = "0") int page,
                    Model model) {
        Set<InvoiceStatus> statuses = switch (filter) {
            case "paid" -> EnumSet.of(InvoiceStatus.PAID);
            case "all" -> null;
            default -> InvoiceService.OPEN_STATUSES;
        };
        model.addAttribute("filter", filter);
        model.addAttribute("page", invoices.search(statuses, PageRequest.of(Math.max(page, 0), 25,
                Sort.by(Sort.Direction.DESC, "issueDate", "id"))));
        return "invoices/list";
    }

    @GetMapping("/invoices/{id}")
    String invoice(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", invoices.get(id));
        model.addAttribute("payments", payments.listByInvoice(id));
        model.addAttribute("methods", PaymentMethod.values());
        return "invoices/detail";
    }

    @PostMapping("/invoices/{id}/payments")
    String pay(@PathVariable Long id, @RequestParam(required = false) BigDecimal amount,
               @RequestParam(required = false) PaymentMethod method, @RequestParam(required = false) String reference,
               RedirectAttributes ra) {
        String back = "/invoices/" + id;
        if (amount == null || amount.signum() <= 0 || method == null) {
            ra.addFlashAttribute("error", "Geçerli bir tutar ve ödeme yöntemi girilmeli");
            return "redirect:" + back;
        }
        if (amount.scale() > 2) {
            ra.addFlashAttribute("error", "Tutar en fazla 2 ondalık hane içerebilir");
            return "redirect:" + back;
        }
        return attempt(ra, "Tahsilat kaydedildi", back,
                () -> payments.pay(id, amount, method, reference == null || reference.isBlank() ? null : reference.trim()));
    }

    @GetMapping("/overdue")
    String overdue(Model model) {
        var list = invoices.overdue();
        model.addAttribute("invoices", list);
        model.addAttribute("total", list.stream().map(i -> i.openAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "overdue";
    }
}
