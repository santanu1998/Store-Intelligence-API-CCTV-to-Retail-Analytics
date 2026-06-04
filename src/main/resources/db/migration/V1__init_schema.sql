-- ============================================================
--  Store Intelligence API — Initial Schema (V1)
--  All DATETIME columns store values in UTC.
--  store_id format: e.g. "STORE_BLR_002", "ST1008"
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- 1. STORES  (auto-registered on first event ingest)
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stores (
    store_id        VARCHAR(60)  NOT NULL,
    store_name      VARCHAR(255),
    city            VARCHAR(100),
    registered_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ──────────────────────────────────────────────────────────
-- 2. STORE_EVENTS  (raw events from the detection pipeline)
--    Idempotent by event_id (UUID v4).
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS store_events (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    event_id         VARCHAR(36)   NOT NULL  COMMENT 'UUID v4 — idempotency key',
    store_id         VARCHAR(60)   NOT NULL,
    camera_id        VARCHAR(100),
    visitor_id       VARCHAR(100)  NOT NULL,
    event_type       VARCHAR(50)   NOT NULL
                       COMMENT 'ENTRY|EXIT|ZONE_ENTER|ZONE_EXIT|ZONE_DWELL|BILLING_QUEUE_JOIN|BILLING_QUEUE_ABANDON|REENTRY',
    event_timestamp  DATETIME      NOT NULL  COMMENT 'UTC — derived from clip + frame offset',
    zone_id          VARCHAR(100)            COMMENT 'null for ENTRY/EXIT events',
    dwell_ms         BIGINT        NOT NULL  DEFAULT 0,
    is_staff         TINYINT(1)    NOT NULL  DEFAULT 0,
    confidence       DECIMAL(5,4)            COMMENT 'model detection confidence 0.00–1.00',
    queue_depth      INT                     COMMENT 'populated for BILLING_QUEUE_JOIN',
    sku_zone         VARCHAR(100)            COMMENT 'zone label from store_layout',
    session_seq      INT                     COMMENT 'ordinal position in visitor session',
    ingested_at      DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE  KEY uk_event_id          (event_id),
    KEY             idx_store_time   (store_id, event_timestamp),
    KEY             idx_visitor      (store_id, visitor_id),
    KEY             idx_event_type   (store_id, event_type),
    KEY             idx_zone         (store_id, zone_id),
    KEY             idx_is_staff     (store_id, is_staff)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add foreign key constraint after both tables exist
ALTER TABLE store_events ADD CONSTRAINT fk_events_store
    FOREIGN KEY (store_id) REFERENCES stores (store_id)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- ──────────────────────────────────────────────────────────
-- 3. POS_TRANSACTIONS
--    Timestamped POS records; correlated with events to
--    compute conversion rate (no customer_id — time-window
--    correlation only).
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pos_transactions (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    store_id                VARCHAR(60)     NOT NULL,
    transaction_id          VARCHAR(100)    NOT NULL,
    transaction_timestamp   DATETIME        NOT NULL  COMMENT 'UTC',
    basket_value_inr        DECIMAL(12, 2),
    order_date              DATE,
    invoice_number          VARCHAR(100),
    invoice_type            VARCHAR(50),
    customer_number         VARCHAR(50),
    salesperson_id          VARCHAR(50),
    ingested_at             DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE  KEY uk_transaction_id      (transaction_id),
    KEY             idx_pos_store_time (store_id, transaction_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add foreign key constraint after both tables exist
ALTER TABLE pos_transactions ADD CONSTRAINT fk_pos_store
    FOREIGN KEY (store_id) REFERENCES stores (store_id)
    ON DELETE RESTRICT ON UPDATE CASCADE;

