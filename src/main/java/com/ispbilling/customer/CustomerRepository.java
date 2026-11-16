package com.ispbilling.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    java.util.Optional<Customer> findByExternalRef(String externalRef);

    /** Ad, abone no veya TCKN/VKN içinde arar. */
    @Query("""
            select c from Customer c
             where lower(c.name) like lower(concat('%', :q, '%'))
                or lower(c.customerNo) like lower(concat('%', :q, '%'))
                or c.taxId like concat('%', :q, '%')
            """)
    Page<Customer> search(String q, Pageable pageable);
}
