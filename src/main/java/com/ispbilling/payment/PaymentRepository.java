package com.ispbilling.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.invoice where p.invoice.id = :invoiceId order by p.paidAt, p.id")
    List<Payment> findByInvoiceId(Long invoiceId);
}
