package com.ispbilling.customer;

import com.ispbilling.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public Customer create(CreateCustomerRequest r) {
        return customers.saveAndFlush(new Customer(r.type(), r.name().trim(), r.taxId(),
                blankToNull(r.taxOffice()), blankToNull(r.phone()), blankToNull(r.email()),
                blankToNull(r.address())));
    }

    /**
     * Müşteri sistemindeki kimliğe göre aboneyi oluşturur veya günceller.
     * Doğrulama çağıran tarafta yapılmış olmalıdır.
     *
     * @return core abone id'si
     */
    public Long upsertByExternalRef(String externalRef, CreateCustomerRequest r) {
        var existing = customers.findByExternalRef(externalRef);
        if (existing.isPresent()) {
            Customer c = existing.get();
            c.update(r.type(), r.name().trim(), r.taxId(), blankToNull(r.taxOffice()), blankToNull(r.phone()),
                    blankToNull(r.email()), blankToNull(r.address()));
            return c.getId();
        }
        Customer c = new Customer(r.type(), r.name().trim(), r.taxId(), blankToNull(r.taxOffice()),
                blankToNull(r.phone()), blankToNull(r.email()), blankToNull(r.address()));
        c.setExternalRef(externalRef);
        return customers.saveAndFlush(c).getId();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Long> findIdByExternalRef(String externalRef) {
        return customers.findByExternalRef(externalRef).map(Customer::getId);
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Abone", id));
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String q, Pageable pageable) {
        return StringUtils.hasText(q) ? customers.search(q.trim(), pageable) : customers.findAll(pageable);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
