# IDX Intelligence

Research and analysis app for stocks on the Indonesian Stock Exchange (IDX), built for personal use by a small group.

> **Status:** development. The dashboard currently shows **DEMO DATA — NOT LIVE MARKET DATA**. All prices and figures are invented.
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
| GET | `/api/market/overview` | IHSG, market breadth, top gainers / losers / most active (demo data) |
| GET | `/api/stocks/{ticker}` | Latest quote for one stock (demo data) |
| GET | `/api/stocks/{ticker}/prices?range=3M` | Daily OHLCV history; `range` is `1M`, `3M`, `6M` or `1Y` (demo data) |
| GET | `/api/stocks/{ticker}/brokers?range=3M` | Broker summary analysis: top net buyers/sellers, daily flow metrics, accumulation/distribution assessment with reasons (demo data) |

Responses that use demo data include `"demo": true` and a `notice` field. Timestamps are in Asia/Jakarta time (`+07:00`).

## Roadmap

1. Market overview dashboard — done (demo data)
2. Stock detail page with daily price chart — done (demo data)
3. Choose real data providers (broker summary + price history)
4. Broker activity analysis (accumulation / distribution) — done with demo data; real data pending step 3
5. Signals with explanations, then backtesting (historical data, no look-ahead bias)
6. Stock list / screener, fundamentals, news

See `docs/IDX_INTELLIGENCE_CLAUDE_HANDOFF.md` for full project context.
