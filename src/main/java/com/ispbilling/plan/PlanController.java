package com.ispbilling.plan;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanRepository plans;

    public PlanController(PlanRepository plans) {
        this.plans = plans;
    }

    public record CreatePlanRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal monthlyFee,
            @NotNull @DecimalMin("0") BigDecimal oivRate,
            @NotNull @DecimalMin("0") BigDecimal vatRate) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Plan create(@Valid @RequestBody CreatePlanRequest r) {
        if (plans.existsByCode(r.code())) {
            throw new BusinessException("Bu kodla bir tarife zaten var: " + r.code());
        }
        return plans.save(new Plan(r.code(), r.name(), r.monthlyFee(), r.oivRate(), r.vatRate()));
    }

    @GetMapping
    public List<Plan> list() {
        return plans.findAll();
    }

    @GetMapping("/{id}")
    public Plan get(@PathVariable Long id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Tarife", id));
    }
}
