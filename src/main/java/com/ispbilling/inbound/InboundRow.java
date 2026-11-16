package com.ispbilling.inbound;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** Bir giriş satırını tip güvenli okumak için ince sarmalayıcı. Boş metinler null kabul edilir. */
record InboundRow(Map<String, Object> values) {

    long id() {
        return ((Number) values.get("id")).longValue();
    }

    String text(String column) {
        Object v = values.get(column);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    String requiredText(String column) {
        String s = text(column);
        if (s == null) throw ImportException.validation(column + " zorunlu");
        return s;
    }

    LocalDate date(String column) {
        Object v = values.get(column);
        return switch (v) {
            case null -> null;
            case java.sql.Date d -> d.toLocalDate();
            case LocalDate d -> d;
            default -> LocalDate.parse(v.toString());
        };
    }

    LocalDate requiredDate(String column) {
        LocalDate d = date(column);
        if (d == null) throw ImportException.validation(column + " zorunlu");
        return d;
    }

    BigDecimal decimal(String column) {
        Object v = values.get(column);
        return v == null ? null : new BigDecimal(v.toString());
    }

    OffsetDateTime timestamp(String column) {
        Object v = values.get(column);
        return switch (v) {
            case null -> null;
            case Timestamp t -> t.toInstant().atOffset(ZoneOffset.UTC);
            case OffsetDateTime t -> t;
            default -> OffsetDateTime.parse(v.toString());
        };
    }

    <E extends Enum<E>> E enumValue(String column, Class<E> type) {
        String s = text(column);
        if (s == null) return null;
        try {
            return Enum.valueOf(type, s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ImportException.validation(column + " geçersiz: " + s);
        }
    }
}
