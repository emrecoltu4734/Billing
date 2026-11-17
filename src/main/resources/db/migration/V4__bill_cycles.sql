-- =====================================================================
-- Fatura dönemleri (bill cycle)
--
-- Her abone bir bill cycle'a bağlıdır. cycle_day = D olan bir cycle'da
-- "2026-11" dönemi D Kasım – (D-1) Aralık aralığıdır ve D Aralık'ta
-- faturalanır (dönem sonu faturalama). D = 1 iken takvim ayıdır.
-- =====================================================================

CREATE TABLE bill_cycle (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    cycle_day   INTEGER      NOT NULL CHECK (cycle_day BETWEEN 1 AND 28),
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,   -- cycle belirtilmeyen yeni aboneler buraya
    auto_run    BOOLEAN      NOT NULL DEFAULT FALSE,   -- cycle gününde gece otomatik faturala
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- En fazla bir varsayılan cycle
CREATE UNIQUE INDEX ux_bill_cycle_default ON bill_cycle (is_default) WHERE is_default;

INSERT INTO bill_cycle (code, name, cycle_day, is_default) VALUES ('C01', 'Ayın 1''i', 1, TRUE);

-- Abone → cycle
ALTER TABLE customer ADD COLUMN bill_cycle_id BIGINT REFERENCES bill_cycle (id);
UPDATE customer SET bill_cycle_id = (SELECT id FROM bill_cycle WHERE code = 'C01');
ALTER TABLE customer ALTER COLUMN bill_cycle_id SET NOT NULL;
CREATE INDEX ix_customer_bill_cycle ON customer (bill_cycle_id);

-- Bill run artık cycle + dönem başına tekil
ALTER TABLE bill_run ADD COLUMN bill_cycle_id BIGINT REFERENCES bill_cycle (id);
UPDATE bill_run SET bill_cycle_id = (SELECT id FROM bill_cycle WHERE code = 'C01');
ALTER TABLE bill_run ALTER COLUMN bill_cycle_id SET NOT NULL;
ALTER TABLE bill_run DROP CONSTRAINT bill_run_period_start_key;
ALTER TABLE bill_run ADD CONSTRAINT ux_bill_run_cycle_period UNIQUE (bill_cycle_id, period_start);

-- Hatalı abone run'ı durdurmaz: run hatalarla tamamlanabilir ve kaldığı yerden sürdürülebilir
ALTER TABLE bill_run DROP CONSTRAINT bill_run_status_check;
ALTER TABLE bill_run ALTER COLUMN status TYPE VARCHAR(30);
ALTER TABLE bill_run ADD CONSTRAINT bill_run_status_check
    CHECK (status IN ('RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS'));
ALTER TABLE bill_run ADD COLUMN error_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE bill_run_error (
    id           BIGSERIAL PRIMARY KEY,
    bill_run_id  BIGINT        NOT NULL REFERENCES bill_run (id),
    customer_id  BIGINT        NOT NULL REFERENCES customer (id),
    message      VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_bill_run_error_run ON bill_run_error (bill_run_id);

-- Aboneliğin hangi güne kadar faturalandığı. Cycle değişikliğinde aynı günlerin
-- iki kez faturalanmasını engeller; sonraki fatura bu günden sonrasını kapsar.
ALTER TABLE subscription ADD COLUMN billed_through DATE;
UPDATE subscription s
   SET billed_through = (SELECT LEAST(MAX(i.period_end), COALESCE(s.end_date, MAX(i.period_end)))
                           FROM invoice_line l JOIN invoice i ON i.id = l.invoice_id
                          WHERE l.subscription_id = s.id);

-- Entegrasyon: aboneyi cycle ile gönderebilme (boş bırakılabilir kolon, sözleşmeye eklemedir)
ALTER TABLE in_customer ADD COLUMN bill_cycle_code VARCHAR(20);
