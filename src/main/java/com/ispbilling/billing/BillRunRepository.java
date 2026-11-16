package com.ispbilling.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BillRunRepository extends JpaRepository<BillRun, Long> {
    boolean existsByPeriodStart(LocalDate periodStart);

    List<BillRun> findAllByOrderByPeriodStartDesc();
}
