package com.ispbilling.web;

import com.ispbilling.billing.ChargeCalculator;
import com.ispbilling.customer.CustomerType;
import com.ispbilling.invoice.InvoiceStatus;
import com.ispbilling.payment.PaymentMethod;
import com.ispbilling.plan.Plan;
import com.ispbilling.subscription.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Şablonlarda ${@fmt.money(x)} şeklinde kullanılan Türkçe biçimlendirme yardımcıları. */
@Component("fmt")
public class Fmt {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final Clock clock;

    public Fmt(Clock clock) {
        this.clock = clock;
    }

    public String money(BigDecimal v) {
        if (v == null) return "";
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(TR));
        return f.format(v) + " ₺";
    }

    /** Form alanları için: 1234.5 → "1234,50" */
    public String amountInput(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public String percent(BigDecimal v) {
        return v == null ? "" : "%" + v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    public String dateTime(OffsetDateTime t) {
        return t == null ? "" : t.atZoneSameInstant(clock.getZone()).format(DATE_TIME);
    }

    /** 2026-11-01 → "Kasım 2026" */
    public String period(LocalDate d) {
        if (d == null) return "";
        String month = d.getMonth().getDisplayName(TextStyle.FULL, TR);
        return month + " " + d.getYear();
    }

    /** Tarifenin vergiler dahil tam aylık tutarı (fatura motoruyla aynı hesap). */
    public BigDecimal grossFee(Plan p) {
        LocalDate start = LocalDate.of(2000, 1, 1);
        return ChargeCalculator.calculate(p.getMonthlyFee(), p.getOivRate(), p.getVatRate(), start, null,
                start, start.withDayOfMonth(31)).total();
    }

    /** Verilen tarihten bugüne geçen gün sayısı. */
    public long daysSince(LocalDate d) {
        return d == null ? 0 : ChronoUnit.DAYS.between(d, today());
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public String label(Enum<?> e) {
        if (e == null) return "";
        return switch (e) {
            case InvoiceStatus s -> switch (s) {
                case ISSUED -> "Ödenmedi";
                case PARTIALLY_PAID -> "Kısmi ödendi";
                case PAID -> "Ödendi";
                case CANCELLED -> "İptal";
            };
            case SubscriptionStatus s -> switch (s) {
                case ACTIVE -> "Aktif";
                case SUSPENDED -> "Kısıtlı";
                case TERMINATED -> "Sonlandırıldı";
            };
            case PaymentMethod m -> switch (m) {
                case CASH -> "Nakit";
                case BANK_TRANSFER -> "Havale/EFT";
                case CREDIT_CARD -> "Kredi kartı";
                case ONLINE -> "Online ödeme";
            };
            case CustomerType t -> switch (t) {
                case INDIVIDUAL -> "Bireysel";
                case CORPORATE -> "Kurumsal";
            };
            default -> e.name();
        };
    }

    /** Durum rozetinin rengi. */
    public String tone(Enum<?> e) {
        if (e == null) return "muted";
        return switch (e.name()) {
            case "PAID", "ACTIVE", "COMPLETED" -> "green";
            case "PARTIALLY_PAID", "SUSPENDED", "RUNNING" -> "amber";
            case "ISSUED" -> "blue";
            default -> "muted";
        };
    }
}
