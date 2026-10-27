# Web Accessibility Analyzer

A master's thesis project at **USV Suceava** that evaluates web page accessibility by comparing three analysis methods:

- **AXE** — rule-based analysis using Axe-Core 4.9 via Playwright
- **AI** — LLM-powered analysis using OpenAI GPT-6 Luna, GPT-4o, or GPT-4o-mini
- **COMBINED** — both methods merged with duplicate findings deduplicated

The system scores pages against **WCAG 2.2** criteria, supports benchmarking with precision/recall/F1 metrics, and provides a React dashboard for exploring results.

---

## Architecture

| Service | Technology | Port |
|---------|-----------|------|
| Database | PostgreSQL 16 | 5432 |
| Backend | Spring Boot 4.x · Java 21 · Playwright · Spring AI | 8080 |
| Frontend | React 19 · Vite | 80 |

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

Rule-based analysis powered by **Axe-Core 4.9** running inside a headless Chromium browser via **Playwright**. Produces a list of WCAG violations with impact levels (critical, serious, moderate, minor) and affected element selectors.

### AI

The page HTML and screenshot are sent to **GPT-6 Luna** by default. **GPT-4o** and **GPT-4o-mini** are also available in Settings. The model identifies accessibility issues based on WCAG 2.2 guidelines and returns structured findings. Useful for catching semantic and contextual issues that rule-based engines miss.

### COMBINED

Runs both AXE and AI analysis then merges the results. Duplicate findings (same WCAG criterion, same element) are deduplicated so each issue is reported once. Provides the broadest coverage.

---

## Scan report evidence

Completed axe-core reports show affected elements grouped by axe impact, the number of distinct rules with findings, and WCAG criteria associated with findings. They also show axe-core checks that need manual review separately from violations. The report does not calculate a composite severity score or WCAG pass percentages; absence of automated findings does not establish that a criterion passes.

---

## Benchmark

The benchmark feature compares analysis methods against **ground-truth accessibility labels**. The [W3C BAD (Before and After Demonstration)](https://www.w3.org/WAI/demos/bad/) pages are used as the reference dataset.

Metrics computed per method:

| Metric | Description |
|--------|-------------|
| **Precision** | Of all reported issues, the fraction that are true violations |
| **Recall** | Of all true violations, the fraction correctly detected |
| **F1** | Harmonic mean of precision and recall |

Results can be exported as CSV for further analysis.

---

## Development Setup

> These steps are only needed if you want to run services outside Docker.
>
> **Requirements:** Java 21, Node 22, PostgreSQL 16 running locally.

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

The backend reads `application.properties` and resolves `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_API_KEY`, and `OPENAI_API_KEY` from environment variables (defaults fall back to the local Docker Compose values).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server starts on `http://localhost:5173` by default and proxies API calls to `http://localhost:8080`.
