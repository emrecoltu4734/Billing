package com.ispbilling.billing;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fatura dönemi (bill cycle). cycleDay = D olan bir cycle'da {@code YearMonth} M dönemi
 * [M ayının D'si, M+1 ayının D-1'i] aralığıdır ve dönem bitince (M+1 ayının D'sinde) faturalanır.
 * D = 1 iken dönem takvim ayıdır.
 */
@Entity
@Table(name = "bill_cycle")
public class BillCycle {

    public static final int MAX_DAY = 28;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    private int cycleDay;
    private boolean isDefault;
    private boolean autoRun;
    private boolean active = true;

    protected BillCycle() {
    }

    public BillCycle(String code, String name, int cycleDay, boolean autoRun) {
        if (cycleDay < 1 || cycleDay > MAX_DAY) {
            throw new IllegalArgumentException("Fatura günü 1-" + MAX_DAY + " arasında olmalı");
        }
        this.code = code;
        this.name = name;
        this.cycleDay = cycleDay;
        this.autoRun = autoRun;
    }

    public LocalDate periodStart(YearMonth period) {
        return period.atDay(cycleDay);
    }

    public LocalDate periodEnd(YearMonth period) {
        return period.plusMonths(1).atDay(cycleDay).minusDays(1);
    }

    /** Dönemin faturalanacağı gün (dönem bitiminin ertesi günü). */
    public LocalDate runDate(YearMonth period) {
        return period.plusMonths(1).atDay(cycleDay);
    }

    /** Verilen günde sona ermiş en son dönem. */
    public YearMonth latestEndedPeriod(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        return today.getDayOfMonth() >= cycleDay ? month.minusMonths(1) : month.minusMonths(2);
    }

    /** Bugün veya sonrasındaki ilk fatura günü. */
    public LocalDate nextRunDate(LocalDate today) {
        LocalDate thisMonth = YearMonth.from(today).atDay(cycleDay);
        return thisMonth.isBefore(today) ? thisMonth.plusMonths(1) : thisMonth;
    }

    public boolean isRunDay(LocalDate date) {
        return date.getDayOfMonth() == cycleDay;
    }

    void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    void setAutoRun(boolean autoRun) {
        this.autoRun = autoRun;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getCycleDay() { return cycleDay; }
    public boolean isDefault() { return isDefault; }
    public boolean isAutoRun() { return autoRun; }
    public boolean isActive() { return active; }
}
