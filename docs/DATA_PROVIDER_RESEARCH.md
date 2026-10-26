# IDX Data Provider Research

Research date: 2026-10-26. Scope: data sources for a personal (non-commercial) IDX research app with a Spring Boot backend, a PostgreSQL cache and a Next.js frontend, used by roughly 3 to 6 people.

Legend used in this document:
- **[Confirmed]**: read directly on the provider's page or docs on 2026-10-26 (URL listed).
- **[Tested]**: verified by an anonymous HTTP request on 2026-10-26 (no account).
- **[Inferred]**: my reasoning or a calculation. Not stated by the provider.
- **[Search only]**: seen only in a search-engine summary. The page itself could not be loaded.
- **[Not loaded]**: the page returned 403/429 or was JS-only, so nothing is claimed.

---

## 1. Recommendation

**Broker summary (the main risk):**
- **Start at Rp0 with IDX Edge PRO (stock.arjum.com).** The free tier is 1,000 requests/day. It has per-stock broker summary with a foreign/domestic filter, broker accumulation history and OHLCV since about 2020. That is enough for one broker-summary call per listed stock per day. The risk is that it is a small, single-operator, donation-funded service with no SLA and no redistribution terms. A similar service, idxpro.id, shut down in 2026.
- **Paid fallback: Index Alpha Starter, Rp250.000/month (Rp200.000 per 30 days on a 3-month plan).** It has the cleanest documented broker-summary schema (buy/sell freq, volume, value and average price per broker), RG and NG markets, history from 2025-01-01 and updates at 19:00 WIB. Budget for the Professional plan (Rp450.000/month) if you want both broker summary and foreign flow for every stock every day.
- **TICMI** (IDX-group institute) has the richest and most official data: broker summary split by investor type per broker, since 1977. API access requires a cooperation agreement at a price you must ask for. Ask only if the options above fail.

**Daily OHLCV, IHSG and splits/dividends:**
- **Yahoo Finance's unofficial chart endpoint** (`BBCA.JK`, `^JKSE`) is free. I tested it today: BBCA daily bars go back to 2005 and include adjusted close plus split and dividend events. It is unofficial, may break, and Yahoo's terms forbid redistribution. Use it for backfill and charts only.
- **Paid fallback: EODHD "EOD Historical", US$19.99/month** (personal plan, 962 active .JK tickers).

**Cheapest viable combo:** IDX Edge PRO free plus Yahoo, at **Rp0/month**. The reliable combo is **Index Alpha Starter plus Yahoo, at about Rp200.000 to 250.000/month**. Cache everything in PostgreSQL so that you never need to re-fetch history.

---

## 2. Comparison table

| Source | Broker summary | Foreign/domestic | OHLCV history | Corp. actions | Update (WIB) | Free tier | Cheapest paid | Official? |
|---|---|---|---|---|---|---|---|---|
| **IDX Edge PRO** (stock.arjum.com) | Yes. Per stock, date range, all brokers optional [Confirmed] | Yes. `flow=F/D/N` filter [Confirmed] | Since ~2020 for most stocks, max 500 candles/call [Confirmed] | Not seen | Not stated | 1,000 req/day [Confirmed] | Daily-quota top-ups for 30 days. Exact price not confirmed | No (third party) |
| **Index Alpha** (indexalpha.id) | Yes. Per broker freq/vol/value/avg [Confirmed] | Yes. `investor=f/d` + foreign-flow endpoint [Confirmed] | **No price data** | No | 19:00 [Confirmed] | 5 req/day [Confirmed] | Rp250.000/mo (Rp200.000/30d on 3M) [Confirmed] | No (third party) |
| **Sectors** (sectors.app) | Yes. Per broker per day, with foreign split per broker [Confirmed] | Yes [Confirmed] | Yes. 90-day window/call, depth not stated | Yes (calendar) [Confirmed] | Not stated | No API on free tier [Confirmed] | US$59/mo, 5,000 credits [Confirmed] | No (third party) |
| **Api.co.id** | "Top" buying/selling brokers (Trader plan) [Confirmed] | Yes (Trader) [Confirmed] | Daily/weekly/monthly, depth not stated (Free) [Confirmed] | Calendar (Free) | Intraday, `as_of` in response | 500 hits/month [Confirmed] | Trader Rp399.000/mo for broker data [Confirmed] | No (generic API marketplace) |
| **TICMI / TICMIDATA** | Yes. Per broker, per investor type, per board [Confirmed] | Yes. Investor summary endpoint [Confirmed] | Yes. "Since 1977" (site claim) [Confirmed] | Yes (dividend, split, rights, etc.) [Confirmed] | "End of day" [Confirmed] | None for API | API by cooperation agreement. Price not public | Semi-official (IDX group) |
| **IDX official data feeds** | EOD products include "Top Brokers" and investor-type recaps | Yes (EoD Recapitulation) | Licensed EOD feed | Licensed reference data | EOD | Website only (could not load) | Rp millions/month per product (see §4.6) | Official |
| **Yahoo Finance** (unofficial) | No | No | BBCA.JK from 2005-01-03 [Tested] | Splits + dividends, adjclose [Tested] | Intraday (delayed) | Free, unofficial | n/a | No. Unofficial endpoint |
| **EODHD** | No | No | 30+ yrs claimed generally. JK depth not verified | Dividends & splits included in EOD plan [Confirmed] | Not verified for JK | 20 calls/day, 1 year of EOD [Confirmed] | US$19.99/mo personal [Confirmed] | No (data vendor) |

---

## 3. IDX broker-code policy (Dec 2021 onward)

- **6 December 2021:** IDX stopped showing broker codes in the running trade **during** the trading session. **[Search only]** This comes from search summaries of Bisnis.com (2021-11-29, 2021-12-05), IDX Channel and Katadata. Bisnis pages returned 403. Per those summaries, broker information stays available **after the close**: IDX keeps distributing broker summary, investor-type summary, daily trading information and EOD transaction data.
- **Mid-2022:** Investor domicile (foreign/domestic) during the session was also hidden, "6 months after" the broker-code closure. **[Search only]** Bisnis.com, 2022-06-25.
- **25 August 2025:** IDX started releasing trading-summary files at the **end of session 1** as well as end of day. The files cover summaries by investor domicile code, index data and recaps by investor type. The article does not mention broker codes. **[Confirmed]** https://www.aseanexchanges.org/content/idx-increases-the-frequency-of-trading-summary-distribution-to-strengthen-investor-confidence/
- **Broker codes in real time:** I found **no confirmation** that they have been re-opened. 2024 to 2025 articles talk about re-opening "not in real time" (Bisnis 2024-01-02, 2025-06-16). **[Search only]**
- **Where the post-close broker summary is published:** Through Exchange Members (brokerage apps such as Stockbit, IPOT and Ajaib) and IDX licensed data feeds. Every third-party API above re-sells or re-derives that EOD data. IDX's own website pages (`idx.co.id/.../ringkasan-broker/`) returned **403** to my requests, so I cannot confirm what the public site still shows. **[Not loaded]**

Implication for the app: **EOD broker summary is the realistic product.** Providers publish it the same evening (Index Alpha states 19:00 WIB).

---

## 4. Per-source details

### 4.1 IDX Edge PRO (stock.arjum.com)
- **Pages read:** https://stock.arjum.com/api-docs. The page is a JavaScript SPA. I read the HTML meta/FAQ and the shipped JS bundle (`/assets/index-B2Q8hrzB.js`), which contains the endpoint docs and the Terms text.
- **API style / auth:** REST JSON plus an MCP server (`https://stock.arjum.com/mcp`). Auth is the header `X-API-Key`. There are 15 REST endpoints, including `/api/broker-summary/{code}`, `/api/broker-accumulation/{code}`, `/api/history/{code}`, `/api/done-details`, `/api/price/list`, `/api/financial-statements/{code}`, `/api/insiders/{code}` and `/api/market-cap`. **[Confirmed]**
- **Broker summary** **[Confirmed]**:
  - Parameters: `start_date`, `end_date`, `broker_limit` (default 20), `all_data=true` (no limits), `net`, and `flow` = `all` (all investors, regular market) / `F` (foreign) / `D` (domestic) / `N` (negotiated).
  - Sample fields: `broker_code`, `broker_name`, `bval`, `sval`, `nval`, `nvol`, `bfrq`, `sfrq`, plus `broker_levels`.
  - The sample shows no per-broker average-price field. **[Inferred]** You can compute it as value / (vol × 100) if buy and sell volumes are present. Verify this with a real response.
  - History depth for broker summary is **not stated**.
- **Done details:** trade-by-trade data for one stock on one date, with an aggressor side derived by tick rule. **[Confirmed]**
- **OHLCV** **[Confirmed]**: `/api/history/{code}` returns daily, weekly or monthly bars. History is "available since 2020 for most stocks" and the `data_available` field gives the actual range. Each call returns 20 to 500 candles. Whether bars are adjusted for splits is **not stated**.
- **Quota** **[Confirmed]**:
  - Free: 1,000 req/day, resetting at 00:00 WIB.
  - Paid "top-ups" add +5,000, +10,000, +20,000 or +50,000 req/day, valid 30 days, paid via a "traktir" (tip-style) flow. A custom amount is also allowed.
  - **Price:** the bundle suggests the rupiah amount equals the quota number, but I could not confirm the rendered price, so treat it as unknown.
  - Batch price calls cost 1 quota unit per stock code.
- **Terms** **[Confirmed]** (Terms of Service, "last updated 30 July 2026"):
  - Bans hacking, "scraping berlebihan" (excessive scraping) and quota exploitation.
  - Data is provided as a decision aid with no liability.
  - Terms can change without notice.
  - **No clause on caching, storage or showing data to other users.**
- **Reliability signals:**
  - Positive: a reasonably detailed docs page and an active product.
  - Negative: single operator, donation-style payment, no SLA, no stated data origin. **[Inferred]**
  - Comparable risk: idxpro.id, a similar IDX MCP/data service, now shows "idxpro telah tutup… layanan dihentikan" ("idxpro has closed… service discontinued"). **[Confirmed]** https://idxpro.id/

### 4.2 Index Alpha (indexalpha.id)
- **Pages read:** https://indexalpha.id/id/docs, https://indexalpha.id/docs/endpoints, https://indexalpha.id/docs/limits, https://indexalpha.id/pricing. `/terms` redirected to the home page (no terms page found).
- **API style / auth:** REST at `https://api.indexalpha.id`. Auth is a Bearer token from the dashboard (Google sign-in). CSV/Excel export is also available from the dashboard, and there is a remote MCP. **[Confirmed]**
- **Endpoints** **[Confirmed]**: `GET /stocks/broker-summary`, `POST /stocks/broker-summary/batch` (max 50 tickers), `GET /foreign-flow`, `POST /foreign-flow/batch`, `GET /usage` (free), `GET /news`.
- **Broker summary fields** **[Confirmed]**: `code`, `buy_freq`, `buy_volume`, `buy_value`, `sell_freq`, `sell_volume`, `sell_value`, `buy_avg`, `sell_avg`.
  - Parameters: `investor` = `all` / `f` / `d`, and `market` = `RG` (default) / `NG` / `ALL`.
  - **A date range is aggregated into one row per broker.** For daily rows, call with `from = to`.
  - Volume is presumably in shares, not lots (sample buy_value / buy_volume ≈ price). **[Inferred]**
- **History:** from 2025-01-01. Earlier dates are rejected, though "older data may be available on the Enterprise plan". Maximum 366 days per request. **[Confirmed]**
- **Update time:** every trading day at 19:00 WIB (12:00 GMT). **[Confirmed]**
- **OHLCV:** none. Index Alpha is broker and foreign-flow data only.
- **Pricing (IDR)** **[Confirmed]**:

  | Plan | Price | Requests | Rate limit | API keys |
  |---|---|---|---|---|
  | Free | Rp0 | 5 req/day | 10 req/min | 1 |
  | Starter | Rp250.000/month, or Rp200.000 per 30 days on 3-month | 25,000/month | 120 req/min | 1 |
  | Professional | Rp450.000/month, or Rp350.000 per 30 days on 3-month | 100,000/month | 300 req/min | 5 |
  | Advanced | Rp950.000/month, or Rp750.000 per 30 days on 3-month | 250,000/month | 500 req/min | 10 |
  | Enterprise | Custom | Custom | Custom | Custom |

  - Batch calls cost 1 unit per ticker.
  - The site says "Prices… may change without notice."
- **Sizing** **[Inferred]**: about 950 listed stocks × about 21 trading days ≈ 20,000 units/month for one broker-summary call per stock per day.
  - That fits Starter, but **only for `investor=all`**.
  - Adding foreign flow (another ~20k), or `f`/`d` per broker, needs Professional.
  - Backfilling all stocks from 2025-01-01 (~450 trading days × 950 ≈ 430k units) is unrealistic. Backfill a watchlist only.
- **Terms:** no ToS page found, so caching and redistribution are **not addressed**.
- **Reliability signals:** good docs (rate-limit headers, error semantics, usage endpoint), a Telegram community and explicit schedule/coverage statements. Who runs it, and the legal entity, is not stated.

### 4.3 Sectors (sectors.app, by Supertype)
- **Pages read:** https://docs.sectors.app/ (via WebFetch), https://docs.sectors.app/llms.txt, https://docs.sectors.app/idx-api.md and https://docs.sectors.app/api-references/v2/indonesia/brokers/broker-summary-by-symbol.md. `sectors.app/pricing` and `/api` returned **429**. **[Not loaded]** The pricing below comes from the docs landing page.
- **API style / auth:** REST at `https://api.sectors.app/v2/`. The API key goes in the `Authorization` header. There is also an MCP server. Billing is in credits: most calls cost 1, failed calls are free and 404s cost 1. **[Confirmed]**
- **Broker summary** **[Confirmed]**: `/v2/broker-summary/{symbol}/` returns per-broker **daily** rows grouped by date, with a maximum 14-day window per call.
  - Fields: `bfreq`, `blot`, `bval`, `bavg_per_share`, `sfreq`, `slot`, `sval`, `savg_per_share`, `nlot`, `nval`, `navg_per_share`.
  - **Per-broker foreign split** (`f_bfreq`, `f_blot`, `f_bval`, `f_bavg_per_share`, …) plus domestic average prices. This is the best schema found for accumulation analysis.
  - Also: broker activity by broker code, top buyers/sellers, broker registry (foreign/domestic origin, retail/institutional "cohort"), full-universe foreign flow per day.
  - History depth is **not stated**.
- **OHLCV** **[Confirmed]**: `/v2/daily/{symbol}/` returns OHLC, volume and market cap, with a 90-day window per call. `/v2/close/` gives the full-universe close for one date, and there are index daily endpoints (IHSG, LQ45, etc.). Adjustment and depth are not stated.
- **Corporate actions** (calendar and per symbol), quarterly financials and news are available. **[Confirmed]**
- **Pricing** **[Confirmed]** (docs landing page):
  - Forever Free: **no API access**.
  - Standard: US$49/month, no API.
  - **Insider: US$59/month or US$531/year, with API access and 5,000 credits/month.**
- **Sizing** **[Inferred]**: 5,000 credits is enough for about 1 broker call per 14 days for ~350 stocks, or daily coverage of a ~10 to 20 stock watchlist. It cannot cover the whole market daily. It is also the most expensive option here.
- **Terms:** not loaded (pricing/ToS pages were rate-limited).
- **Reliability signals:** high. Extensive OpenAPI docs, versioned API, established company, multi-market coverage.

### 4.4 Api.co.id
- **Page read:** https://api.co.id/api-saham-indonesia/
- **API style / auth:** REST at `https://use.api.co.id/idx/v2/...`. Auth is the header `x-api-co-id`. Responses carry `data.meta.as_of` and `stale_seconds`. 37 endpoints. **[Confirmed]**
- **Broker data** (Trader plan and up) **[Confirmed]**:
  - `broker-summary/{symbol}` is described as "Ringkasan broker pembeli dan penjual terbesar" (largest buying and selling brokers), so possibly top-N only rather than all brokers. **[Inferred]**
  - Also `broker-flow/{symbol}`, `broker-activity/{code}`, broker distribution, `foreign-flow/{symbol}`, top-foreign.
  - Fields and history depth are **not stated**.
- **OHLCV:** `chart/daily/{symbol}` (Free plan) returns daily, weekly and monthly history "for backtesting", with cursor pagination. Depth and adjustment are not stated. **[Confirmed]**
- **Pricing (IDR)** **[Confirmed]**:

  | Plan | Monthly | Annual | Hits/month | Rate limit | Notes |
  |---|---|---|---|---|---|
  | Free | Rp0 | Rp0 | 500 | 30/min | 12 reference endpoints |
  | Starter | Rp149.000 | Rp1.490.000 | 10,000 | 60/min | 22 endpoints |
  | **Trader** (needed for broker data) | **Rp399.000** | Rp3.990.000 | 40,000 | 120/min | 37 endpoints |
  | Business | Rp999.000 | Rp9.990.000 | 120,000 | 300/min | 99.5% SLA |
  | Enterprise | from Rp2.500.000 | Contract | Contract | Dedicated | Custom |

- **Terms:** caching and redistribution are not addressed on the page.
- **Reliability signals:** a generic API marketplace (also sells shipping, eKYC, prayer-time and LLM APIs). The data origin is not stated. Mid-priority.

### 4.5 TICMI / TICMIDATA
- **Pages read:**
  - https://doc-api.ticmidata.co.id/ (via its JSON at `/api/docs`, v1.1.08)
  - https://ticmidata.co.id/
  - https://www.ticmidata.co.id/pricing
- **Who:** The Indonesia Capital Market Institute, located in the IDX building (IDX-group training and data institute). **[Confirmed]**
- **API style / auth:** GET-only JSON at `https://api.ticmidata.co.id/direct/v1`. "Access credentials are issued by TICMI under your cooperation agreement." **[Confirmed]**
- **Broker summary** **[Confirmed]**: `/saham/bs/eq/?secCode=BBCA&startDate=...&endDate=...&type=all`.
  - Fields: `BrokerCode`, `InvestorType` (Domestic/Foreign), `TrxType` (RG...), `BuyVol`, `SellVol`, `BuyValue`, `SellValue`, `NetBuyVol`, `NetBuyValue`, `BuyerAvg`, `SellerAvg`.
  - Also: Investor Summary `/saham/is/eq/` (foreign/domestic buy/sell vol, value and freq), Top Broker `/saham/tb/eq/`, Stock `/saham/dp/eq/` (Date, OHLC, Volume, Value, Frequency, ChangePercent; daily/weekly/monthly), Index `/saham/dp/ix/`, intraday 5-min data, corporate actions (dividend, bonus, split/reverse split, rights, warrants, IPO, AGM/EGM), financial statements and key stats, news, insider activity.
  - Update time: "Data is generally updated at the end of the day."
- **History:** the site claims data "since 1977". Per-endpoint depth is not stated. Adjusted prices are not stated.
- **Pricing** **[Confirmed]**:
  - The individual **web** subscription is Rp150.000 (Value), Rp185.000 (Value Plus) or Rp250.000 (Pro) per month. 3-month tiers are Rp399.000 / Rp499.000 / Rp669.000.
  - These web plans list screener, documents and "Data Historis 5 Tahun" (5 years of historical data). **They do not list API access.**
  - The "API" banner links to WhatsApp sales. **API price is not published.**
  - The site also mentions "Pay Per Use" and "Bucket" (bulk download).
- **Terms** **[Confirmed]**: the API docs disclaimer says storage, display, processing, duplication and distribution "must be fulfilled by the Licensee in accordance with the license approved by TICMI in the Cooperation Agreement". Caching and display to friends must therefore be negotiated.
- **Reliability signals:** the highest of all candidates (institutional, IDX-affiliated, clean docs). The barrier is contract and price.

### 4.6 IDX official (idx.co.id / data.idx.co.id / idx.id)
- **data.idx.co.id and idx.co.id:** returned **403** (Cloudflare) to both WebFetch and curl. **[Not loaded]** I cannot confirm what is freely downloadable today, for example the stock summary or broker summary pages.
- **IDX Data Services catalogue**:
  - Page read: https://idx.id/en/products/idx-data-services
  - PDF read: https://www.idx.id/media/rzfl4wzy/20260513_idx-data-services-catalogue-pricelist-non-ab-2026.pdf (2026 non-exchange-member price list). The other 2026 PDF was corrupt.
  - Licensed **EOD** products **[Confirmed]**:
    - IDX Equity EoD (Professional/Basic): quotations, foreign ownership, "Most Active Share Bought by Foreign, **Top Brokers**", "Trading Summary By Investor Type".
    - IDX Equity EoD Indices and Indices Weight.
    - **IDX Equity EoD Recapitulation**: foreign/domestic buy/sell volume and value.
  - The PDF table text is garbled, so I can't map each price to each row reliably. Monthly fees visible in the EoD section range from about **Rp400.000 to Rp11.000.000 per product per month** (USD 40 to 2,000 for foreign users). Most main licences are Rp millions. The fees also apply under display/non-display licence categories. **[Confirmed numbers; row mapping inferred]**
  - Contact: idxdata@idx.co.id.
  - Non-display category notes say "*Free if sourced from redistributor" for some uses. **[Confirmed]**
- **Assessment** **[Inferred]**: the official feeds are designed for vendors, brokers and fintechs. They are far above budget and come with licensing overhead. They are not suitable for this app.

### 4.7 Yahoo Finance (unofficial)
- **Tested** with an anonymous request on 2026-10-26: `https://query1.finance.yahoo.com/v8/finance/chart/BBCA.JK?period1=...&interval=1d&events=div,splits`
  - Returned **5,367 daily bars from 2005-01-03 to today**, with no null closes.
  - Includes `adjclose`, a `dividends` and a `splits` event list (e.g. 2:1 in 2008 and 5:1 in 2021 for BBCA).
  - `^JKSE` (IHSG) daily also works (10y requested, a few null rows).
  - The current-day bar updates intraday.
  - Without explicit `period1/period2`, `range=max` silently returns **monthly** bars.
- **Status:** **unofficial and undocumented.** There is no API key or SLA, and it may rate-limit, change or break without notice. **[Inferred]**
- **Terms:**
  - The Yahoo API ToS (https://legal.yahoo.com/us/en/yahoo/terms/product-atos/apiforydn/index.html) bars selling or sublicensing the APIs and deriving income. It reserves rate limits at Yahoo's discretion. **[Confirmed]**
  - This chart endpoint is not a documented Yahoo developer API, and Yahoo's general terms prohibit redistributing content. **[Inferred]**
  - A personal app shared with a few people is a grey area. Keep it non-commercial, attribute the source and don't expose raw data dumps.
- **Data quality** **[Inferred]**: occasional bad ticks and missing days are known for small IDX stocks. Cross-check suspicious bars.

### 4.8 EODHD
- **Pages read:** https://eodhd.com/pricing. The exchange page (`eodhd.com/exchanges/JK`) returned 404 at that URL.
- **Search only:** a search summary of https://eodhd.com/exchange/JK says MIC XIDX, 962 active tickers, suffix `.JK`, and that access needs "All World Extended or higher". That last point is unclear, so verify it.
- **Pricing** **[Confirmed]**:

  | Plan | Monthly | Annual | Includes |
  |---|---|---|---|
  | Free | US$0 | US$0 | 20 API calls/day, EOD history 1 year |
  | EOD Historical All-World | US$19.99 | US$199/year | 100,000 calls/day, 30+ years EOD, dividends & splits |
  | EOD + Intraday | US$29.99 | US$299.90/year | |
  | Fundamentals | US$59.99 | | |
  | All-in-one | US$99.99 | | |

  - Bulk calls cost 100+ calls each.
  - The plans shown are **"Personal use"**. Commercial/B2B plans are separate.
- **Auth:** API token in the query string (standard EODHD). **[Inferred from general knowledge. Not re-verified.]**
- **Terms:** personal plans are for personal use. Whether serving data to a few friends counts as personal use must be asked. **[Inferred]**
- **Reliability:** an established vendor (Unicorn Data Services, Lyon, France). No broker data.

### 4.9 Others seen
- **GoAPI (goapi.io):** the homepage loaded but had no IDX stock details in the first 100k characters. A search found nothing on its IDX stock API. **Not verified.**
- **idxpro.id:** **shut down** in 2026 ("layanan dihentikan", service discontinued). **[Confirmed]** Useful as a reliability warning.
- **idx-cli** (github.com/0xrsydn/idx-cli): open-source CLI/MCP. **[Search only]** It reportedly pulls broker summary and foreign flow from public **IPOT (Indo Premier)** endpoints without a login. That means scraping a broker's app backend, which is likely against IPOT's terms and fragile. Not recommended as a backend source.
- **Brokerage apps** (Stockbit, Ajaib, IPOT): show broker summary to account holders but offer no public developer API. **[Inferred]**

---

## 5. Suggested architecture choices [Inferred]
1. **Nightly job after 19:30 WIB:**
   - Fetch broker summary per stock for the day (`from = to`).
   - Fetch foreign flow.
   - Fetch daily OHLCV for the full universe.
   - Upsert everything into PostgreSQL.
   - The frontend reads only from the database, never from providers. This keeps quota use flat no matter how many users there are.
2. **Abstract providers behind interfaces** (`BrokerSummaryProvider`, `PriceProvider`) so that you can swap Arjum ↔ Index Alpha ↔ TICMI, and Yahoo ↔ EODHD.
3. **Store raw prices plus split and dividend events.** Compute the adjusted series yourself. Don't rely on one provider's adjusted close.
4. **Store broker-summary volume in shares and lots explicitly.** Providers differ (Sectors uses `blot`, Index Alpha uses `buy_volume`).
5. **Backfill only a watchlist** for broker data at first. Broker history is short anyway: Index Alpha starts 2025-01-01, and the others don't state their depth.

---

## 6. Open questions to verify yourself (contact the provider)
1. **IDX Edge PRO:**
   - Where does the data come from?
   - How deep is broker-summary history?
   - Is OHLCV split-adjusted?
   - May you cache the data and show it to ~5 friends in a private app?
   - What does a +5,000 req/day top-up actually cost?
   - Ask via the dashboard or contact after signing in.
2. **Index Alpha:**
   - Does a caching/redistribution policy exist? There is no ToS page.
   - What is the Enterprise price for data before 2025-01-01?
   - Is `buy_volume` in shares or lots?
   - Contact: Telegram @neon_scale.
3. **Sectors:**
   - How deep is broker-summary and daily-price history?
   - Are there terms for a private multi-user app?
   - Is US$59 still the current API price? The pricing page was rate-limited.
4. **TICMI:**
   - What does an individual or personal API cooperation agreement cost?
   - Which endpoints and history depth would it include?
   - Would it allow display to a handful of non-paying users?
   - Contact: data@ticmi.co.id, WhatsApp +62 813-9843-4789.
5. **Api.co.id:**
   - Does `broker-summary` return all brokers or top-N only?
   - Does it include average prices and history?
   - What are the terms for storing data?
6. **EODHD:**
   - Is .JK EOD data included in the US$19.99 plan? The exchange page text mentioned "All World Extended".
   - How deep is JK history?
   - Does showing data to friends count as "personal use"?
7. **IDX:**
   - Does idx.co.id still publish a free daily stock summary and broker summary download? The page was blocked to automated requests. Check in a browser.
   - Does IDX's website ToS forbid automated retrieval?
8. **Policy:**
   - Has IDX re-opened broker codes intraday (even per session) in 2026? I found no confirmation. EOD broker summary is unaffected either way.

---

## 7. Sources actually read (2026-10-26)
- https://indexalpha.id/id/docs, https://indexalpha.id/docs/endpoints, https://indexalpha.id/docs/limits, https://indexalpha.id/pricing
- https://stock.arjum.com/api-docs (HTML plus JS bundle `/assets/index-B2Q8hrzB.js`)
- https://api.co.id/api-saham-indonesia/
- https://doc-api.ticmidata.co.id/ (plus `/api/docs` JSON), https://ticmidata.co.id/, https://www.ticmidata.co.id/pricing
- https://idx.id/en/products/idx-data-services, https://www.idx.id/media/rzfl4wzy/20260513_idx-data-services-catalogue-pricelist-non-ab-2026.pdf
- https://docs.sectors.app/, https://docs.sectors.app/llms.txt, https://docs.sectors.app/idx-api.md, https://docs.sectors.app/api-references/v2/indonesia/brokers/broker-summary-by-symbol.md, https://docs.sectors.app/api-references/v2/indonesia/transaction/daily.md
- https://eodhd.com/pricing
- https://idxpro.id/, https://idxpro.id/docs
- https://legal.yahoo.com/us/en/yahoo/terms/product-atos/apiforydn/index.html
- https://www.aseanexchanges.org/content/idx-increases-the-frequency-of-trading-summary-distribution-to-strengthen-investor-confidence/
- Yahoo chart endpoint test: `query1.finance.yahoo.com/v8/finance/chart/BBCA.JK` and `^JKSE`

**Could not load:**
- data.idx.co.id (403)
- idx.co.id broker summary page (403)
- sectors.app/pricing and /api (429)
- idnfinancials.com (403)
- market.bisnis.com (403)
- eodhd.com/exchanges/JK (404)
- second IDX 2026 PDF (corrupt)

**Search-engine summaries only:**
- Bisnis.com, IDX Channel and Katadata on the 6 Dec 2021 broker-code closure
- Bisnis.com 2022-06-25 on domicile hiding
- the EODHD JK exchange page
- idx-cli on GitHub
