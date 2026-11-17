package com.ispbilling.billing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillCycleTest {

    private final BillCycle day1 = new BillCycle("C01", "Ayın 1'i", 1, false);
    private final BillCycle day15 = new BillCycle("C15", "Ayın 15'i", 15, false);

    @Test
    void day1IsCalendarMonth() {
        YearMonth nov = YearMonth.of(2026, 11);
        assertThat(day1.periodStart(nov)).isEqualTo("2026-11-01");
        assertThat(day1.periodEnd(nov)).isEqualTo("2026-11-30");
        assertThat(day1.runDate(nov)).isEqualTo("2026-12-01");
    }

    @Test
    void day15SpansTwoMonths() {
        YearMonth nov = YearMonth.of(2026, 11);
        assertThat(day15.periodStart(nov)).isEqualTo("2026-11-15");
        assertThat(day15.periodEnd(nov)).isEqualTo("2026-12-14");
        assertThat(day15.runDate(nov)).isEqualTo("2026-12-15");
        // Ocak dönemi Şubat'a uzanır
        assertThat(day15.periodEnd(YearMonth.of(2027, 1))).isEqualTo("2027-02-14");
    }

    @Test
    void latestEndedPeriod() {
        assertThat(day15.latestEndedPeriod(LocalDate.of(2026, 12, 15))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(day15.latestEndedPeriod(LocalDate.of(2026, 12, 14))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(day1.latestEndedPeriod(LocalDate.of(2026, 12, 1))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(day1.latestEndedPeriod(LocalDate.of(2026, 12, 31))).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void nextRunDate() {
        assertThat(day15.nextRunDate(LocalDate.of(2026, 11, 15))).isEqualTo("2026-11-15");
        assertThat(day15.nextRunDate(LocalDate.of(2026, 11, 16))).isEqualTo("2026-12-15");
    }

    @Test
    void dayMustBe1To28() {
        assertThatThrownBy(() -> new BillCycle("X", "X", 29, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillCycle("X", "X", 0, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
