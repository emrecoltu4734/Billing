package com.ispbilling.web;

import com.ispbilling.billing.BillRunService;
import com.ispbilling.dashboard.DashboardService;
import com.ispbilling.invoice.InvoiceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DashboardService dashboard;
    private final InvoiceService invoices;
    private final BillRunService billRuns;

    public HomeController(DashboardService dashboard, InvoiceService invoices, BillRunService billRuns) {
        this.dashboard = dashboard;
        this.invoices = invoices;
        this.billRuns = billRuns;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("summary", dashboard.summary());
        model.addAttribute("overdue", invoices.overdue().stream().limit(8).toList());
        model.addAttribute("billRuns", billRuns.list().stream().limit(5).toList());
        return "dashboard";
    }
}
