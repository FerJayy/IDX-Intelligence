# IDX Intelligence

Research and analysis app for stocks on the Indonesian Stock Exchange (IDX), built for personal use by a small group.

> **Status:** development. The app shows real end-of-day data: prices from Yahoo Finance and broker summaries from IDX Edge PRO, imported into PostgreSQL (see *Importing real data*). Broker history fills in gradually; the stock page says how many days are covered.
>
> To run with invented demo data instead (no database data needed), start the backend with `app.data-source=demo`, e.g. `$env:APP_DATA_SOURCE = "demo"` before `.\mvnw.cmd spring-boot:run`. Demo data is always labelled **DEMO DATA — NOT LIVE MARKET DATA**.
>
> This is a research and decision-support tool. It does not give investment advice or guarantee returns.

## Stack

| Part | Technology | Folder |
|------|------------|--------|
| Frontend | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4 | `frontend/` |
| Backend | Java 21, Spring Boot 4, Maven | `backend/` |
| Docs | Project handoff and planning | `docs/` |

Planned later: PostgreSQL (Neon), frontend on Vercel, backend on Render. Target hosting cost: Rp0/month on free tiers.

## Prerequisites

- Java 21 (Temurin)
- Node.js 22 and npm
- Git

Maven is not needed; the backend includes the Maven wrapper (`mvnw.cmd`).

## Database setup (first time)

The backend uses PostgreSQL on [Neon](https://neon.tech) (free tier, Singapore region). Tables are created automatically by Flyway from `backend/src/main/resources/db/migration/` on startup.

1. In Neon: project → **Connect**, turn **off** connection pooling, copy the `postgresql://...` connection string.
2. Create `backend/.env` from it:
   ```powershell
   cd backend
   powershell -ExecutionPolicy Bypass -File scripts\neon-to-env.ps1
   ```
   Paste the string when asked. (Or copy `.env.example` to `.env` and fill it in by hand.) `.env` is git-ignored — never commit it.

The backend will not start without `backend/.env` (or `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables). Tests do **not** use Neon: they start their own embedded PostgreSQL (first run downloads it, a few minutes).

## Importing real data

| Data | Source | Table | Schedule (while the backend runs) |
|------|--------|-------|------------------------------------|
| Daily prices | Yahoo Finance (unofficial endpoint, no key; personal non-commercial use) | `daily_price` | Weekdays 17:00 WIB |
| Broker summaries (all investors + foreign) | IDX Edge PRO (`IDX_EDGE_API_KEY` in `backend/.env`) | `broker_daily_activity` | Daily 19:30 WIB |

Every stock in the `stock` table is imported. Re-running any import is always safe.

**Manual runs** (backend running, from `backend/`):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\import.ps1 prices    # ~2 min first time (10-year backfill), ~30 s after
powershell -ExecutionPolicy Bypass -File scripts\import.ps1 brokers   # up to 800 requests, ~15 min
powershell -ExecutionPolicy Bypass -File scripts\import.ps1 stats     # row counts and storage vs Neon's 512 MB
```

The first time, `import.ps1` adds a random `ADMIN_TOKEN` to `backend/.env`; restart the backend and run it again.

**Broker quota:** IDX Edge's free tier allows 1,000 requests/day (reset 00:00 WIB), and one request = one stock on one day. Each run fetches newest days first, then extends history backwards (default: 1 year), and stops when 150 requests are left. A full 1-year backfill for 34 stocks takes about 20 daily runs. Store a new secret in `.env` without it appearing on screen with `scripts\set-secret.ps1 NAME`.

Each run is logged in the `import_run` table.

## Running locally

Run the backend and frontend in two terminals (PowerShell).

**Backend** — http://localhost:8080

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**Frontend** — http://localhost:3000

```powershell
cd frontend
npm install        # first time only
npm run dev
```

Open http://localhost:3000. If the backend is not running, the dashboard shows "Cannot load market data".

### Configuration

The frontend reads the backend address from `API_BASE_URL` (server-side only), defaulting to `http://localhost:8080`. To override it, copy `frontend/.env.example` to `frontend/.env.local` and edit it. Never commit `.env` or `.env.local` files.

## Tests and checks

**Backend**

```powershell
cd backend
.\mvnw.cmd test                                   # all tests
.\mvnw.cmd test -Dtest=MarketControllerTests      # one test class
.\mvnw.cmd test -Dtest=MarketControllerTests#overviewIsLabelledAsDemoData   # one test method
```

**Frontend**

```powershell
cd frontend
npm run lint
npm run build      # production build; also type-checks
```

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Backend status |
| GET | `/api/market/overview` | IHSG, market breadth, top gainers / losers / most active |
| GET | `/api/stocks/{ticker}` | Latest quote for one stock |
| GET | `/api/stocks/{ticker}/prices?range=3M` | Daily OHLCV history; `range` is `1M`, `3M`, `6M` or `1Y` |
| GET | `/api/stocks/{ticker}/brokers?range=3M` | Broker summary analysis: top net buyers/sellers, daily flow metrics, accumulation/distribution assessment with reasons, and coverage (`brokerDays` of `tradingDays`) |
| POST | `/api/admin/import/prices`, `/api/admin/import/brokers` | Run an import (needs `X-Admin-Token`) |
| GET | `/api/admin/stats` | Table sizes (needs `X-Admin-Token`) |

Every response says where its data comes from (`dataSource`) and, for real data, which trading day it is from (`dataDate`). Demo responses have `"demo": true` and a `notice`. Missing values are `null`, never `0`. Timestamps are in Asia/Jakarta time (`+07:00`).

## Roadmap

1. Market overview dashboard — done
2. Stock detail page with daily price chart — done
3. Real data: Yahoo prices + IDX Edge PRO broker summaries in PostgreSQL — done (broker history backfilling)
4. Broker activity analysis (accumulation / distribution) — done, on real data
5. Signals with explanations, then backtesting (historical data, no look-ahead bias)
6. Stock list / screener, fundamentals, news

See `docs/IDX_INTELLIGENCE_CLAUDE_HANDOFF.md` for full project context.
