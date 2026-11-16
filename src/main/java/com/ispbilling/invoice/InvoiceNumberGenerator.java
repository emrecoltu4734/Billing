package com.ispbilling.invoice;

import com.ispbilling.common.BillingProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * GİB e-Arşiv biçiminde 16 karakterlik fatura numarası üretir: ÖNEK(3) + YIL(4) + SIRA(9).
 *
 * <p>Not: Şu an sıra yıllar arasında sıfırlanmıyor. e-Fatura entegrasyonu eklendiğinde numarayı
 * entegratör verecek veya sıra yıl bazında tutulacak.
 */
@Component
public class InvoiceNumberGenerator {

    private final JdbcTemplate jdbc;
    private final String prefix;

    public InvoiceNumberGenerator(JdbcTemplate jdbc, BillingProperties props) {
        if (props.invoicePrefix() == null || props.invoicePrefix().length() != 3) {
            throw new IllegalStateException("billing.invoice-prefix tam 3 karakter olmalı");
        }
        this.jdbc = jdbc;
        this.prefix = props.invoicePrefix().toUpperCase();
    }

    public String next(LocalDate issueDate) {
        Long seq = jdbc.queryForObject("select nextval('invoice_no_seq')", Long.class);
        return "%s%d%09d".formatted(prefix, issueDate.getYear(), seq);
    }
}
