-- Flyway runs each V<n>__*.sql file once, in order, and records it in flyway_schema_history.
-- Never edit a migration after it has run; add a new V<n+1> file instead.

-- One row per listed stock.
CREATE TABLE stock (
    ticker      VARCHAR(10) PRIMARY KEY,           -- IDX code, e.g. BBCA
    name        TEXT        NOT NULL,
    sector      TEXT,                              -- NULL = unknown
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per stock per trading day. The primary key makes re-imports safe:
-- the same (ticker, trade_date) can only exist once, so imports update instead of duplicating.
CREATE TABLE daily_price (
    ticker          VARCHAR(10)   NOT NULL REFERENCES stock (ticker),
    trade_date      DATE          NOT NULL,          -- IDX trading day (Asia/Jakarta)
    open            NUMERIC(18, 2),                  -- NULL = missing, never 0
    high            NUMERIC(18, 2),
    low             NUMERIC(18, 2),
    close           NUMERIC(18, 2) NOT NULL,
    adjusted_close  NUMERIC(18, 4),                  -- close adjusted for splits/dividends
    volume          BIGINT,                          -- shares
    source          VARCHAR(32)   NOT NULL,          -- where the row came from, e.g. YAHOO
    fetched_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (ticker, trade_date)
);
