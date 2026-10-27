# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

IDX Intelligence: Indonesian Stock Exchange (IDX) stock research app for a handful of personal users. Core goal is beyond screening: broker-activity analysis (accumulation/distribution) and explainable signals for stocks likely to gain. Decision support only — not a trading system; signals must show their reasons and never be framed as guaranteed or as buy/sell orders. Hosting budget target Rp0/month (free tiers). End-of-day/delayed data is acceptable.

Full context, roadmap, data-provider candidates, and deployment plan live in `docs/IDX_INTELLIGENCE_CLAUDE_HANDOFF.md` — read it before non-trivial work.

## Layout

Two independent apps, no root build tooling:

- `frontend/` — Next.js 16 (App Router, `src/app/`), React 19, TypeScript, Tailwind v4 (loaded via `@tailwindcss/turbopack` rule in `next.config.ts`; `cacheComponents` and `partialPrefetching` enabled).
- `backend/` — Spring Boot 4.1 (`spring-boot-starter-webmvc`), Java 21, Maven wrapper. Package `com.idxintelligence.backend`.
- `docs/` — handoff/planning docs.

Data flow:
- Data source switch: `app.data-source` = `database` (default: `DatabaseMarketDataProvider` + `DatabaseBrokerDataProvider`, reading the imported tables) or `demo` (`MockMarketDataProvider` + `MockBrokerDataProvider`). `@WebMvcTest`s set `properties = "app.data-source=demo"`. Responses carry `dataDate` (real trading day; null for demo). DB quotes whose latest bar is older than the market's latest day keep the price but drop change/volume (`StockQuote.withoutDailyChange`). Broker analysis reports coverage (`brokerDays`/`tradingDays`), needs ≥5 days for an assessment, and treats unknown foreign flow as null (never summed as 0).
- Backend `market` package: `MarketDataProvider` interface (database or demo implementation) → `MarketService` (rankings, breadth, Jakarta `generatedAt`) → `MarketController` (`GET /api/market/overview`). A real provider should implement `MarketDataProvider`; nothing above it should change.
- Frontend fetches from **Server Components** via `src/lib/api.ts` using server-only `API_BASE_URL` (default `http://localhost:8080`; see `frontend/.env.example`). With `cacheComponents` on, uncached fetching components must sit inside `<Suspense>` (page is Partial Prerender). Catch blocks around fetches call `unstable_rethrow` first.
- Endpoints: `/api/market/overview`, `/api/stocks/{ticker}`, `/api/stocks/{ticker}/prices?range=1M|3M|6M|1Y`. Demo price history (`DemoPriceGenerator`) is deterministic per ticker/day, respects IDX tick sizes, and ends exactly at the quote's previous close / last price so chart and dashboard agree.
- Backend `broker` package: `BrokerDataProvider` is separate from `MarketDataProvider` (broker data may come from a different vendor). `BrokerAnalysisService` turns raw per-broker-per-day rows into top buyers/sellers, daily flow (top-5 concentration, buyer/seller counts, foreign-broker net) and a scored `FlowAssessment` with human-readable reasons; thresholds are untested heuristics until step 5 backtesting. `GET /api/stocks/{ticker}/brokers?range=`.
- `DemoBrokerGenerator` invariants (tested): daily buy lots = sell lots = bar volume, avg prices within bar high/low, whole lots. Demo flows must stay **non-predictive** (same-day reactions only) so future backtests on demo data can't look falsely good. Demo uses broker codes only, no firm names.
- Stock page `src/app/stocks/[ticker]/page.tsx`: params/searchParams are runtime data, read inside `<Suspense>`. Chart is the only client component (`components/price-chart.tsx`, lightweight-charts v5): hollow up / filled down candles, volume in a separate pane (never a second axis).
- Database: Neon PostgreSQL 18 via Spring `JdbcClient` (plain SQL, no JPA) + Flyway migrations in `backend/src/main/resources/db/migration` (never edit an applied migration; add `V<n+1>__*.sql`). Credentials come from git-ignored `backend/.env` (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, imported via `spring.config.import`); never read or print its values. DB tests extend `EmbeddedPostgresTest` (zonky embedded PG 18, no Docker), which overrides `.env` so tests never hit Neon. `@WebMvcTest` slices need no DB.
- Backend `ingest` package: `YahooFinanceClient` (unofficial chart API; needs a User-Agent or Yahoo returns 429) → `PriceImportService` → `PriceRepository` upsert into `daily_price` (`ON CONFLICT DO UPDATE`). Backfills 10y on first run, then re-fetches the last 7 days; skips today's bar before 17:00 WIB and zero-volume placeholder rows; per-stock failures don't abort the run; runs logged in `import_run`. Triggered by `@Scheduled` (weekdays 17:00 WIB) or `POST /api/admin/import/prices`. Prices are imported for every `stock` row with `active = true` (~960 after the listing sync; new stocks backfill 5 years, the original 34 have 10); Yahoo 429s are retried with a wait.
- Stock universe: `StockListingService` syncs `stock` from IDX Edge `/api/market-cap` (50/page, ~20 requests, weekly Sun 09:00 WIB or `POST /api/admin/import/stocks`); only 4-letter codes; existing names kept, new names lose the `Tbk.` suffix; stocks missing from a *complete* listing become `active = false`. Sync times are truncated to milliseconds before comparing with PostgreSQL timestamps (microsecond precision) — otherwise every row looks stale and gets deactivated. No sectors from this source (NULL = unknown).
- Broker tracking: `BrokerTrackingService` adds stocks (`broker_tracked_reason` CORE | SCREENER | USER) with caps (80 total, 5/day from the screener, 3/day from users) and a short history window (`broker_backfill_from` = today − 35 days; NULL for the original 34 = the import's 365-day default). `POST /api/stocks/{ticker}/track` (public, capped) tracks and immediately fetches up to 60 requests in the background (`applicationTaskExecutor`); frontend calls it via the `loadBrokerData` Server Action (`src/lib/actions.ts`). `ScreenerService` stores IDX Edge `/api/screener/latest` picks in `screener_pick` (their win rates are *claims*; keep picks to measure outcomes) and auto-tracks them; weekdays 19:15 WIB or `POST /api/admin/import/screener`.
- Live prices: `LivePriceService` uses IDX Edge `/api/price/{code}` (real-time running trade) only 08:55–16:15 WIB weekdays, caches 60 s per stock (shared by all viewers) and has its own daily budget (150) separate from broker imports. `GET /api/stocks/{ticker}/live`; the browser polls the Next Route Handler `src/app/api/live/[ticker]/route.ts` every minute while visible, stopping after 15 min. IDX Edge records returned to our frontend use `@JsonAlias` (not `@JsonProperty`) so our JSON stays camelCase.
- Broker import button fetches may use the quota down to `maintenanceReserve()` (2 × tracked stocks + 20); the last quota reading of the day is remembered, and the track endpoint returns `loadingNow=false` (frontend shows QUEUED) when too little is left. Extra broker run at 00:30 WIB. IDX Edge's remaining counter has been seen to *rise* during the day — the quota may be a rolling window; verify before relying on a midnight reset.
- Broker Radar (`signal.RadarService`, `GET /api/watchlist/radar?window=5|10|20`): per tracked stock over its last N broker days — accumulation streak (signed consecutive days of top-5 net buying/selling), longest single-broker net-buying run, avg concentration, foreign net, above-rising-MA20, price vs top buyers' avg cost (exit gap); score from `RADAR_*` reason codes; illiquid/limit-up/chasing rows listed separately. Also measures `screener_pick` outcomes (+1/+3/+5 days close-to-close, vs IHSG). Cached per data version; first load ~10 s from Neon. Scores are untested heuristics.
- Broker profile: `BrokerProfileService` → `GET /api/brokers` (brokers active in the last 30 days) and `GET /api/brokers/{code}?window=10|20|60` (window = last N days with broker data; per stock: net, avg buy/sell, gap of last close vs avg buy for net buyers / avg sell for net sellers, buy streak over that stock's broker days, foreign net null unless every day known). Pages `[lang]/brokers` and `[lang]/brokers/[code]`; broker codes on stock pages and Radar streaks link there.
- My watchlist: per-browser `watchlist` cookie (comma-separated 4-letter codes, max 30, httpOnly; `src/lib/watchlist.ts`), changed only by the `toggleWatch` / `addToWatchlist` Server Actions (`src/lib/actions.ts`). `[lang]/watchlist` shows quotes first and streams each stock's 10-day Radar row; stock pages get a star button (`components/watch-button.tsx`). No backend storage or accounts.
- Yahoo 404s set `stock.price_missing_since`; such stocks are skipped by daily price imports and retried every 7 days.
- Charts: `GET /api/stocks/{ticker}/candles` and `/api/index/COMPOSITE/candles?interval=15m|1h|4h|1d|1w|1mo&range=` (`CandleService`, `ChartInterval`). Intraday comes from Yahoo on request (not stored; 10-minute in-memory cache); 4h = morning/afternoon IDX session buckets; 1w/1mo and ranges ≥2Y are split/dividend adjusted. Frontend passes `?tf=&range=`; the chart shifts intraday epoch times by +7 h so the axis reads WIB.
- `stock.broker_tracked` (true for the original 34) limits broker imports, watchlist broker signals and the backtest universe; untracked stocks show a "not tracked" note instead of broker analysis. `GET /api/stocks?search=&sector=&sort=marketCap|change|value|volume|price|ticker&order=&page=&size=` lists all active stocks (filter/sort in Java over `quotes()`, nulls last). Top gainers/losers require ≥ Rp1 miliar traded that day (`MarketService.MIN_MOVER_VALUE`).
- Broker import: `IdxEdgeClient` (IDX Edge PRO, `X-API-Key` = `IDX_EDGE_API_KEY`; 1 request = 1 stock-day; a date range returns one aggregate, not daily rows; `flow=F` = foreign *investors* per broker) → `BrokerImportService` → `BrokerImportRepository`. Pending work = trading days in `daily_price` minus `broker_import_day`, newest first, ALL before F (foreign figures are UPDATEs onto ALL rows; NULL foreign = not fetched, 0 = none). Stops at quota reserve (reads `x-ratelimit-remaining`), on 429, or after 5 consecutive failures. Empty summary for a traded day = not published yet: left pending. Daily 19:30 WIB or `POST /api/admin/import/brokers`.
- All `/api/admin/*` endpoints go through `AdminAuth` (`X-Admin-Token` = `ADMIN_TOKEN`; 403 when unset). `GET /api/admin/stats` shows table sizes (Neon free tier = 512 MB; ~100 MB per year of broker data for 34 stocks). Helper scripts in `backend/scripts/` (`import.ps1 prices|brokers|stats`, `set-secret.ps1`, `neon-to-env.ps1`); with Windows PowerShell 5.1, don't use `$PSScriptRoot` in `param()` defaults.
- Running a second backend for checks: set `$env:SERVER_PORT` (spring-boot:run `-Dspring-boot.run.arguments` doesn't split multiple args).
- Backend `signal` package (watchlist + backtest): `Features.at(bars, i)` uses only bars `<= i` (tested); `MomentumScorer` turns features (+ optional `BrokerSignal`) into points with reasons and exclusion filters; `BacktestService` replays it on split/dividend-adjusted history (`adjusted_close / close` factor), entry `NEXT_OPEN` or `SIGNAL_CLOSE`, 0,4% round-trip cost, excess vs IHSG, baseline = all stocks. `GET /api/watchlist`, `GET /api/watchlist/backtest?entry=`. Results cached per latest price date (loading 10y from Neon takes ~20 s).
- Backtest findings (2026-10-26, 34 large caps, 10y): the breakout/volume rules show **no edge** after costs for next-day trades (top-5 picks ≈ -0,5% vs IHSG per trade, ~35% hit rate); only score ≥70 over 5 days is slightly positive on average (+0,2–0,45%) with <50% hit rate. Don't present the watchlist as picks without these numbers.
- **i18n (en / id):** every route lives under `src/app/[lang]/` (root layout there, `generateStaticParams` for both locales). `src/proxy.ts` redirects URLs without a prefix using the `NEXT_LOCALE` cookie, then `Accept-Language`, then `en`. Server Components call `getI18n()` (`src/lib/i18n`, reads `next/root-params`) for `{ locale, t, f }`; Client Components get `locale` and plain-string labels as props (never import `@/lib/i18n` index there). `en.ts` defines the dictionary shape; `id.ts` must satisfy it, so a missing translation fails the build. Numbers/dates format per locale via `makeFormat(locale)` (`id-ID` / `en-US`).
- **Backend text is language-neutral:** explanations are `Reason { code, values, points }` (`backend/.../Reason.java`); the frontend renders them with `describe()` and `t.reasons[code]`. Add a new reason code to both `en.ts` and `id.ts`. Don't build sentences in Java.
- **Theme:** colours are semantic tokens in `globals.css` (`bg-page`, `bg-card`, `border-line`, `text-fg/muted/faint`, `text-up/down`, `*-warn/info/danger/up/down-*`); `.dark` on `<html>` swaps them. No theme script (React 19 warns about <script> in components, even via next/script): CSS follows `prefers-color-scheme` unless <html> has `.light`, and `ThemeSwitch` applies the stored choice (`localStorage.theme` = light|dark|system) after mount, so only visitors overriding their device see a brief flash on full reloads. Keep the dark token block and its `@media` copy in sync. Charts read `--chart-*` variables and redraw on theme change. Don't use raw Tailwind colours like `slate-900` in components.
- Client components must not read URL hooks (`usePathname`, `useSearchParams`) during render on dynamic routes without a `<Suspense>` boundary (build fails with cacheComponents); read `window.location` in event handlers instead.
- TS types in `src/lib/api.ts` mirror the Java records by hand — update both together. Formatting helpers (`src/lib/format.ts`) use `id-ID` locale and render `null` as `—`.
- Hosting: backend runs on Render Free from `render.yaml` (Docker, Singapore) via `backend/Dockerfile` (extracted jar + class-data-sharing archive from a DB-less training start, Serial GC, C1 JIT: ~70 s cold start at 0.1 CPU instead of ~200 s; ~120–230 MB RAM). `server.port=${PORT:8080}`. Render rejected the developer's BCA debit card (2026-10-28), so hosting is pending. `.github/workflows/imports.yml` runs the imports on GitHub's schedule: without a `BACKEND_URL` secret it builds and starts the backend inside the runner (secrets `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`IDX_EDGE_API_KEY`, throw-away admin token, crons `-`); with `BACKEND_URL` (+ `ADMIN_TOKEN`) it calls the hosted backend instead (admin imports are synchronous, which keeps a sleeping instance awake). The repo is public, so the workflow never prints the backend log (it names the Neon host). Keep crons `-` wherever the workflow runs imports, or both runs spend IDX Edge quota. Test the image locally with `docker run --memory=512m --cpus=0.1 --env-file backend/.env -e PORT=10000 -p 8082:10000 idx-backend`.
- CORS (`CorsConfig.java`) allows only `http://localhost:3000` on `/api/**`; server-side fetches don't need it. Keep it narrow; add exact deployed origins later.

## Commands

Windows dev machine; prefer PowerShell commands when giving instructions to the developer.

Frontend (from `frontend/`):
```
npm run dev      # http://localhost:3000
npm run build
npm run lint
```

Backend (from `backend/`):
```
.\mvnw.cmd spring-boot:run                              # http://localhost:8080, health at /api/health
.\mvnw.cmd test
.\mvnw.cmd test -Dtest=BackendApplicationTests          # single test class
.\mvnw.cmd test -Dtest=ClassName#methodName             # single test method
```

## Next.js version warning (from `frontend/AGENTS.md`)

This Next.js version has breaking changes vs. training data — APIs, conventions, and file structure may differ. Read the relevant guide in `frontend/node_modules/next/dist/docs/` before writing frontend code, and heed deprecation notices. `next dev` re-generates `frontend/AGENTS.md`; don't fight that diff.

## Rules from the handoff doc

- Scope discipline: don't add PostgreSQL, auth, Docker, or a real stock-data integration until asked; the current milestone is the frontend↔backend health connection. Avoid unnecessary dependencies.
- Planned API shape: `/api/market/overview`, `/api/stocks?search=&sector=&sort=`, `/api/stocks/{ticker}`, `/api/stocks/{ticker}/prices?range=1M`.
- Financial values: `BigDecimal` in Java. Timestamps: Asia/Jakarta consistently. Distinguish missing values from zero.
- Mock data must be labelled `DEMO DATA — NOT LIVE MARKET DATA`. Real data must show source and last-updated timestamp. Never fabricate broker data or imply guaranteed returns / buy-sell advice.
- API keys and DB credentials live only in backend env vars; provide `.env.example` placeholders. Never expose them in frontend code.
- Planned hosting: Vercel Hobby (frontend), Render Free (backend), Neon (Postgres). Don't create/deploy external resources without explicit approval; don't use fake uptime pings to dodge free-tier sleep.
- Cache provider responses; don't hit the provider per page view.

## Working with the developer

- Experienced with Java/Spring and web dev; new to PostgreSQL (used Firebase) — explain relational concepts practically.
- Give one or two steps at a time, then let them test.
- Inspect files before editing; don't overwrite working code.
