package com.ispbilling.billing;

import com.ispbilling.billing.ChargeCalculator.Charge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeCalculatorTest {

    private static final BigDecimal FEE = new BigDecimal("300.00");
    private static final BigDecimal OIV = new BigDecimal("10");
    private static final BigDecimal VAT = new BigDecimal("20");
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    private static Charge calc(LocalDate start, LocalDate end) {
        return ChargeCalculator.calculate(FEE, OIV, VAT, start, end, NOV_1, NOV_30);
    }

    @Test
    void fullMonth_chargesFullFeeWithTaxes() {
        Charge c = calc(LocalDate.of(2026, 1, 15), null);

        assertThat(c.billedDays()).isEqualTo(30);
        assertThat(c.net()).isEqualByComparingTo("300.00");
        assertThat(c.oiv()).isEqualByComparingTo("30.00");   // 300 × %10
        assertThat(c.vat()).isEqualByComparingTo("66.00");   // (300 + 30) × %20, ÖİV KDV matrahına dahil
        assertThat(c.total()).isEqualByComparingTo("396.00");
    }

    @Test
    void startsMidMonth_isProrated() {
        Charge c = calc(LocalDate.of(2026, 11, 16), null);  // 16-30 Kasım = 15 gün

        assertThat(c.billedDays()).isEqualTo(15);
        assertThat(c.net()).isEqualByComparingTo("150.00");
        assertThat(c.total()).isEqualByComparingTo("198.00");
    }

    @Test
    void terminatedMidMonth_endDateIsInclusive() {
        Charge c = calc(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 10));  // 1-10 Kasım = 10 gün

        assertThat(c.billedDays()).isEqualTo(10);
        assertThat(c.net()).isEqualByComparingTo("100.00");
    }

    @Test
    void startsAndEndsInsidePeriod() {
        Charge c = calc(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7));  // 3 gün

        assertThat(c.billedDays()).isEqualTo(3);
        assertThat(c.net()).isEqualByComparingTo("30.00");
    }

    @Test
    void outsidePeriod_isEmpty() {
        Charge c = calc(LocalDate.of(2026, 12, 1), null);

        assertThat(c.isEmpty()).isTrue();
        assertThat(c.total()).isEqualByComparingTo("0");
    }

    @Test
    void roundsHalfUpToKurus() {
        // 99,99 × 7/30 = 23,331 → 23,33 ; ÖİV 2,333 → 2,33 ; KDV (25,66 × %20) = 5,132 → 5,13
        Charge c = ChargeCalculator.calculate(new BigDecimal("99.99"), OIV, VAT,
                LocalDate.of(2026, 11, 24), null, NOV_1, NOV_30);

        assertThat(c.billedDays()).isEqualTo(7);
        assertThat(c.net()).isEqualByComparingTo("23.33");
        assertThat(c.oiv()).isEqualByComparingTo("2.33");
        assertThat(c.vat()).isEqualByComparingTo("5.13");
        assertThat(c.total()).isEqualByComparingTo("30.79");
    }

    @Test
    void february_usesActualDayCount() {
        Charge c = ChargeCalculator.calculate(FEE, OIV, VAT, LocalDate.of(2027, 2, 15), null,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28));

        assertThat(c.periodDays()).isEqualTo(28);
        assertThat(c.billedDays()).isEqualTo(14);
        assertThat(c.net()).isEqualByComparingTo("150.00");
    }
}
