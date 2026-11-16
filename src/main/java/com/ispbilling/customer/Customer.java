package com.ispbilling.customer;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "customer_no", insertable = false, updatable = false)
    private String customerNo;

    @Enumerated(EnumType.STRING)
    private CustomerType type;

    private String name;
    private String taxId;
    private String taxOffice;
    private String phone;
    private String email;
    private String address;

    @Column(insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    protected Customer() {
    }

    public Customer(CustomerType type, String name, String taxId, String taxOffice,
                    String phone, String email, String address) {
        this.type = type;
        this.name = name;
        this.taxId = taxId;
        this.taxOffice = taxOffice;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getCustomerNo() { return customerNo; }
    public CustomerType getType() { return type; }
    public String getName() { return name; }
    public String getTaxId() { return taxId; }
    public String getTaxOffice() { return taxOffice; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
