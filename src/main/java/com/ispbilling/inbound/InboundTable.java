package com.ispbilling.inbound;

import com.ispbilling.common.NotFoundException;

import java.util.Arrays;

/** Giriş tabloları. SQL'e yalnızca buradaki sabit tablo adları girer, kullanıcı girdisi girmez. */
public enum InboundTable {
    CUSTOMER("in_customer", "customers", "Aboneler"),
    SUBSCRIPTION_EVENT("in_subscription_event", "subscription-events", "Abonelik olayları"),
    PAYMENT("in_payment", "payments", "Tahsilatlar");

    /** Veritabanı tablo adı */
    public final String tableName;
    /** API ve panel adreslerinde kullanılan ad */
    public final String slug;
    /** Ekranda görünen ad */
    public final String label;

    InboundTable(String tableName, String slug, String label) {
        this.tableName = tableName;
        this.slug = slug;
        this.label = label;
    }

    public static InboundTable fromSlug(String slug) {
        return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst()
                .orElseThrow(() -> new NotFoundException("Giriş tablosu", slug));
    }
}
