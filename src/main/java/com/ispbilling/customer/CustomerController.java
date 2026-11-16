package com.ispbilling.customer;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@Valid @RequestBody CreateCustomerRequest r) {
        return service.create(r);
    }

    @GetMapping
    public Page<Customer> list(@RequestParam(required = false) String q, Pageable pageable) {
        return service.search(q, pageable);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return service.get(id);
    }
}
