-- =====================================================================
-- Fatura kesinleştirme ve aboneye gönderim
--
-- Faturalandırma önce TASLAK (DRAFT, numarasız) fatura üretir. Çalıştırma
-- bitince taslaklar tek seferde kesinleştirilir: boşluksuz fatura numarası,
-- aboneye gönderilecek bağlantı anahtarı ve e-posta/SMS kayıtları aynı
-- transaction'da oluşur.
-- =====================================================================

ALTER TABLE invoice ALTER COLUMN invoice_no DROP NOT NULL;   -- taslakta numara yok
ALTER TABLE invoice DROP CONSTRAINT invoice_status_check;
ALTER TABLE invoice ADD CONSTRAINT invoice_status_check
    CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'));
ALTER TABLE invoice ADD CONSTRAINT invoice_no_required_when_final
    CHECK (status = 'DRAFT' OR invoice_no IS NOT NULL);

ALTER TABLE invoice ADD COLUMN issued_at  TIMESTAMPTZ;           -- kesinleşme zamanı
ALTER TABLE invoice ADD COLUMN view_token VARCHAR(64) UNIQUE;    -- aboneye gönderilen bağlantının anahtarı

UPDATE invoice
   SET issued_at  = created_at,
       view_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '')
 WHERE status <> 'DRAFT';

-- Boşluksuz fatura numarası: yıl başına sayaç. Satır kilidiyle artırılır; transaction geri alınırsa
-- numara da geri alınır (sequence'lerden farkı budur). GİB numaraların yıl içinde boşluksuz olmasını ister.
CREATE TABLE invoice_number_series (
    year        INTEGER PRIMARY KEY,
    last_value  BIGINT  NOT NULL
);
INSERT INTO invoice_number_series (year, last_value)
SELECT substring(invoice_no FROM 4 FOR 4)::int, max(substring(invoice_no FROM 8)::bigint)
  FROM invoice WHERE invoice_no IS NOT NULL
 GROUP BY 1;
DROP SEQUENCE invoice_no_seq;

-- Gönderim kuyruğu (outbox). Fatura kesinleştiğinde aynı transaction'da yazılır, arka plan işi gönderir.
CREATE TABLE notification (
    id               BIGSERIAL PRIMARY KEY,
    invoice_id       BIGINT        NOT NULL REFERENCES invoice (id),
    channel          VARCHAR(10)   NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    recipient        VARCHAR(200)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts         INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_error       VARCHAR(1000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    UNIQUE (invoice_id, channel)
);
CREATE INDEX ix_notification_due ON notification (status, next_attempt_at);
