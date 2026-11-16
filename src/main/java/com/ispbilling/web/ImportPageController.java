package com.ispbilling.web;

import com.ispbilling.common.BusinessException;
import com.ispbilling.inbound.ImportService;
import com.ispbilling.inbound.ImportStatusService;
import com.ispbilling.inbound.InboundTable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ImportPageController {

    private final ImportService importService;
    private final ImportStatusService status;

    public ImportPageController(ImportService importService, ImportStatusService status) {
        this.importService = importService;
        this.status = status;
    }

    @GetMapping("/imports")
    String page(Model model) {
        model.addAttribute("tables", InboundTable.values());
        model.addAttribute("counts", status.counts());
        model.addAttribute("importErrors", status.recentErrors(50));
        return "imports";
    }

    @PostMapping("/imports/run")
    String run(RedirectAttributes ra) {
        try {
            ImportService.RunResult r = importService.runAll();
            if (r.total() == 0) {
                ra.addFlashAttribute("success", "İşlenecek yeni kayıt yok");
            } else if (r.errors() > 0) {
                ra.addFlashAttribute("error", "%d kayıt işlendi, %d kayıtta hata var".formatted(r.total(), r.errors()));
            } else {
                ra.addFlashAttribute("success", "%d kayıt işlendi".formatted(r.total()));
            }
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/imports";
    }

    @PostMapping("/imports/{table}/retry")
    String retry(@PathVariable String table, RedirectAttributes ra) {
        InboundTable t = InboundTable.fromSlug(table);
        int n = importService.retryErrors(t);
        ra.addFlashAttribute("success", "%s: %d hatalı kayıt yeniden kuyruğa alındı".formatted(t.label, n));
        return "redirect:/imports";
    }
}
