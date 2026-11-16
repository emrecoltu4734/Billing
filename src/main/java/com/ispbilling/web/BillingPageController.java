package com.ispbilling.web;

import com.ispbilling.billing.BillRun;
import com.ispbilling.billing.BillRunRepository;
import com.ispbilling.billing.BillRunService;
import com.ispbilling.common.BusinessException;
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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Set;

@Controller
public class BillingPageController extends PageSupport {

    private final BillRunService billRunService;
    private final BillRunRepository billRuns;
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final Clock clock;
    private final Fmt fmt;

    public BillingPageController(BillRunService billRunService, BillRunRepository billRuns,
                                 InvoiceService invoices, PaymentService payments, Clock clock, Fmt fmt) {
        this.fmt = fmt;
        this.billRunService = billRunService;
        this.billRuns = billRuns;
        this.invoices = invoices;
        this.payments = payments;
        this.clock = clock;
    }

    // ---------- Fatura dönemleri ----------

    @GetMapping("/bill-runs")
    String billRuns(Model model) {
        model.addAttribute("billRuns", billRuns.findAllByOrderByPeriodStartDesc());
        // Varsayılan: geçen ay (ay başında geçen ayın faturası kesilir); o kesilmişse bu ay
        YearMonth previous = YearMonth.now(clock).minusMonths(1);
        YearMonth suggested = billRuns.existsByPeriodStart(previous.atDay(1)) ? previous.plusMonths(1) : previous;
        model.addAttribute("defaultPeriod", suggested.toString());
        return "bill-runs";
    }

    @PostMapping("/bill-runs")
    String runBilling(@RequestParam String period, RedirectAttributes ra) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            ra.addFlashAttribute("error", "Geçersiz dönem: " + period);
            return "redirect:/bill-runs";
        }
        try {
            BillRun run = billRunService.run(ym);
            ra.addFlashAttribute("success", "%s dönemi faturalandı: %d fatura".formatted(fmt.period(ym.atDay(1)), run.getInvoiceCount()));
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/bill-runs";
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
