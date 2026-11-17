package com.ispbilling.web;

import com.ispbilling.billing.BillCycleService;
import com.ispbilling.customer.CreateCustomerRequest;
import com.ispbilling.customer.Customer;
import com.ispbilling.customer.CustomerService;
import com.ispbilling.invoice.InvoiceService;
import com.ispbilling.invoice.InvoiceView;
import com.ispbilling.plan.PlanService;
import com.ispbilling.subscription.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
public class CustomerPageController extends PageSupport {

    private final CustomerService customers;
    private final SubscriptionService subscriptions;
    private final InvoiceService invoices;
    private final PlanService plans;
    private final BillCycleService cycles;

    public CustomerPageController(CustomerService customers, SubscriptionService subscriptions,
                                  InvoiceService invoices, PlanService plans, BillCycleService cycles) {
        this.cycles = cycles;
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.plans = plans;
    }

    @GetMapping("/customers")
    String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                Model model) {
        model.addAttribute("q", q);
        model.addAttribute("page", customers.search(q, PageRequest.of(Math.max(page, 0), 25,
                Sort.by(Sort.Direction.DESC, "id"))));
        return "customers/list";
    }

    @GetMapping("/customers/new")
    String newForm(Model model) {
        model.addAttribute("cycles", cycles.all());
        return "customers/new";
    }

    @PostMapping("/customers")
    String create(@Valid @ModelAttribute CreateCustomerRequest form, BindingResult br, Model model,
                  RedirectAttributes ra) {
        if (br.hasErrors()) {
            addErrors(model, br);
            return newForm(model);
        }
        Customer c = customers.create(form);
        ra.addFlashAttribute("success", "Abone kaydedildi: " + c.getCustomerNo());
        return "redirect:/customers/" + c.getId();
    }

    @GetMapping("/customers/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customers.get(id));
        model.addAttribute("subscriptions", subscriptions.listByCustomer(id));
        List<InvoiceView> customerInvoices = invoices.listByCustomer(id);
        model.addAttribute("invoices", customerInvoices);
        model.addAttribute("openBalance", customerInvoices.stream()
                .filter(i -> InvoiceService.OPEN_STATUSES.contains(i.status()))
                .map(InvoiceView::openAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("plans", plans.activePlans());
        model.addAttribute("cycles", cycles.all());
        return "customers/detail";
    }

    @PostMapping("/customers/{id}/bill-cycle")
    String changeBillCycle(@PathVariable Long id, @RequestParam String billCycleCode, RedirectAttributes ra) {
        return attempt(ra, "Fatura dönemi değiştirildi. Daha önce faturalanan günler tekrar faturalanmaz.",
                "/customers/" + id, () -> customers.changeBillCycle(id, billCycleCode));
    }

    @PostMapping("/customers/{id}/subscriptions")
    String addSubscription(@PathVariable Long id, @RequestParam(required = false) Long planId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                           RedirectAttributes ra) {
        String back = "/customers/" + id;
        if (planId == null || startDate == null) {
            ra.addFlashAttribute("error", "Tarife ve başlangıç tarihi seçilmeli");
            return "redirect:" + back;
        }
        return attempt(ra, "Abonelik başlatıldı", back, () -> subscriptions.create(id, planId, startDate));
    }

    @PostMapping("/subscriptions/{id}/suspend")
    String suspend(@PathVariable Long id, RedirectAttributes ra) {
        Long customerId = subscriptions.get(id).customerId();
        return attempt(ra, "Abonelik kısıtlandı", "/customers/" + customerId, () -> subscriptions.suspend(id));
    }

    @PostMapping("/subscriptions/{id}/resume")
    String resume(@PathVariable Long id, RedirectAttributes ra) {
        Long customerId = subscriptions.get(id).customerId();
        return attempt(ra, "Abonelik yeniden açıldı", "/customers/" + customerId, () -> subscriptions.resume(id));
    }

    @PostMapping("/subscriptions/{id}/terminate")
    String terminate(@PathVariable Long id,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                     RedirectAttributes ra) {
        Long customerId = subscriptions.get(id).customerId();
        String back = "/customers/" + customerId;
        if (endDate == null) {
            ra.addFlashAttribute("error", "Bitiş tarihi seçilmeli");
            return "redirect:" + back;
        }
        return attempt(ra, "Abonelik sonlandırıldı", back, () -> subscriptions.terminate(id, endDate));
    }
}
