-- Set when Yahoo has no data for a stock (HTTP 404, e.g. long-suspended or delisted).
-- Daily imports skip such stocks and retry them once a week; a successful import clears it.
ALTER TABLE stock ADD COLUMN price_missing_since DATE;
