package com.ispbilling.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    boolean existsByCode(String code);

    List<Plan> findAllByOrderByCode();

    List<Plan> findByActiveTrueOrderByCode();
}
