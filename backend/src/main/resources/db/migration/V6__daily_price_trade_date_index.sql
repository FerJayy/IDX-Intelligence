-- "Latest trading day" (max(trade_date) across all stocks) is asked on almost every request.
-- The primary key starts with ticker, so without this index that query scans every price row.
CREATE INDEX daily_price_trade_date_idx ON daily_price (trade_date);
