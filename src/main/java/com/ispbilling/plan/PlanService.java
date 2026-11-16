package com.ispbilling.plan;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PlanService {

    private final PlanRepository plans;

    public PlanService(PlanRepository plans) {
        this.plans = plans;
    }

    public Plan create(CreatePlanRequest r) {
        String code = r.code().trim().toUpperCase();
        if (plans.existsByCode(code)) {
            throw new BusinessException("Bu kodla bir tarife zaten var: " + code);
        }
        return plans.save(new Plan(code, r.name().trim(), r.monthlyFee(), r.oivRate(), r.vatRate()));
    }

    /** Tarifeyi satışa açar/kapatır. Mevcut abonelikler etkilenmez. */
    public Plan setActive(Long id, boolean active) {
        Plan plan = get(id);
        plan.setActive(active);
        return plan;
    }

    @Transactional(readOnly = true)
    public Plan get(Long id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Tarife", id));
    }

    @Transactional(readOnly = true)
    public List<Plan> all() {
        return plans.findAllByOrderByCode();
    }

    @Transactional(readOnly = true)
    public List<Plan> activePlans() {
        return plans.findByActiveTrueOrderByCode();
    }
}
