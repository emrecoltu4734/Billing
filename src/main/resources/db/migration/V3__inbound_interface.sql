-- =====================================================================
-- Entegrasyon arayüzü (giriş tabloları)
--
-- Müşteri sistemleri core tablolara DOĞRUDAN YAZMAZ. Toplu veri bu in_*
-- tablolarına eklenir; ISP360 import batch'i satırları doğrular, core
-- tablolara işler ve sonucu aynı satıra geri yazar.
--
-- Bu tablolar müşteriyle aramızdaki sözleşmedir: kolon silinmez, adı
-- veya anlamı değiştirilmez. Yalnızca boş bırakılabilir (nullable) yeni
-- kolon eklenebilir. Ayrıntı: docs/ENTEGRASYON.md
-- =====================================================================

-- Müşteri sistemindeki kimlikler (external_ref) ile core kayıtlarının eşlemesi
ALTER TABLE customer     ADD COLUMN external_ref VARCHAR(64) UNIQUE;
ALTER TABLE subscription ADD COLUMN external_ref VARCHAR(64) UNIQUE;
ALTER TABLE payment      ADD COLUMN external_ref VARCHAR(64) UNIQUE;

-- Ortak durum alanları:
--   status        NEW → DONE | ERROR | DUPLICATE   (müşteri yalnızca NEW ekler)
--   error_code    VALIDATION | NOT_FOUND | BUSINESS_RULE | INTERNAL
--   core_id       işlem sonucu oluşan/değişen core kaydın id'si

CREATE TABLE in_customer (
    id             BIGSERIAL PRIMARY KEY,
    external_ref   VARCHAR(64)  NOT NULL,            -- müşterinin kendi abone kimliği
    operation      VARCHAR(20)  NOT NULL DEFAULT 'UPSERT' CHECK (operation IN ('UPSERT')),
    type           VARCHAR(20),                      -- INDIVIDUAL | CORPORATE
    name           VARCHAR(200),
    tax_id         VARCHAR(11),
    tax_office     VARCHAR(100),
    phone          VARCHAR(20),
    email          VARCHAR(200),
    address        VARCHAR(500),
    status         VARCHAR(20)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'DONE', 'ERROR', 'DUPLICATE')),
    error_code     VARCHAR(30),
    error_message  VARCHAR(1000),
    core_id        BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ
);
CREATE INDEX ix_in_customer_status ON in_customer(status, id);

CREATE TABLE in_subscription_event (
    id                     BIGSERIAL PRIMARY KEY,
    external_ref           VARCHAR(64)  NOT NULL,    -- müşterinin kendi abonelik/hizmet kimliği
    operation              VARCHAR(20)  NOT NULL CHECK (operation IN ('ACTIVATE', 'SUSPEND', 'RESUME', 'TERMINATE')),
    customer_external_ref  VARCHAR(64),              -- ACTIVATE için zorunlu
    plan_code              VARCHAR(50),              -- ACTIVATE için zorunlu
    effective_date         DATE,                     -- ACTIVATE: başlangıç, TERMINATE: son hizmet günü (dahil)
    status                 VARCHAR(20)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'DONE', 'ERROR', 'DUPLICATE')),
    error_code             VARCHAR(30),
    error_message          VARCHAR(1000),
    core_id                BIGINT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at           TIMESTAMPTZ
);
CREATE INDEX ix_in_subscription_event_status ON in_subscription_event(status, id);

CREATE TABLE in_payment (
    id                     BIGSERIAL PRIMARY KEY,
    external_ref           VARCHAR(64)   NOT NULL,   -- ödeme/işlem kimliği: tekrar gönderimde çift kayıt oluşmaz
    invoice_no             VARCHAR(16),              -- şu an zorunlu
    customer_external_ref  VARCHAR(64),              -- ileride faturasız (cari hesaba) ödeme için ayrıldı
    amount                 NUMERIC(14,2),
    method                 VARCHAR(20),              -- CASH | BANK_TRANSFER | CREDIT_CARD | ONLINE
    paid_at                TIMESTAMPTZ,              -- boşsa işlenme zamanı
    reference              VARCHAR(100),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'DONE', 'ERROR', 'DUPLICATE')),
    error_code             VARCHAR(30),
    error_message          VARCHAR(1000),
    core_id                BIGINT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at           TIMESTAMPTZ
);
CREATE INDEX ix_in_payment_status ON in_payment(status, id);
