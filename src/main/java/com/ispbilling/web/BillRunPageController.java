package com.ispbilling.web;

import com.ispbilling.billing.BillCycleService;
import com.ispbilling.billing.BillRunService;
import com.ispbilling.billing.BillRunView;
import com.ispbilling.billing.CreateBillCycleRequest;
import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Fatura dönemleri (bill cycle) ve faturalandırma çalıştırmaları. */
@Controller
public class BillRunPageController extends PageSupport {

    private final BillCycleService cycles;
    private final BillRunService billRuns;
    private final com.ispbilling.notification.NotificationService notifications;

    public BillRunPageController(BillCycleService cycles, BillRunService billRuns,
                                 com.ispbilling.notification.NotificationService notifications) {
        this.notifications = notifications;
        this.cycles = cycles;
        this.billRuns = billRuns;
    }

    @GetMapping("/bill-runs")
    String page(Model model) {
        model.addAttribute("cycles", cycles.all());
        model.addAttribute("billRuns", billRuns.list());
        return "bill-runs";
    }

    @PostMapping("/bill-runs")
    String run(@RequestParam(required = false) String cycleCode, @RequestParam String period, RedirectAttributes ra) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            ra.addFlashAttribute("error", "Geçersiz dönem: " + period);
            return "redirect:/bill-runs";
        }
        try {
            BillRunView run = billRuns.run(cycleCode, ym);
            if (run.errorCount() > 0) {
                ra.addFlashAttribute("error", "%d fatura kesildi, %d abone faturalanamadı. Ayrıntılar aşağıda."
                        .formatted(run.invoiceCount(), run.errorCount()));
                return "redirect:/bill-runs/" + run.id();
            }
            ra.addFlashAttribute("success", "%s · %s dönemi faturalandı: %d fatura"
                    .formatted(run.cycleName(), Fmt.periodName(ym), run.invoiceCount()));
        } catch (BusinessException | NotFoundException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/bill-runs";
    }

    @GetMapping("/bill-runs/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("run", billRuns.get(id));
        model.addAttribute("delivery", notifications.summaryForRun(id));
        return "bill-run-detail";
    }

    /** Hatalarla biten çalıştırmada kalan aboneleri yeniden dener. */
    @PostMapping("/bill-runs/{id}/resume")
    String resume(@PathVariable Long id, RedirectAttributes ra) {
        BillRunView before = billRuns.get(id);
        try {
            BillRunView run = billRuns.run(before.cycleCode(), before.period());
            if (run.errorCount() > 0) {
                ra.addFlashAttribute("error", "Hâlâ faturalanamayan %d abone var".formatted(run.errorCount()));
            } else {
                ra.addFlashAttribute("success", "Kalan aboneler faturalandı. Toplam %d fatura.".formatted(run.invoiceCount()));
            }
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/bill-runs/" + id;
    }

    // ---------- Cycle yönetimi ----------

    @PostMapping("/bill-cycles")
    String createCycle(@Valid @ModelAttribute CreateBillCycleRequest form, BindingResult br, Model model,
                       RedirectAttributes ra) {
        if (br.hasErrors()) {
            addErrors(model, br);
            return page(model);
        }
        return attempt(ra, "Fatura dönemi eklendi: " + form.code().trim().toUpperCase(), "/bill-runs",
                () -> cycles.create(form));
    }

    @PostMapping("/bill-cycles/{code}/default")
    String makeDefault(@PathVariable String code, RedirectAttributes ra) {
        return attempt(ra, "Yeni aboneler artık bu döneme atanacak: " + code, "/bill-runs", () -> cycles.makeDefault(code));
    }

    @PostMapping("/bill-cycles/{code}/auto-run")
    String setAutoRun(@PathVariable String code, @RequestParam boolean enabled, RedirectAttributes ra) {
        return attempt(ra, enabled ? code + ": fatura gününde otomatik faturalanacak" : code + ": otomatik faturalama kapatıldı",
                "/bill-runs", () -> cycles.setAutoRun(code, enabled));
    }
}
