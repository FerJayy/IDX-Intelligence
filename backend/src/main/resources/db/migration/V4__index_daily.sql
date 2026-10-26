-- Daily values for market indices (not stocks, so kept out of `stock` / `daily_price`).
CREATE TABLE index_daily (
    code        VARCHAR(16)    NOT NULL,      -- our code, e.g. COMPOSITE (IHSG)
    trade_date  DATE           NOT NULL,
    open        NUMERIC(18, 4),
    high        NUMERIC(18, 4),
    low         NUMERIC(18, 4),
    close       NUMERIC(18, 4) NOT NULL,
    volume      BIGINT,
    source      VARCHAR(32)    NOT NULL,
    fetched_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (code, trade_date)
);
