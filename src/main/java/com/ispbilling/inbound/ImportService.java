package com.ispbilling.inbound;

import com.ispbilling.common.BusinessException;
import com.ispbilling.common.NotFoundException;
import com.ispbilling.payment.DuplicatePaymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Giriş tablolarındaki NEW satırları işleyen import batch'i.
 *
 * <ul>
 *   <li>Tablolar bağımlılık sırasıyla işlenir: abone → abonelik olayı → tahsilat.</li>
 *   <li>Her satır kendi transaction'ındadır: core değişikliği ve satırın DONE olması birlikte commit
 *       edilir. Hatalı satır geri alınır, ERROR olarak işaretlenir ve diğer satırları durdurmaz.</li>
 *   <li>Satır {@code FOR UPDATE SKIP LOCKED} ile kilitlenir; aynı satır iki kez işlenmez.</li>
 * </ul>
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final int CHUNK_SIZE = 500;

    public record TableResult(int done, int error, int duplicate) {
        TableResult add(String status) {
            return switch (status) {
                case "DONE" -> new TableResult(done + 1, error, duplicate);
                case "DUPLICATE" -> new TableResult(done, error, duplicate + 1);
                default -> new TableResult(done, error + 1, duplicate);
            };
        }

        public int total() {
            return done + error + duplicate;
        }
    }

    public record RunResult(Map<InboundTable, TableResult> tables) {
        public int total() {
            return tables.values().stream().mapToInt(TableResult::total).sum();
        }

        public int errors() {
            return tables.values().stream().mapToInt(TableResult::error).sum();
        }
    }

    private final JdbcClient jdbc;
    private final InboundRowHandlers handlers;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ImportService(JdbcClient jdbc, InboundRowHandlers handlers, PlatformTransactionManager txManager,
                         Clock clock) {
        this.jdbc = jdbc;
        this.handlers = handlers;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Tüm giriş tablolarını işler. Aynı anda yalnızca bir çalıştırma olabilir. */
    public RunResult runAll() {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException("Veri aktarımı zaten çalışıyor");
        }
        try {
            Map<InboundTable, TableResult> results = new EnumMap<>(InboundTable.class);
            for (InboundTable table : InboundTable.values()) {
                results.put(table, processTable(table));
            }
            RunResult result = new RunResult(results);
            if (result.total() > 0) {
                log.info("Veri aktarımı: {} satır işlendi, {} hata", result.total(), result.errors());
            }
            return result;
        } finally {
            running.set(false);
        }
    }

    /** Bir tablodaki ERROR satırları yeniden işlenmek üzere NEW yapar. */
    public int retryErrors(InboundTable table) {
        return jdbc.sql("update " + table.tableName + " set status = 'NEW', error_code = null, error_message = null, "
                        + "processed_at = null where status = 'ERROR'")
                .update();
    }

    private TableResult processTable(InboundTable table) {
        TableResult result = new TableResult(0, 0, 0);
        Set<Long> seen = new HashSet<>();
        while (true) {
            List<Long> ids = jdbc.sql("select id from " + table.tableName + " where status = 'NEW' order by id limit :n")
                    .param("n", CHUNK_SIZE).query(Long.class).list();
            ids = ids.stream().filter(seen::add).toList();
            if (ids.isEmpty()) {
                return result;   // bitti veya kalanlar başka bir işlem tarafından kilitli
            }
            for (Long id : ids) {
                String status = processRow(table, id);
                if (status != null) {
                    result = result.add(status);
                }
            }
        }
    }

    /** @return satırın yeni durumu; satır başka bir işlem tarafından alınmışsa null */
    private String processRow(InboundTable table, long id) {
        try {
            return tx.execute(s -> {
                List<Map<String, Object>> rows = jdbc.sql("select * from " + table.tableName
                                + " where id = :id and status = 'NEW' for update skip locked")
                        .param("id", id).query().listOfRows();
                if (rows.isEmpty()) {
                    return null;
                }
                InboundRowHandlers.Result r = handlers.handle(table, new InboundRow(rows.getFirst()));
                jdbc.sql("update " + table.tableName + " set status = :status, core_id = :coreId, "
                                + "error_code = null, error_message = :message, processed_at = :now where id = :id")
                        .param("status", r.status()).param("coreId", r.coreId()).param("message", r.message())
                        .param("now", OffsetDateTime.now(clock)).param("id", id)
                        .update();
                return r.status();
            });
        } catch (ImportException e) {
            return markFailed(table, id, "ERROR", e.code.name(), e.getMessage());
        } catch (DuplicatePaymentException e) {
            return markFailed(table, id, "DUPLICATE", null, e.getMessage());
        } catch (NotFoundException e) {
            return markFailed(table, id, "ERROR", ImportException.Code.NOT_FOUND.name(), e.getMessage());
        } catch (BusinessException e) {
            return markFailed(table, id, "ERROR", ImportException.Code.BUSINESS_RULE.name(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Giriş satırı işlenemedi: {} id={}", table.tableName, id, e);
            return markFailed(table, id, "ERROR", ImportException.Code.INTERNAL.name(),
                    "Beklenmeyen hata: " + e.getClass().getSimpleName() + " (ayrıntı uygulama logunda)");
        }
    }

    private String markFailed(InboundTable table, long id, String status, String code, String message) {
        String msg = message == null ? null : message.length() > 1000 ? message.substring(0, 1000) : message;
        tx.executeWithoutResult(s -> jdbc.sql("update " + table.tableName + " set status = :status, error_code = :code, "
                        + "error_message = :message, processed_at = :now where id = :id and status = 'NEW'")
                .param("status", status).param("code", code).param("message", msg)
                .param("now", OffsetDateTime.now(clock)).param("id", id)
                .update());
        return status;
    }
}
