package com.ispbilling.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillRunRepository extends JpaRepository<BillRun, Long> {

    Optional<BillRun> findByBillCycleIdAndPeriodStart(Long billCycleId, LocalDate periodStart);

    @Query("select r from BillRun r join fetch r.billCycle order by r.periodStart desc, r.id desc")
    List<BillRun> findAllWithCycle();

    @Query("select r from BillRun r join fetch r.billCycle where r.id = :id")
    Optional<BillRun> findWithCycle(Long id);
}
