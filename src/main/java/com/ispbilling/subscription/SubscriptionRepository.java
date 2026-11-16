package com.ispbilling.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** Verilen dönemle en az bir günü çakışan tüm abonelikler (tarife ve abone ile birlikte). */
    @Query("""
            select s from Subscription s
              join fetch s.customer
              join fetch s.plan
             where s.startDate <= :periodEnd
               and (s.endDate is null or s.endDate >= :periodStart)
             order by s.customer.id, s.id
            """)
    List<Subscription> findBillable(LocalDate periodStart, LocalDate periodEnd);

    @Query("select s from Subscription s join fetch s.plan where s.customer.id = :customerId order by s.id")
    List<Subscription> findByCustomerId(Long customerId);
}
