package com.ispbilling.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Bir aboneliğin bir fatura dönemindeki ücretini hesaplar. Saf fonksiyondur, veritabanına dokunmaz.
 *
 * <p>Kurallar:
 * <ul>
 *   <li>Kıst (prorate): aylık ücret × (dönemde aktif gün / dönemin gün sayısı).</li>
 *   <li>ÖİV, net tutar üzerinden hesaplanır.</li>
 *   <li>KDV matrahına ÖİV dahildir: KDV = (net + ÖİV) × KDV oranı.</li>
 *   <li>Her ara adım 2 haneye HALF_UP yuvarlanır.</li>
 * </ul>
 * Vergi kuralları ve oranları canlıya çıkmadan önce bir mali müşavirle doğrulanmalıdır.
 */
public final class ChargeCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ChargeCalculator() {
    }

    public record Charge(int billedDays, int periodDays, BigDecimal net, BigDecimal oiv,
                         BigDecimal vat, BigDecimal total) {
        public boolean isEmpty() {
            return billedDays == 0;
        }
    }

    /**
     * @param subscriptionStart aboneliğin başlangıç günü (dahil)
     * @param subscriptionEnd   aboneliğin bitiş günü (dahil), açık uçlu ise null
     */
    public static Charge calculate(BigDecimal monthlyFee, BigDecimal oivRate, BigDecimal vatRate,
                                   LocalDate subscriptionStart, LocalDate subscriptionEnd,
                                   LocalDate periodStart, LocalDate periodEnd) {
        int periodDays = daysInclusive(periodStart, periodEnd);

        LocalDate from = max(subscriptionStart, periodStart);
        LocalDate to = subscriptionEnd == null ? periodEnd : min(subscriptionEnd, periodEnd);
        int billedDays = to.isBefore(from) ? 0 : daysInclusive(from, to);

        BigDecimal net = billedDays == periodDays
                ? scale(monthlyFee)
                : scale(monthlyFee.multiply(BigDecimal.valueOf(billedDays))
                        .divide(BigDecimal.valueOf(periodDays), 10, RoundingMode.HALF_UP));
        BigDecimal oiv = percent(net, oivRate);
        BigDecimal vat = percent(net.add(oiv), vatRate);
        return new Charge(billedDays, periodDays, net, oiv, vat, net.add(oiv).add(vat));
    }

    private static int daysInclusive(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    private static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return scale(base.multiply(rate).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
