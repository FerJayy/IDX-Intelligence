-- IDX "High Shareholding Concentration" (HSC) list: a few holders own almost all shares. IDX removes
-- these stocks from the IHSG calculation from 2 November 2026; they keep trading. IDX publishes the list
-- in announcements (no API), so it is kept here and replaced via PUT /api/admin/hsc when IDX updates it.
CREATE TABLE hsc_stock (
    ticker      VARCHAR(10) PRIMARY KEY,
    as_of       DATE        NOT NULL,   -- date of the IDX data the entry comes from
    source      TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 80 stocks as of 12 Oct 2026 (IDX data, as published by investortrust.id on 13 Oct 2026).
INSERT INTO hsc_stock (ticker, as_of, source)
SELECT code, DATE '2026-10-12', 'IDX HSC list, data 2026-10-12 (via investortrust.id, 2026-10-13)'
FROM unnest(ARRAY[
    'MPRO','DCII','BBSI','PGUN','POLU','SOHO','BNLI','YUPI','ROCK','PRAY','BTPN','FAPA','IFSH','SMAR','MLPT','CMNT',
    'GEMS','LIFE','BNII','ELPI','MCOL','BYAN','KING','SOTS','DNET','RISE','AGII','ALII','DGWG','BREN','SRAJ','MKPI',
    'SILO','HATM','TBIG','WBSA','DSSA','MEGA','MORA','RLCO','KONI','FITT','STTP','BINA','SATU','TCPI','BELI','MGRO',
    'SSMS','BBHI','CMNP','AGAR','ALKA','BKDP','BAJA','MDIA','BDKR','SAFE','NICK','MSJA','BIKE','INDO','BSWD','PSAB',
    'SMMT','CITA','BMAS','MTEL','ADES','SAME','BBMD','SHIP','BALI','INPP','EPMT','DUTI','MAYA','CLAY','TMAS','DMND'
]) AS code;

-- Added on 23 Oct 2026 (Bisnis.com: total 82).
INSERT INTO hsc_stock (ticker, as_of, source) VALUES
    ('TFCO', DATE '2026-10-23', 'IDX HSC update 2026-10-23 (via Bisnis.com)'),
    ('FILM', DATE '2026-10-23', 'IDX HSC update 2026-10-23 (via Bisnis.com)');
