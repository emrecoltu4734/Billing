package com.ispbilling.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillCycleRepository extends JpaRepository<BillCycle, Long> {

    Optional<BillCycle> findByCode(String code);

    boolean existsByCode(String code);

    Optional<BillCycle> findByIsDefaultTrue();

    List<BillCycle> findAllByOrderByCycleDayAscCodeAsc();

    List<BillCycle> findByActiveTrueAndAutoRunTrue();
}
