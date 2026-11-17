package com.ispbilling.invoice;

import com.ispbilling.common.BillingProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * GİB e-Arşiv biçiminde 16 karakterlik fatura numarası üretir: ÖNEK(3) + YIL(4) + SIRA(9).
 *
 * <p>Sıra, yıl başına {@code invoice_number_series} satırında tutulur ve satır kilidiyle artırılır.
 * Çağıran transaction geri alınırsa numara da geri alınır; böylece numaralar <b>boşluksuzdur</b> ve
 * her yıl 1'den başlar. Aynı yılın numaraları sırayla verildiği için eşzamanlı kesinleştirmeler kilitte
 * sıraya girer.
 */
@Component
public class InvoiceNumberGenerator {

    private final JdbcClient jdbc;
    private final String prefix;

    public InvoiceNumberGenerator(JdbcClient jdbc, BillingProperties props) {
        if (props.invoicePrefix() == null || props.invoicePrefix().length() != 3) {
            throw new IllegalStateException("billing.invoice-prefix tam 3 karakter olmalı");
        }
        this.jdbc = jdbc;
        this.prefix = props.invoicePrefix().toUpperCase();
    }

    /** Açık bir transaction içinde çağrılmalıdır. */
    public String next(LocalDate issueDate) {
        long seq = jdbc.sql("""
                        insert into invoice_number_series (year, last_value) values (:year, 1)
                        on conflict (year) do update set last_value = invoice_number_series.last_value + 1
                        returning last_value
                        """)
                .param("year", issueDate.getYear()).query(Long.class).single();
        return "%s%d%09d".formatted(prefix, issueDate.getYear(), seq);
    }
}
