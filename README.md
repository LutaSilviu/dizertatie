# Web Accessibility Analyzer

A master's thesis project at **USV Suceava** that evaluates web page accessibility by comparing three analysis methods:

- **AXE** — rule-based analysis using Axe-Core 4.9 via Playwright
- **AI** — LLM-powered analysis using OpenAI GPT-4o / GPT-4o-mini
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
# Edit .env — set at minimum: POSTGRES_PASSWORD, APP_API_KEY, OPENAI_API_KEY
docker compose up --build
```

| URL | Description |
|-----|-------------|
| http://localhost | React frontend |
| http://localhost:8080 | Backend REST API |
| http://localhost:8080/swagger-ui.html | Swagger UI (interactive API docs) |

---

## Environment Variables

Copy `.env.example` to `.env` and fill in the required values before starting the stack.

| Variable | Description | Example |
|----------|-------------|---------|
| `POSTGRES_DB` | PostgreSQL database name | `accessibility` |
| `POSTGRES_USER` | PostgreSQL username | `accessibility` |
| `POSTGRES_PASSWORD` | PostgreSQL password (**required**) | `changeme` |
| `APP_API_KEY` | API key that protects all backend endpoints (**required**) | `my-secret-key` |
| `OPENAI_API_KEY` | OpenAI API key used for AI analysis (**required**) | `sk-...` |

---

## API Authentication

All API endpoints (except `/health`) require the `X-API-Key` header:

```
X-API-Key: <your APP_API_KEY value>
```

The key can be configured in the **Settings** page of the React UI; it is stored locally in the browser and attached to every request automatically.

---

## Analysis Methods

### AXE

Rule-based analysis powered by **Axe-Core 4.9** running inside a headless Chromium browser via **Playwright**. Produces a list of WCAG violations with impact levels (critical, serious, moderate, minor) and affected element selectors.

### AI

The page HTML is sent to **GPT-4o** or **GPT-4o-mini** (selectable in Settings). The model identifies accessibility issues based on WCAG 2.2 guidelines and returns structured findings. Useful for catching semantic and contextual issues that rule-based engines miss.

### COMBINED

Runs both AXE and AI analysis then merges the results. Duplicate findings (same WCAG criterion, same element) are deduplicated so each issue is reported once. Provides the broadest coverage.

---

## WCAG Scoring

Each analysis produces two complementary scores:

- **Severity score (0–100)** — weighted aggregate of violation impact levels; 100 means no issues found.
- **Conformance pass rates** — percentage of criteria that pass at each conformance level (Level A, AA, AAA).

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
