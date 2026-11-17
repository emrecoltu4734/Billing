package com.ispbilling.customer;

import com.ispbilling.billing.BillCycleService;
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
    private final BillCycleService cycles;

    public CustomerService(CustomerRepository customers, BillCycleService cycles) {
        this.customers = customers;
        this.cycles = cycles;
    }

    public Customer create(CreateCustomerRequest r) {
        Customer c = new Customer(r.type(), r.name().trim(), r.taxId(),
                blankToNull(r.taxOffice()), blankToNull(r.phone()), blankToNull(r.email()),
                blankToNull(r.address()));
        c.assignBillCycle(cycles.resolve(r.billCycleCode()));
        return customers.saveAndFlush(c);
    }

    /**
     * Aboneyi başka bir fatura dönemine taşır. Daha önce faturalanan günler tekrar faturalanmaz;
     * yeni cycle'ın ilk faturası kaldığı günden başlar.
     */
    public Customer changeBillCycle(Long customerId, String cycleCode) {
        Customer c = get(customerId);
        c.assignBillCycle(cycles.getByCode(cycleCode));
        return c;
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
            if (StringUtils.hasText(r.billCycleCode())) {   // boşsa mevcut cycle korunur
                c.assignBillCycle(cycles.getByCode(r.billCycleCode()));
            }
            return c.getId();
        }
        Customer c = new Customer(r.type(), r.name().trim(), r.taxId(), blankToNull(r.taxOffice()),
                blankToNull(r.phone()), blankToNull(r.email()), blankToNull(r.address()));
        c.setExternalRef(externalRef);
        c.assignBillCycle(cycles.resolve(r.billCycleCode()));
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
