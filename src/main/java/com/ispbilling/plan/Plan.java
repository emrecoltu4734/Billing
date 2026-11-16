package com.ispbilling.plan;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Tarife / paket. Ücret vergiler hariç aylık tutardır. */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private BigDecimal monthlyFee;
    /** Özel İletişim Vergisi oranı (%). */
    private BigDecimal oivRate;
    /** KDV oranı (%). */
    private BigDecimal vatRate;
    private boolean active = true;

    protected Plan() {
    }

    public Plan(String code, String name, BigDecimal monthlyFee, BigDecimal oivRate, BigDecimal vatRate) {
        this.code = code;
        this.name = name;
        this.monthlyFee = monthlyFee;
        this.oivRate = oivRate;
        this.vatRate = vatRate;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public BigDecimal getOivRate() { return oivRate; }
    public BigDecimal getVatRate() { return vatRate; }
    public boolean isActive() { return active; }
}
