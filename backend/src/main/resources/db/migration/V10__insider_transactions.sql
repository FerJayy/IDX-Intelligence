-- Insider transactions (directors, commissioners, >= 5% holders) as disclosed to IDX, via IDX Edge.
-- Fetched per stock on demand (a stock page asks), at most once a day per stock.
CREATE TABLE insider_transaction (
    id              BIGSERIAL     PRIMARY KEY,
    ticker          VARCHAR(10)   NOT NULL REFERENCES stock (ticker),
    trade_date      DATE          NOT NULL,
    insider_name    TEXT          NOT NULL,
    action          VARCHAR(20)   NOT NULL,          -- as given by the source, e.g. buy / sell
    nationality     VARCHAR(20),                     -- local / foreign; NULL = not given
    shares_before   BIGINT,
    shares_after    BIGINT,
    shares_change   BIGINT,                          -- signed
    percent_before  NUMERIC(12, 6),                  -- percent of all shares (1.5 = 1,5%)
    percent_after   NUMERIC(12, 6),
    price           NUMERIC(18, 2),                  -- NULL = not given
    source          VARCHAR(30)   NOT NULL,
    fetched_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- The source has no ids: the same person can report twice on one day, so the holding after
    -- the trade is part of the identity. NULLS NOT DISTINCT so missing figures still dedupe.
    CONSTRAINT insider_transaction_identity
        UNIQUE NULLS NOT DISTINCT (ticker, trade_date, insider_name, action, shares_after)
);

CREATE INDEX insider_transaction_ticker_date ON insider_transaction (ticker, trade_date DESC);

-- When each stock's insider list was last fetched, so pages don't re-fetch within a day.
CREATE TABLE insider_fetch (
    ticker      VARCHAR(10)  PRIMARY KEY REFERENCES stock (ticker),
    fetched_at  TIMESTAMPTZ  NOT NULL,
    total       INTEGER                             -- all disclosures the source knows for the stock
);
