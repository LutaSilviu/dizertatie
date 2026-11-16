# Web Accessibility Analyzer

A master's thesis project at **USV Suceava** that evaluates web page accessibility by comparing three analysis methods:

- **AXE** — rule-based analysis using Axe-Core 4.14 via Playwright
- **AI** — LLM-powered analysis using OpenAI GPT-6 Luna, GPT-4o, or GPT-4o-mini
- **COMBINED** — both methods merged with duplicate findings deduplicated

The system scores pages against **WCAG 2.2** criteria, supports benchmarking with precision/recall/F1 metrics, and provides a React dashboard for exploring results.

---

## Architecture

| Service | Technology | Port |
|---------|-----------|------|
| Database | PostgreSQL 16 | 5432 (loopback only; `POSTGRES_PORT`) |
| Backend | Spring Boot 4.x · Java 21 · Playwright · Spring AI | 8080 |
| Frontend | React 19 · Vite | 80 (`FRONTEND_PORT`) |

---

## Quick Start with Docker Compose

**Prerequisites:** Docker and Docker Compose installed.

```bash
cp .env.example .env
# Set OPENAI_API_KEY in .env to enable AI and COMBINED analysis.
docker compose up --build
```

| URL | Description |
|-----|-------------|
| http://localhost | React frontend |
| http://localhost:8080 | Backend REST API |
| http://localhost:8080/swagger-ui.html | Swagger UI (interactive API docs) |

If port 80 is already in use (on Windows, IIS or another service often holds it), set
`FRONTEND_PORT=8088` in `.env` and open http://localhost:8088 instead.

### If the image build fails with a certificate error

`PKIX path building failed` or `unable to verify the first certificate` during
`docker compose up --build` means something on the machine is inspecting HTTPS traffic
(an antivirus "web shield" or a corporate proxy) and the build containers do not trust its
certificate. Pause HTTPS scanning for the duration of the build, or run the services outside
Docker as described under [Development Setup](#development-setup).

---

## Environment Variables

Copy `.env.example` to `.env` before starting the stack. Compose has local-only fallbacks
for the database password and API key, so AXE analysis and the dashboard can start without
an OpenAI key. AI and COMBINED analysis require a valid `OPENAI_API_KEY`.

| Variable | Description | Example |
|----------|-------------|---------|
| `POSTGRES_DB` | PostgreSQL database name | `accessibility` |
| `POSTGRES_USER` | PostgreSQL username | `accessibility` |
| `POSTGRES_PASSWORD` | PostgreSQL password (use a private value outside local development) | `changeme` |
| `APP_API_KEY` | API key required by backend endpoints (local fallback: `local-development-only`) | `my-secret-key` |
| `OPENAI_API_KEY` | OpenAI API key required for AI and COMBINED analysis | `sk-...` |
| `FRONTEND_PORT` | Optional. Host port for the frontend (default `80`) | `8088` |
| `POSTGRES_PORT` | Optional. Host port for the database, bound to 127.0.0.1 (default `5432`) | `5433` |

The Settings page shows whether the configured OpenAI key is missing, rejected or working.
While it is missing, AI and COMBINED scans are refused with an explanation and AXE keeps working.

### If PostgreSQL rejects the password after it has already started

PostgreSQL applies `POSTGRES_PASSWORD` only when it initializes an empty data volume.
Changing `.env` later does not change the password stored in the existing database volume.
To keep the data, open a local database session and set the role password to match `.env`:

```bash
docker compose exec db psql -U dizertatie -d dizertatie
```

At the `psql` prompt, run `\password dizertatie`, enter the value of `POSTGRES_PASSWORD`,
then exit with `\q` and restart the backend with `docker compose restart backend`.
If `POSTGRES_USER` or `POSTGRES_DB` were customized, use those values in the `psql` command.

---

## API Authentication

All API endpoints (except `/health`) require the `X-API-Key` header:

```
X-API-Key: <your APP_API_KEY value>
```

The Docker frontend proxy reads `APP_API_KEY` from `.env` and adds it to API requests on the server side. You do not need to paste it into the browser. Direct requests to port 8080 must still include the header above.

---

## Analysis Methods

### AXE

Rule-based analysis powered by **Axe-Core 4.14** running inside a headless Chromium browser via **Playwright**. Produces a list of WCAG violations with impact levels (critical, serious, moderate, minor) and affected element selectors.

### AI

The page HTML and screenshot are sent to **GPT-6 Luna** by default. **GPT-4o** and **GPT-4o-mini** are also available in Settings. The model identifies accessibility issues based on WCAG 2.2 guidelines and returns structured findings. Useful for catching semantic and contextual issues that rule-based engines miss.

### COMBINED

Runs both AXE and AI analysis then merges the results. Duplicate findings (same WCAG criterion, same element) are deduplicated so each issue is reported once. Provides the broadest coverage.

---

## Scan report evidence

A completed report shows:

- the engine that produced it: axe-core version and browser version, and the AI model for AI and COMBINED scans;
- reported elements grouped by impact and by source (axe-core only, AI only, or both), counting every finding in the table below them;
- the number of distinct axe-core rules with findings (AI findings are not tied to a rule);
- the WCAG criteria associated with findings;
- axe-core checks that need manual review, listed separately from violations.

The report does not calculate a composite severity score or WCAG pass percentages; absence of automated findings does not establish that a criterion passes.

A scan that could not produce evidence is stored with a status instead of an empty result: `BLOCKED` (bot challenge, HTTP 401/403), `RATE_LIMITED` (HTTP 429), `TIMED_OUT`, or `FAILED` (anything else, including an AI reply that is not the expected JSON). Such a scan shows the reason and no findings, so it cannot be mistaken for a clean page.

---

## Benchmark

The benchmark feature compares analysis methods against **ground-truth accessibility labels**. The [W3C BAD (Before and After Demonstration)](https://www.w3.org/WAI/demos/bad/) pages are used as the reference dataset.

Metrics computed per method:

| Metric | Description |
|--------|-------------|
| **Precision** | Of all reported issues, the fraction that are true violations |
| **Recall** | Of all true violations, the fraction correctly detected |
| **F1** | Harmonic mean of precision and recall |

A run can use the built-in dataset or your own URLs, and any subset of the three methods. Methods are isolated from each other: if AI is unavailable, AXE results are still produced and the run's error summary says what failed.

Counting rules (details in [ADR 0001](docs/adr/0001-benchmark-counting-rules.md)):

- a finding matches a label when both have the same WCAG criterion and element selector;
- several findings on the same criterion and element count once;
- a page whose scan did not complete for a method is left out of that method's counts rather than scored as all false negatives.

Results can be exported as CSV for further analysis. The dataset and how its labels were derived are described in [DATASET.md](DATASET.md).

---

## Development Setup

> These steps are only needed if you want to run services outside Docker.
>
> **Requirements:** Java 21, Node 22, and a PostgreSQL 16 database. The simplest database is
> the Compose one: `docker compose up -d db` publishes it on `127.0.0.1:5432`.

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

The backend reads the project's `.env` file directly, so it uses the same database credentials, `APP_API_KEY` and `OPENAI_API_KEY` as Docker Compose; without a `.env` it falls back to the Compose defaults. Environment variables take precedence, and `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` override the database settings entirely. The first run downloads the Playwright browsers.

```bash
./mvnw test   # backend test suite (uses an in-memory H2 database)
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server starts on `http://localhost:5173` by default and proxies `/api` calls to `http://localhost:8080`, adding the `APP_API_KEY` from `.env`.
