package com.ispbilling.web;

import com.ispbilling.plan.CreatePlanRequest;
import com.ispbilling.plan.PlanService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/plans")
public class PlanPageController extends PageSupport {

    private final PlanService plans;

    public PlanPageController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("plans", plans.all());
        return "plans";
    }

    @PostMapping
    String create(@Valid @ModelAttribute CreatePlanRequest form, BindingResult br, Model model,
                  RedirectAttributes ra) {
        if (br.hasErrors()) {
            addErrors(model, br);
            return list(model);
        }
        return attempt(ra, "Tarife eklendi: " + form.code().trim().toUpperCase(), "/plans", () -> plans.create(form));
    }

    @PostMapping("/{id}/active")
    String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes ra) {
        return attempt(ra, active ? "Tarife satışa açıldı" : "Tarife satışa kapatıldı", "/plans",
                () -> plans.setActive(id, active));
    }
}
