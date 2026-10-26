# IDX Intelligence — Claude CLI Handoff

Last updated: 2026-10-26

Read this file before making changes. Inspect the existing repository first; do not recreate the project from scratch or overwrite working files without checking them.

## Goal

Build IDX Intelligence, a responsive Indonesian Stock Exchange (IDX) stock research and screening app for the developer, their father, and a few friends. Target hosting budget: Rp0/month where possible, using free tiers. End-of-day or delayed market data is acceptable.

This is a research/decision-support app, not an automated trading system or a promise of returns. Clearly label demo data, missing values, data timestamps, and delays.

## Chosen stack

- Frontend: Next.js App Router, React, TypeScript, Tailwind CSS.
- Backend: Java 21 + Spring Boot REST API.
- Database later: PostgreSQL, likely Neon Free.
- Frontend hosting later: Vercel Hobby, subject to its personal/non-commercial terms.
- Backend hosting later: Render Free is an option, but expect sleep/cold-start limitations and verify current terms.
- Git for version control; VS Code on Windows.
- Do not add PostgreSQL, authentication, Docker, or a real stock-data integration until the basic frontend/backend connection works.

## Environment already verified

Windows computer. These commands worked:
- `java -version`: Temurin OpenJDK 21.0.11 LTS
- `javac -version`: 21.0.11
- `node -v`: v22.20.0
- `npm -v`: 10.9.3
- `git --version`: 2.51.1.windows.1
- VS Code is installed. `code --version` returned no output, but the developer confirmed VS Code works. Do not reinstall these tools without a specific reason.

## Current local setup

The developer created `idx-intelligence` under their Windows user folder, initialized Git, and created `frontend`, `backend`, and `docs` directories.

- Next.js frontend was created with `npx create-next-app@latest frontend`; `npm run dev` works.
- Spring Boot project was generated using Spring Initializr and both apps reportedly run.
- The developer has not yet confirmed whether `HealthController` exists or whether frontend-to-backend communication works.
- Do not assume the earlier suggested code was saved. Inspect the repo first.

Expected local URLs:
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Planned health endpoint: `http://localhost:8080/api/health`

Likely frontend page: `frontend/src/app/page.tsx`, but inspect actual generated structure first.

Spring Initializr choices used/intended:
- Maven, Java, Jar
- Group: `com.idxintelligence`
- Artifact: `backend`
- Java: 21
- Dependency: Spring Web
- Spring Boot version: stable version selected at generation time

Use the actual package declaration from the main Spring Boot application class. Do not assume the package is `com.idxintelligence.backend`.

Expected structure:

```text
idx-intelligence/
├── frontend/
├── backend/
├── docs/
├── .gitignore
└── README.md
```

## Immediate milestone: connect frontend to backend

1. Inspect the repository structure, frontend `package.json`, backend `pom.xml`, main Java application class, and current page.
2. If no health controller exists, create `HealthController.java` in the same package as the main Spring Boot class:

```java
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "application", "IDX Intelligence",
            "message", "Backend is running"
        );
    }
}
```

Include imports for `GetMapping`, `RestController`, and `java.util.Map`.

3. Test `http://localhost:8080/api/health`. Expected JSON fields: `status`, `application`, `message`.
4. Connect the Next.js page to fetch `http://localhost:8080/api/health` and display the result. Inspect current page code before changing it.
5. If browser requests are blocked, configure CORS narrowly for local origin `http://localhost:3000`. Do not disable security broadly. Later, configure exact deployed origins.
6. Verify both URLs and troubleshoot real errors rather than assuming the cause.

Do not add database or stock-provider integration until this milestone works.

## Product roadmap

Planned features, in order:
1. Market overview.
2. Stock search/screener (gainers, losers, volume, momentum, basic filters).
3. Daily price charts and historical data.
4. Fundamentals.
5. News.
6. Broker summary/accumulation analysis only if a lawful and reliable data source supports it.
7. Backtesting later, with historical data and no look-ahead bias.

Start with clearly labelled mock/demo data. Never present mock prices as live, fabricate broker data, or imply broker accumulation guarantees price movement.

Potential future endpoints:
- `GET /api/health`
- `GET /api/market/overview`
- `GET /api/stocks?search=&sector=&sort=`
- `GET /api/stocks/{ticker}`
- `GET /api/stocks/{ticker}/prices?range=1M`

Use `BigDecimal` for financial values in Java. Use Asia/Jakarta timestamps consistently. Distinguish missing values from zero.

## Market data provider research

Possible candidates; none is confirmed as the final provider:
- IDX official data: https://data.idx.co.id/
- Index Alpha docs: https://indexalpha.id/id/docs
- IDX Edge PRO docs: https://stock.arjum.com/api-docs
- Api.co.id docs: https://api.co.id/api-saham-indonesia/
- TICMI data docs: https://doc-api.ticmidata.co.id/

Index Alpha was previously suggested as a candidate, but verify current docs, free request limits, coverage, update schedule, license, caching rights, and price before depending on it. Do not assume current prices/terms from this handoff.

Evaluate:
- IDX ticker coverage.
- Daily OHLCV history length.
- Update time, delay, and timezone.
- Fundamentals and broker-summary availability.
- Request limits.
- Whether displaying/caching data for a few other users is permitted.
- Commercial-use restrictions.

Keep all API keys and DB credentials in backend environment variables. Never expose them in frontend code or commit secrets. Include placeholder values in `.env.example`, and ensure `.env` files are ignored by Git.

## Future free-tier deployment

Target architecture:

```text
Browser / phone
      |
      v
Next.js frontend (Vercel)
      |
      v
Spring Boot API (Render or another suitable host)
      |                         \
      v                          v
PostgreSQL (Neon)          Market data provider
```

Free-tier limits and terms can change. Verify before deploying. Render Free may sleep and cold-start. Neon has quotas. Vercel Hobby is for personal/non-commercial use. There is no guarantee the full setup stays free forever. Avoid short-lived free databases for persistent data. Do not use fake uptime pings to bypass free-tier sleep rules.

For a few users and end-of-day data, cache results and refresh sensibly rather than calling the provider on every page view. Start with manual/on-demand refresh or a modest scheduled refresh only if provider terms and host capabilities allow it.

## Security and engineering rules

- Never expose market-data API keys or database credentials in browser code.
- Do not commit `.env`, `.env.local`, secrets, `node_modules`, `.next`, or Maven `target` artifacts.
- Use parameterized queries / Spring Data repositories.
- Add authentication only when needed; plan access controls before storing private user data.
- Clearly label demo data: `DEMO DATA — NOT LIVE MARKET DATA`.
- Show source and last-updated timestamp for real data.
- Do not claim guaranteed returns or automatic buy/sell advice.
- Keep the architecture simple and explain changes incrementally.

Check `.gitignore` before changing it. It should ignore at least `node_modules/`, `.next/`, `target/`, `.env`, and `.env.*`, while allowing `.env.example`.

## Developer working preferences

The developer has used Java/Spring Boot and web development, but has only used Firebase for databases and is new to PostgreSQL. Explain relational concepts practically and incrementally.

- Give one or two steps at a time, then ask the developer to test.
- Prefer exact Windows PowerShell or Command Prompt commands.
- Inspect files before editing.
- Avoid unnecessary dependencies.
- Do not create/deploy external resources without explicit approval.
- Keep the hosting budget at Rp0/month where feasible.
- Do not ask the developer to repeat details already in this file.

## First task for Claude CLI

Inspect the current repo and report:
1. Actual frontend/backend structure.
2. Whether `HealthController` exists.
3. Whether `/api/health` returns JSON.
4. Whether the frontend fetches the backend.
5. Any immediate errors/configuration issues.

Then implement only what is needed to make the health endpoint and frontend connection work. Do not add PostgreSQL or real stock APIs yet.
