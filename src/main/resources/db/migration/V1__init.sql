-- Abone (müşteri)
CREATE SEQUENCE customer_no_seq START 1;

CREATE TABLE customer (
    id           BIGSERIAL PRIMARY KEY,
    customer_no  VARCHAR(20)  NOT NULL UNIQUE
                 DEFAULT ('M' || lpad(nextval('customer_no_seq')::text, 8, '0')),
    type         VARCHAR(20)  NOT NULL CHECK (type IN ('INDIVIDUAL', 'CORPORATE')),
    name         VARCHAR(200) NOT NULL,          -- Ad soyad veya unvan
    tax_id       VARCHAR(11)  NOT NULL,          -- TCKN (11) veya VKN (10)
    tax_office   VARCHAR(100),
    phone        VARCHAR(20),
    email        VARCHAR(200),
    address      VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Tarife / paket
CREATE TABLE plan (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(50)   NOT NULL UNIQUE,
    name         VARCHAR(200)  NOT NULL,
    monthly_fee  NUMERIC(14,2) NOT NULL CHECK (monthly_fee >= 0),  -- Vergiler hariç
    oiv_rate     NUMERIC(5,2)  NOT NULL CHECK (oiv_rate >= 0),     -- Özel İletişim Vergisi %
    vat_rate     NUMERIC(5,2)  NOT NULL CHECK (vat_rate >= 0),     -- KDV %
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Abonelik (abone + tarife + tarih aralığı)
CREATE TABLE subscription (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT      NOT NULL REFERENCES customer(id),
    plan_id      BIGINT      NOT NULL REFERENCES plan(id),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    start_date   DATE        NOT NULL,
    end_date     DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_subscription_customer ON subscription(customer_id);
CREATE INDEX ix_subscription_dates ON subscription(start_date, end_date);

-- Fatura dönemi çalıştırması (bill run)
CREATE TABLE bill_run (
    id             BIGSERIAL PRIMARY KEY,
    period_start   DATE          NOT NULL UNIQUE,  -- Aynı dönem iki kez faturalanamaz
    period_end     DATE          NOT NULL,
    status         VARCHAR(20)   NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED')),
    invoice_count  INTEGER       NOT NULL DEFAULT 0,
    total_amount   NUMERIC(16,2) NOT NULL DEFAULT 0,
    started_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ
);

-- Fatura
CREATE SEQUENCE invoice_no_seq START 1;

CREATE TABLE invoice (
    id            BIGSERIAL PRIMARY KEY,
    invoice_no    VARCHAR(16)   NOT NULL UNIQUE,
    customer_id   BIGINT        NOT NULL REFERENCES customer(id),
    bill_run_id   BIGINT        REFERENCES bill_run(id),
    period_start  DATE          NOT NULL,
    period_end    DATE          NOT NULL,
    issue_date    DATE          NOT NULL,
    due_date      DATE          NOT NULL,
    net_amount    NUMERIC(14,2) NOT NULL,
    oiv_amount    NUMERIC(14,2) NOT NULL,
    vat_amount    NUMERIC(14,2) NOT NULL,
    total_amount  NUMERIC(14,2) NOT NULL,
    paid_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    status        VARCHAR(20)   NOT NULL CHECK (status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (customer_id, period_start)
);
CREATE INDEX ix_invoice_status_due ON invoice(status, due_date);

CREATE TABLE invoice_line (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT        NOT NULL REFERENCES invoice(id),
    subscription_id BIGINT        REFERENCES subscription(id),
    description     VARCHAR(300)  NOT NULL,
    billed_days     INTEGER       NOT NULL,
    period_days     INTEGER       NOT NULL,
    unit_price      NUMERIC(14,2) NOT NULL,   -- Aylık tam ücret
    net_amount      NUMERIC(14,2) NOT NULL,   -- Kıst (prorate) edilmiş tutar
    oiv_rate        NUMERIC(5,2)  NOT NULL,
    oiv_amount      NUMERIC(14,2) NOT NULL,
    vat_rate        NUMERIC(5,2)  NOT NULL,
    vat_amount      NUMERIC(14,2) NOT NULL,
    total_amount    NUMERIC(14,2) NOT NULL,
    period_start    DATE          NOT NULL,
    UNIQUE (subscription_id, period_start)    -- Bir abonelik aynı dönemde iki kez faturalanamaz
);

-- Tahsilat
CREATE TABLE payment (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT        NOT NULL REFERENCES invoice(id),
    amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    method      VARCHAR(20)   NOT NULL CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CREDIT_CARD', 'ONLINE')),
    reference   VARCHAR(100),
    paid_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_invoice ON payment(invoice_id);
