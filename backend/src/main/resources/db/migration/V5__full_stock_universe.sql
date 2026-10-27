-- The stock table now holds (almost) every IDX listing, synced weekly from IDX Edge.
-- Prices are imported for all active stocks; broker summaries only for broker_tracked ones,
-- because broker data costs one provider request per stock per day.
ALTER TABLE stock
    ADD COLUMN listed_shares   BIGINT,                              -- NULL = unknown
    ADD COLUMN market_cap      NUMERIC(24, 2),                      -- IDR, from the last sync; NULL = unknown
    ADD COLUMN active          BOOLEAN     NOT NULL DEFAULT true,   -- false = no longer in the listing (e.g. delisted)
    ADD COLUMN broker_tracked  BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN synced_at       TIMESTAMPTZ;                         -- last time the listing sync saw this stock

-- The original 34-stock watchlist keeps its broker data.
UPDATE stock SET broker_tracked = true;
