package com.ispbilling.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("select i from Invoice i join fetch i.customer where i.customer.id = :customerId order by i.periodStart desc, i.id desc")
    List<Invoice> findByCustomerId(Long customerId);

    /** Vadesi geçmiş ve tamamen ödenmemiş faturalar (borçlu listesi). */
    @Query("""
            select i from Invoice i join fetch i.customer
             where i.status in :statuses and i.dueDate < :today
             order by i.dueDate, i.id
            """)
    List<Invoice> findOverdue(LocalDate today, Collection<InvoiceStatus> statuses);

    java.util.Optional<Invoice> findByInvoiceNo(String invoiceNo);

    @EntityGraph(attributePaths = "customer")
    Page<Invoice> findByStatusIn(Collection<InvoiceStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    @Query("select i from Invoice i")
    Page<Invoice> findAllWithCustomer(Pageable pageable);
}
