-- Starter watchlist: large, liquid IDX stocks. Not an official index list (LQ45 changes twice a year).
-- Every row in `stock` is imported daily. Add a stock: INSERT a row here in a new migration, or by SQL.
-- Sectors follow IDX-IC level 1.
INSERT INTO stock (ticker, name, sector) VALUES
    ('BBCA', 'Bank Central Asia', 'Financials'),
    ('BBRI', 'Bank Rakyat Indonesia', 'Financials'),
    ('BMRI', 'Bank Mandiri', 'Financials'),
    ('BBNI', 'Bank Negara Indonesia', 'Financials'),
    ('BRIS', 'Bank Syariah Indonesia', 'Financials'),
    ('TLKM', 'Telkom Indonesia', 'Infrastructures'),
    ('ISAT', 'Indosat', 'Infrastructures'),
    ('JSMR', 'Jasa Marga', 'Infrastructures'),
    ('BREN', 'Barito Renewables Energy', 'Infrastructures'),
    ('ASII', 'Astra International', 'Industrials'),
    ('UNTR', 'United Tractors', 'Industrials'),
    ('UNVR', 'Unilever Indonesia', 'Consumer Non-Cyclicals'),
    ('ICBP', 'Indofood CBP Sukses Makmur', 'Consumer Non-Cyclicals'),
    ('INDF', 'Indofood Sukses Makmur', 'Consumer Non-Cyclicals'),
    ('CPIN', 'Charoen Pokphand Indonesia', 'Consumer Non-Cyclicals'),
    ('AMRT', 'Sumber Alfaria Trijaya', 'Consumer Non-Cyclicals'),
    ('HMSP', 'HM Sampoerna', 'Consumer Non-Cyclicals'),
    ('MAPI', 'Mitra Adiperkasa', 'Consumer Cyclicals'),
    ('KLBF', 'Kalbe Farma', 'Healthcare'),
    ('GOTO', 'GoTo Gojek Tokopedia', 'Technology'),
    ('ANTM', 'Aneka Tambang', 'Basic Materials'),
    ('MDKA', 'Merdeka Copper Gold', 'Basic Materials'),
    ('INCO', 'Vale Indonesia', 'Basic Materials'),
    ('AMMN', 'Amman Mineral Internasional', 'Basic Materials'),
    ('SMGR', 'Semen Indonesia', 'Basic Materials'),
    ('INKP', 'Indah Kiat Pulp & Paper', 'Basic Materials'),
    ('TPIA', 'Chandra Asri Pacific', 'Basic Materials'),
    ('BRPT', 'Barito Pacific', 'Basic Materials'),
    ('ADRO', 'Alamtri Resources Indonesia', 'Energy'),
    ('PTBA', 'Bukit Asam', 'Energy'),
    ('ITMG', 'Indo Tambangraya Megah', 'Energy'),
    ('PGAS', 'Perusahaan Gas Negara', 'Energy'),
    ('MEDC', 'Medco Energi Internasional', 'Energy'),
    ('AKRA', 'AKR Corporindo', 'Energy');

-- One row per import job run, so the app can show when data was last refreshed.
CREATE TABLE import_run (
    id              BIGSERIAL    PRIMARY KEY,
    source          VARCHAR(32)  NOT NULL,          -- e.g. YAHOO
    started_at      TIMESTAMPTZ  NOT NULL,
    finished_at     TIMESTAMPTZ,                    -- NULL while running or if it crashed
    stocks_ok       INTEGER      NOT NULL DEFAULT 0,
    stocks_failed   INTEGER      NOT NULL DEFAULT 0,
    rows_written    INTEGER      NOT NULL DEFAULT 0,
    message         TEXT                            -- failures, if any
);
