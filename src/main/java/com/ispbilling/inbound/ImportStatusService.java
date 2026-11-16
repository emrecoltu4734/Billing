package com.ispbilling.inbound;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Giriş tablolarının durum özeti ve hata listesi (panel ve API için). */
@Service
public class ImportStatusService {

    public record Counts(long pending, long done, long error, long duplicate) {
    }

    public record ErrorRow(InboundTable table, long id, String externalRef, String errorCode, String errorMessage,
                           OffsetDateTime createdAt) {
        public String tableLabel() {
            return table.label;
        }
    }

    private final JdbcClient jdbc;

    public ImportStatusService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<InboundTable, Counts> counts() {
        Map<InboundTable, Counts> result = new EnumMap<>(InboundTable.class);
        for (InboundTable t : InboundTable.values()) {
            result.put(t, jdbc.sql("""
                            select count(*) filter (where status = 'NEW')       as pending,
                                   count(*) filter (where status = 'DONE')      as done,
                                   count(*) filter (where status = 'ERROR')     as error,
                                   count(*) filter (where status = 'DUPLICATE') as duplicate
                              from %s
                            """.formatted(t.tableName))
                    .query((rs, n) -> new Counts(rs.getLong("pending"), rs.getLong("done"), rs.getLong("error"),
                            rs.getLong("duplicate")))
                    .single());
        }
        return result;
    }

    /** Tüm tablolardaki en yeni hatalı satırlar. */
    public List<ErrorRow> recentErrors(int limit) {
        List<ErrorRow> all = new ArrayList<>();
        for (InboundTable t : InboundTable.values()) {
            all.addAll(jdbc.sql("select id, external_ref, error_code, error_message, created_at from " + t.tableName
                            + " where status = 'ERROR' order by id desc limit :n")
                    .param("n", limit)
                    .query((rs, n) -> new ErrorRow(t, rs.getLong("id"), rs.getString("external_ref"),
                            rs.getString("error_code"), rs.getString("error_message"),
                            rs.getObject("created_at", OffsetDateTime.class)))
                    .list());
        }
        return all.stream().sorted(Comparator.comparing(ErrorRow::createdAt).reversed()).limit(limit).toList();
    }
}
