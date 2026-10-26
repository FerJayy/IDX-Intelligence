-- Broker registry, filled from provider responses.
CREATE TABLE broker (
    code        VARCHAR(8)  PRIMARY KEY,               -- IDX broker code, e.g. YP
    name        TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- IDX broker summary: one row per stock, trading day and broker.
-- Volumes in shares, values in IDR. Each day, total buy volume = total sell volume = the stock's volume.
CREATE TABLE broker_daily_activity (
    ticker               VARCHAR(10)    NOT NULL REFERENCES stock (ticker),
    trade_date           DATE           NOT NULL,
    broker_code          VARCHAR(8)     NOT NULL REFERENCES broker (code),
    buy_volume           BIGINT         NOT NULL,
    buy_value            NUMERIC(20, 2) NOT NULL,
    buy_frequency        INTEGER,                      -- number of trades; NULL = not provided
    sell_volume          BIGINT         NOT NULL,
    sell_value           NUMERIC(20, 2) NOT NULL,
    sell_frequency       INTEGER,
    -- Portion of the above done by foreign investors (through this broker).
    -- NULL = foreign flow not fetched yet; 0 = fetched, none.
    foreign_buy_volume   BIGINT,
    foreign_buy_value    NUMERIC(20, 2),
    foreign_sell_volume  BIGINT,
    foreign_sell_value   NUMERIC(20, 2),
    source               VARCHAR(32)    NOT NULL,
    fetched_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (ticker, trade_date, broker_code)
);

-- Which (stock, day, flow) summaries have been fetched, so each is requested only once,
-- even for days where the result had few rows.
CREATE TABLE broker_import_day (
    ticker        VARCHAR(10) NOT NULL REFERENCES stock (ticker),
    trade_date    DATE        NOT NULL,
    flow          VARCHAR(3)  NOT NULL CHECK (flow IN ('ALL', 'F')),  -- ALL investors / Foreign only
    broker_count  INTEGER     NOT NULL,
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ticker, trade_date, flow)
);
