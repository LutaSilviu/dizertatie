# Usage:
#   $env:GH_TOKEN = "ghp_your_token_here"
#   .\create-issues.ps1

param(
  [string]$Token = $env:GH_TOKEN,
  [string]$Repo  = "LutaSilviu/dizertatie"
)

if (-not $Token) { throw "Set GH_TOKEN env var first: `$env:GH_TOKEN = 'ghp_...'`" }

$headers = @{
  Authorization        = "Bearer $Token"
  Accept               = "application/vnd.github+json"
  "X-GitHub-Api-Version" = "2022-11-28"
}

function New-GHIssue([string]$Title, [string]$Body) {
  $payload = [ordered]@{ title = $Title; body = $Body; labels = @("ready-for-agent") } | ConvertTo-Json -Depth 5
  $r = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/issues" `
    -Method Post -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) `
    -ContentType "application/json; charset=utf-8"
  Write-Host "Created #$($r.number): $($r.title)  ->  $($r.html_url)"
  Start-Sleep -Milliseconds 400
  return $r.number
}

# --- Ensure label exists ---
try {
  $lb = @{ name = "ready-for-agent"; color = "0075ca"; description = "Ready for an agent to pick up" } | ConvertTo-Json
  Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/labels" -Method Post -Headers $headers `
    -Body $lb -ContentType "application/json" | Out-Null
} catch {}

# ── Infrastructure ────────────────────────────────────────────────────────────

New-GHIssue "T01: PostgreSQL + JPA entities + Flyway migrations" @"
## What to build
Configure the PostgreSQL datasource, define all JPA entities, and set up Flyway so the schema is created on boot.

- Datasource driven entirely by environment variables (no hardcoded credentials).
- JPA entities: Scan, Finding, BenchmarkRun, BenchmarkPage, GroundTruthLabel — matching the spec schema.
- Flyway V1 migration that creates all five tables.

## Acceptance criteria
- [ ] App starts and connects to PostgreSQL using env vars.
- [ ] Flyway creates all five tables on first boot and is a no-op on repeated boots.
- [ ] All entities persist and reload in a test context without errors.

## Blocked by
None (can start immediately).
"@

New-GHIssue "T02: API key security filter + health endpoint" @"
## What to build
Protect every backend endpoint behind a single API key and add a health check for Docker Compose.

- Servlet filter reads the X-API-Key header; returns 401 if missing or wrong (value configured via env var).
- GET /health returns 200 with no authentication required.

## Acceptance criteria
- [ ] GET /health returns 200 without an API key.
- [ ] Any other endpoint returns 401 when the header is missing or incorrect.
- [ ] Any other endpoint returns its normal response when the correct key is supplied.

## Blocked by
None (can start immediately).
"@

# ── Playwright capture ────────────────────────────────────────────────────────

New-GHIssue "T03: Playwright setup + DOM and screenshot capture" @"
## What to build
Add Playwright for Java. Given a URL, open one headless Chromium session, wait for full render, capture rendered outerHTML and a PNG screenshot, then close. Both artefacts are reused by the Axe and AI conditions so there is no second page load.

- Pin the Chromium version.
- Expose browser version as a string for recording on Scan.
- Typed exception on unreachable URL.

## Acceptance criteria
- [ ] A service method accepts a URL and returns rendered outerHTML + PNG bytes.
- [ ] Both artefacts come from one browser session.
- [ ] Browser version is accessible as a string.
- [ ] Calling the method with an unreachable URL throws a typed exception.

## Blocked by
T01, T02
"@

New-GHIssue "T04: Axe-Core injection + raw result persistence" @"
## What to build
Reusing the Playwright session from T03, inject axe-core.min.js after full render, execute it, collect the raw JSON violations array, and persist them as Finding entities linked to a new Scan.

- Pin axe-core version; record it and the browser version on Scan.
- POST /scans (method=AXE) is the entry point.
- GET /scans/{id} returns the scan with findings.

## Acceptance criteria
- [ ] POST /scans with method AXE persists a Scan and findings for a known-inaccessible URL.
- [ ] Each Finding stores: source method, WCAG criterion, WCAG level, severity, element selector, description.
- [ ] Axe-Core version and browser version are recorded on the Scan.
- [ ] GET /scans/{id} returns the scan and its findings.

## Blocked by
T03
"@

# ── AI condition ──────────────────────────────────────────────────────────────

New-GHIssue "T05: OpenAI integration + structured accessibility prompt" @"
## What to build
Call the OpenAI API via Spring AI with the rendered DOM and screenshot. Send a fixed, versioned system prompt requesting structured JSON: issue, element (CSS selector), WCAG criterion, severity, confidence, description, suggested fix. Support GPT-4o and GPT-4o-mini per scan request. Use temperature 0 where supported. Store the raw JSON response before parsing.

## Acceptance criteria
- [ ] POST /scans with method AI calls OpenAI and returns a non-empty findings list.
- [ ] Model ID and prompt version are stored on the Scan.
- [ ] GPT-4o-mini can be selected and produces a different model_id than GPT-4o.
- [ ] Raw JSON response is stored on the Scan before parsing.

## Blocked by
T03
"@

New-GHIssue "T06: AI response parser + unit tests with fixtures" @"
## What to build
Parse the raw OpenAI JSON into Finding entities. Handle partial, malformed, or unexpected shapes without unhandled exceptions. Ship fixture files (well-formed, partial, malformed) and unit tests covering all three.

## Acceptance criteria
- [ ] A well-formed fixture produces the correct Finding count with all fields populated.
- [ ] A partial fixture (missing confidence or suggestion) produces findings with those fields null, not an exception.
- [ ] A malformed fixture returns an empty list and logs a warning.
- [ ] Unit tests for all three fixture cases pass.

## Blocked by
T05
"@

New-GHIssue "T07: AI response caching (URL hash + model + prompt version)" @"
## What to build
Cache every raw OpenAI response. Cache key = SHA-256(rendered outerHTML) + model ID + prompt version. On a hit, load and re-parse the stored response; skip the API call. Record a cache_hit flag on Scan.

## Acceptance criteria
- [ ] A second POST /scans for the same URL and model makes zero OpenAI API calls (verified by mock/spy).
- [ ] The Scan for the cache hit has cache_hit = true.
- [ ] Changing model or prompt version causes a cache miss and a new API call.

## Blocked by
T06
"@

# ── Scoring and merge ─────────────────────────────────────────────────────────

New-GHIssue "T08: WCAG conformance level scoring (A / AA / AAA pass rate)" @"
## What to build
Given a list of findings, compute the percentage of applicable WCAG criteria at each level (A, AA, AAA) that have no detected violations. Implement as a pure function. GET /scans/{id} must include the three pass rates.

## Acceptance criteria
- [ ] Function returns level_A_pass_rate, level_AA_pass_rate, level_AAA_pass_rate.
- [ ] A scan with no findings scores 100% at all levels.
- [ ] A Level A violation reduces only the Level A pass rate.
- [ ] Unit tests: no findings, one A violation, one AA violation, mixed violations.

## Blocked by
T04
"@

New-GHIssue "T09: Weighted severity score + combined condition dedup merge" @"
## What to build
Weighted severity score (0-100): Critical=4, Serious=3, Moderate=2, Minor=1. Formula: 100 minus (sum of weights divided by max possible weight times 100).

Combined condition: merge Axe and AI findings. Two findings are the same issue when they share the same WCAG criterion AND the same element selector. Deduplicated findings tagged source=COMBINED.

Both scores appear on GET /scans/{id}. POST /scans with method COMBINED runs both conditions and merges.

## Acceptance criteria
- [ ] POST /scans with method COMBINED returns a merged list with no duplicates by the rule.
- [ ] GET /scans/{id} includes severity_score (0-100).
- [ ] No-findings scan scores 100. One critical violation scores below 100.
- [ ] Dedup unit tests: same criterion + same element = one finding; same criterion + different element = two; different criterion = two.
- [ ] Scoring unit tests verify the formula against a fixed input.

## Blocked by
T07, T08
"@

# ── Scan API ──────────────────────────────────────────────────────────────────

New-GHIssue "T10: POST /scans + GET /scans/{id} — scan submission and full result" @"
## What to build
Wire up the two primary scan endpoints.

POST /scans: accepts url, method (AXE / AI / COMBINED), optional model. Runs the analysis, persists Scan and Findings, returns scan ID. GET /scans/{id}: returns full scan — metadata, both scores, findings grouped by WCAG category (Perceivable, Operable, Understandable, Robust).

Add MockMvc integration tests for both endpoints with Playwright and OpenAI stubbed.

## Acceptance criteria
- [ ] POST /scans returns a scan ID synchronously.
- [ ] GET /scans/{id} returns findings grouped by WCAG category.
- [ ] Each finding includes: source_method, wcag_criterion, wcag_level, severity, element_selector, description, suggestion.
- [ ] Both conformance scores and the severity score are present.
- [ ] Integration tests pass with stubbed dependencies.

## Blocked by
T09
"@

New-GHIssue "T11: GET /scans (paginated history) + GET /scans/{id}/export.csv" @"
## What to build
Let the frontend list past scans and download findings as CSV.

GET /scans: paginated list in reverse chronological order, each item showing URL, method, model, timestamp, scores. GET /scans/{id}/export.csv: streams findings as CSV with headers — method, wcag_criterion, wcag_level, severity, element_selector, description, suggestion.

Add MockMvc + RestDocs integration tests for both endpoints.

## Acceptance criteria
- [ ] GET /scans returns scans in reverse chronological order, paginated.
- [ ] GET /scans/{id}/export.csv returns a valid CSV with the correct headers and one row per finding.
- [ ] RestDocs generates API documentation snippets during the test run.
- [ ] Integration tests pass with stubbed dependencies.

## Blocked by
T10
"@

# ── Ground truth ──────────────────────────────────────────────────────────────

New-GHIssue "T12: GroundTruthLabel entity + seed built-in dataset" @"
## What to build
Populate the GroundTruthLabel table with a built-in curated test set of pages that have known, documented accessibility issues. Document every source in DATASET.md.

Seed includes: W3C Before and After Demonstration pages plus hand-crafted pages with injected defects. At least 20 pages covering: missing alt text, low contrast, unlabelled form fields, missing heading structure, broken ARIA roles.

Each label records: url, wcag_criterion, wcag_level, severity, element_selector, annotator, verified_by (nullable), confidence.

## Acceptance criteria
- [ ] Application boot seeds at least 20 GroundTruthLabel rows.
- [ ] The seed is idempotent (no duplicate rows on repeated boots).
- [ ] DATASET.md lists every seeded page with source URL and license.
- [ ] Labels cover at least five distinct WCAG criteria.

## Blocked by
T01
"@

New-GHIssue "T13: Ground truth label endpoints (POST / GET / DELETE /labels)" @"
## What to build
Expose REST endpoints so the researcher can view and extend the ground truth dataset with custom labels for real-world pages.

GET /labels — returns all labels, filterable by url query param.
POST /labels — persists a new custom label.
DELETE /labels/{id} — removes a label.

## Acceptance criteria
- [ ] GET /labels returns the seeded built-in labels.
- [ ] POST /labels persists a new label and it appears in subsequent GET /labels responses.
- [ ] DELETE /labels/{id} removes the label and it no longer appears in GET /labels.
- [ ] Filtering by url returns only labels matching that URL.

## Blocked by
T12
"@

# ── Benchmark engine ──────────────────────────────────────────────────────────

New-GHIssue "T14: Benchmark run entity + async scan runner" @"
## What to build
Accept a batch of URLs (or the built-in test set flag) and run all three scan conditions asynchronously for every URL. Expose progress polling.

POST /benchmark-runs: accepts run name, list of URLs or use_builtin_set flag. Starts async execution, returns run ID immediately. Each URL is scanned with AXE, AI, and COMBINED in sequence. Results are stored as BenchmarkPage rows linked to the run. GET /benchmark-runs/{id}/status: returns pages_done, total_pages, status (RUNNING / COMPLETE / FAILED). GET /benchmark-runs: lists past runs.

## Acceptance criteria
- [ ] POST /benchmark-runs returns a run ID immediately without waiting for scans to finish.
- [ ] GET /benchmark-runs/{id}/status reflects live progress.
- [ ] After completion, status is COMPLETE and pages_done equals total_pages.
- [ ] Using use_builtin_set runs all seeded pages.
- [ ] Each completed BenchmarkPage has findings for all three methods.

## Blocked by
T11, T13
"@

New-GHIssue "T15: Per-page cost and latency tracking" @"
## What to build
For each page in a benchmark run, record how long each analysis condition took and how much the AI conditions cost.

- Record wall-clock latency per condition per page (milliseconds).
- Estimate OpenAI cost per page: token count times the model's per-token price (GPT-4o and GPT-4o-mini rates; document the rates used).
- Expose average cost per page and average latency per condition on GET /benchmark-runs/{id}/status once the run is complete.

## Acceptance criteria
- [ ] Each BenchmarkPage stores latency_axe_ms, latency_ai_ms, latency_combined_ms.
- [ ] Each BenchmarkPage stores estimated_cost_usd for AI and COMBINED conditions.
- [ ] GET /benchmark-runs/{id}/status includes avg_cost_per_page_usd and per-condition avg latency when complete.
- [ ] The cost model (rates used) is documented in a code comment or README section.

## Blocked by
T14
"@

New-GHIssue "T16: Benchmark metrics (precision / recall / F1) + CSV export" @"
## What to build
Compute detection metrics per method against ground truth labels. Export raw results as CSV.

Matching rule: a predicted finding matches a ground truth label when both share the same WCAG criterion AND element selector.

GET /benchmark-runs/{id}/metrics: returns per-method precision, recall, F1 (overall and per WCAG category), FP count + examples, FN count + examples, avg cost, avg latency. GET /benchmark-runs/{id}/export.csv: streams per-page, per-method, per-finding results.

Unit tests for precision, recall, F1 against a fixed predicted-vs-truth fixture.

## Acceptance criteria
- [ ] GET /benchmark-runs/{id}/metrics returns non-zero metrics for a completed run against the seeded test set.
- [ ] Per-category breakdown covers Perceivable, Operable, Understandable, Robust.
- [ ] FP and FN examples include: url, criterion, element selector, description.
- [ ] CSV export has one row per finding with columns: method, url, criterion, severity, element, ground_truth_match.
- [ ] Unit tests for all three metric functions pass.

## Blocked by
T15
"@

# ── Frontend ──────────────────────────────────────────────────────────────────

New-GHIssue "T17: React project bootstrap + settings (API key)" @"
## What to build
Create the React frontend project and implement the settings screen.

Bootstrap a Vite + React project under frontend/ alongside the backend directory. Configure a proxy so /api calls reach the backend (used in Docker Compose and local dev). Settings screen: API key input stored in sessionStorage, sent as X-API-Key on every request. Basic navigation shell linking to Analyzer and Benchmark pages.

## Acceptance criteria
- [ ] npm run dev starts the frontend and proxies /api to the backend.
- [ ] The settings screen stores the API key and a test request to GET /health succeeds.
- [ ] Navigation links to Analyzer and Benchmark pages render without errors.
- [ ] docker compose up serves the React app.

## Blocked by
T11
"@

New-GHIssue "T18: React frontend — Analyzer page" @"
## What to build
The main user-facing page: submit a URL, choose method and model, view the structured report, and export CSV.

- URL input form with method selector (Axe-Core / AI / Combined) and model selector (GPT-4o / GPT-4o-mini, visible only when method includes AI).
- Loading state while scan runs.
- Report view: conformance scores (A/AA/AAA pass rates) and severity score (0-100), findings grouped by WCAG category and severity, each finding showing source method, element, description, and suggestion.
- CSV export button.
- Scan history list (GET /scans) linking to past reports.

## Acceptance criteria
- [ ] Submitting a URL with method Axe-Core displays a report with scores and grouped findings.
- [ ] Switching to AI or Combined shows correct source labels on findings.
- [ ] Model selector is visible only when method includes AI.
- [ ] CSV export button downloads a file from GET /scans/{id}/export.csv.
- [ ] History list shows past scans; clicking one loads its report.

## Blocked by
T17
"@

New-GHIssue "T19: React frontend — Benchmark page" @"
## What to build
The research harness UI: configure a batch run, watch progress, and explore comparative metrics.

- URL list input (textarea) or toggle to use the built-in test set.
- Run button triggers POST /benchmark-runs; progress bar polls GET /benchmark-runs/{id}/status.
- Results view on completion: summary table (precision / recall / F1 per method side by side), per-WCAG-category breakdown, FP/FN example list per method (expandable), cost and latency columns.
- CSV export button.
- Past runs list linking to their results.

## Acceptance criteria
- [ ] Pasting 3 URLs and clicking Run starts a benchmark and shows live progress.
- [ ] After completion the summary table shows metrics for all three methods.
- [ ] Per-category breakdown is visible and navigable.
- [ ] At least one FP or FN example per method is shown when the run produced any.
- [ ] CSV export downloads the raw results file.

## Blocked by
T18, T16
"@

# ── Ops ───────────────────────────────────────────────────────────────────────

New-GHIssue "T20: Docker Compose + README" @"
## What to build
Package all three services into a single docker-compose.yml and document the setup.

- Three services: db (PostgreSQL with persisted volume), backend (Spring Boot), frontend (Nginx serving the React build).
- Frontend Nginx config proxies /api to the backend.
- Backend depends_on db with a health-check condition so it waits for Postgres to be ready.
- All secrets via environment variables; provide a .env.example listing every required variable.
- README.md: one-command startup, list of env vars, how to run a scan and a benchmark.

## Acceptance criteria
- [ ] docker compose up from a clean checkout starts all three services without errors.
- [ ] POST /scans via the frontend (through Nginx) returns a valid result.
- [ ] A benchmark run completes end-to-end against the built-in test set.
- [ ] docker compose down followed by docker compose up preserves scan history.
- [ ] README.md covers: env vars, startup command, and how to verify the app is working.

## Blocked by
T19
"@

Write-Host "`nAll 20 issues created."
