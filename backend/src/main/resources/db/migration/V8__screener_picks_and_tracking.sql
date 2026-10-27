-- Daily picks from IDX Edge's market-wide screener (third-party; their win rates are claims).
-- Kept so we can measure what actually happened after each pick.
CREATE TABLE screener_pick (
    pick_date    DATE          NOT NULL,          -- trading day the screener describes
    ticker       VARCHAR(10)   NOT NULL,
    bucket       TEXT          NOT NULL,          -- provider's group, e.g. "SINYAL SENYAP"
    summary      TEXT,
    note         TEXT,
    win_rate     NUMERIC(6, 2),                   -- provider's claimed "WR Event", percent; NULL = not given
    potential    NUMERIC(8, 2),                   -- provider's claimed upside, percent
    drawdown     NUMERIC(8, 2),                   -- provider's claimed drawdown, percent
    source       VARCHAR(32)   NOT NULL,
    fetched_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (pick_date, ticker)
);

-- Why and since when a stock gets broker summaries, and how far back to fetch them.
ALTER TABLE stock
    ADD COLUMN broker_tracked_reason VARCHAR(16),   -- CORE | SCREENER | USER
    ADD COLUMN broker_tracked_since  DATE,
    ADD COLUMN broker_backfill_from  DATE;          -- NULL = the import's default window

UPDATE stock SET broker_tracked_reason = 'CORE' WHERE broker_tracked;
