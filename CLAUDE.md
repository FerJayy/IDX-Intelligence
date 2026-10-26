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
- Backend `market` package: `MarketDataProvider` interface (currently `MockMarketDataProvider`, hard-coded demo quotes) → `MarketService` (rankings, breadth, Jakarta `generatedAt`) → `MarketController` (`GET /api/market/overview`). A real provider should implement `MarketDataProvider`; nothing above it should change.
- Frontend fetches from **Server Components** via `src/lib/api.ts` using server-only `API_BASE_URL` (default `http://localhost:8080`; see `frontend/.env.example`). With `cacheComponents` on, uncached fetching components must sit inside `<Suspense>` (page is Partial Prerender). Catch blocks around fetches call `unstable_rethrow` first.
- Endpoints: `/api/market/overview`, `/api/stocks/{ticker}`, `/api/stocks/{ticker}/prices?range=1M|3M|6M|1Y`. Demo price history (`DemoPriceGenerator`) is deterministic per ticker/day, respects IDX tick sizes, and ends exactly at the quote's previous close / last price so chart and dashboard agree.
- Backend `broker` package: `BrokerDataProvider` is separate from `MarketDataProvider` (broker data may come from a different vendor). `BrokerAnalysisService` turns raw per-broker-per-day rows into top buyers/sellers, daily flow (top-5 concentration, buyer/seller counts, foreign-broker net) and a scored `FlowAssessment` with human-readable reasons; thresholds are untested heuristics until step 5 backtesting. `GET /api/stocks/{ticker}/brokers?range=`.
- `DemoBrokerGenerator` invariants (tested): daily buy lots = sell lots = bar volume, avg prices within bar high/low, whole lots. Demo flows must stay **non-predictive** (same-day reactions only) so future backtests on demo data can't look falsely good. Demo uses broker codes only, no firm names.
- Stock page `src/app/stocks/[ticker]/page.tsx`: params/searchParams are runtime data, read inside `<Suspense>`. Chart is the only client component (`components/price-chart.tsx`, lightweight-charts v5): hollow up / filled down candles, volume in a separate pane (never a second axis).
- Database: Neon PostgreSQL 18 via Spring `JdbcClient` (plain SQL, no JPA) + Flyway migrations in `backend/src/main/resources/db/migration` (never edit an applied migration; add `V<n+1>__*.sql`). Credentials come from git-ignored `backend/.env` (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, imported via `spring.config.import`); never read or print its values. DB tests extend `EmbeddedPostgresTest` (zonky embedded PG 18, no Docker), which overrides `.env` so tests never hit Neon. `@WebMvcTest` slices need no DB.
- TS types in `src/lib/api.ts` mirror the Java records by hand — update both together. Formatting helpers (`src/lib/format.ts`) use `id-ID` locale and render `null` as `—`.
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
