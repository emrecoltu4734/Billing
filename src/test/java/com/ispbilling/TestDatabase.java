package com.ispbilling;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Entegrasyon testleri için veritabanı temizliği. Faturalandırma ve import her abone/satır için ayrı
 * transaction commit ettiğinden testler geri almaya (rollback) güvenemez; her testten önce ve sonra
 * iş tabloları boşaltılır. Panel kullanıcıları (app_user) korunur.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static void reset(JdbcClient jdbc) {
        jdbc.sql("""
                truncate in_customer, in_subscription_event, in_payment,
                         notification, invoice_number_series, bill_run_error, payment, invoice_line, invoice, bill_run, subscription, customer, plan
                restart identity cascade
                """).update();
        jdbc.sql("delete from bill_cycle where code <> 'C01'").update();
        jdbc.sql("update bill_cycle set is_default = true, auto_run = false, active = true, cycle_day = 1, "
                 + "name = 'Ayın 1''i' where code = 'C01'").update();
    }
}
