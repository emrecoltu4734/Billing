package com.ispbilling.customer;

import com.ispbilling.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customers;

    public CustomerController(CustomerRepository customers) {
        this.customers = customers;
    }

    public record CreateCustomerRequest(
            @NotNull CustomerType type,
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "\\d{10,11}", message = "TCKN 11, VKN 10 haneli olmalı") String taxId,
            String taxOffice,
            String phone,
            @Email String email,
            String address) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@Valid @RequestBody CreateCustomerRequest r) {
        return customers.saveAndFlush(new Customer(
                r.type(), r.name(), r.taxId(), r.taxOffice(), r.phone(), r.email(), r.address()));
    }

    @GetMapping
    public Page<Customer> list(Pageable pageable) {
        return customers.findAll(pageable);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Abone", id));
    }
}
