# Development history: problems found and current status

Date of this record: 16 November 2026. Branch: `feat/complete-app`.

This document records the problems found while reviewing and completing the Web Accessibility Analyzer, how each was resolved, what was measured afterwards, and what is still open. It is meant as source material for the project documentation and the dissertation. Issue numbers refer to this repository's GitHub Issues.

## 1. Starting point

The application compares three ways of detecting WCAG problems on a web page:

- **AXE**: rule-based analysis with axe-core 4.14, run in a headless Chromium browser through Playwright.
- **AI**: a language model (OpenAI, default `gpt-6-luna`) receives the rendered HTML and a full-page screenshot and returns findings as JSON.
- **COMBINED**: both, with findings on the same element and criterion merged.

A benchmark module scores each method against a hand-labelled dataset (356 labels on four pages of the W3C "Before and After Demonstration" site) using precision, recall and F1.

At the start of the review the code was split across two lines of work that had diverged: `main`, and a parked branch (`wip/pre-scan-trustworthiness`, one commit of about 5,800 lines) holding the redesigned frontend, the OpenAI key status check, uniform API error handling, selectable benchmark methods and a corrected dataset. Eight bug reports were open.

## 2. Problems and resolutions

### 2.1 Integration problems

| Problem | Cause | Resolution |
|---|---|---|
| Two diverged lines of work | Work was parked on a side branch while `main` continued | Merged into `feat/complete-app`; 16 conflicts resolved. Where the lines disagreed, the later decisions on `main` were kept: failed scans are stored with a status, no composite scores, 15 s navigation limit |
| The existing database could not start on `main` | Both lines had added a database migration numbered V6 with different content; the real database had the parked line's V6 applied | The parked line's V6–V8 were kept as the database knew them; `main`'s two migrations were renumbered V9 and V10. The real database then migrated V6 → V10 without error (a dump was taken first) |
| One remote branch (`feat/03-api-key-filter`) was not merged | Its two commits were superseded by later work on `main` | Left out deliberately |

### 2.2 Result-correctness problems

These are the problems that matter for the validity of the thesis results. Each fix has a regression test.

| Issue | Problem | Why it mattered | Resolution |
|---|---|---|---|
| #33 | A benchmark page whose scan failed (blocked, timed out) was marked done, and every label on it was counted as a false negative | Recall was understated for pages the scanner never reached | A method's failed scan on a page is left out of that method's counts; the run reports how many pages failed per method |
| #34 | Two findings on the same element and criterion each counted as a true positive against one label | True positives could exceed the number of labels, inflating precision and recall | Findings are reduced to distinct criterion + selector pairs before counting |
| #35 | An AI reply that was empty, prose, or not the expected JSON was saved as a completed scan with zero findings | A failed AI call looked like a page with no problems | Such a reply now fails the scan with a readable reason; only a well-formed reply with an empty findings list counts as "nothing found" |
| #40 | axe rules from WCAG 2.1/2.2 Level AA were labelled Level A | Wrong conformance level in reports and exports | Level is taken from the WCAG criterion registry, then from axe's level tags |
| #42 | The report of an AI scan showed "0 affected elements, 0 rules" above a list of findings | Summary contradicted the detail | Counts cover all findings; findings are also counted by source (axe only, AI only, both) |
| #43 | The scan response did not say which axe-core version, browser version or AI model produced it | Results were not reproducible from the report | These are stored and shown |
| #44 | Page-capture tests failed after a navigation change | Test stubs were out of date | Tests updated; a missing main-document response is now an explicit failure |
| (found during testing with a real key) | AI findings could almost never match an axe finding or a dataset label | See 2.3 | See 2.3 |

The counting rules that follow from #33 and #34 are recorded in [ADR 0001](adr/0001-benchmark-counting-rules.md). Benchmark numbers produced before these changes are not comparable with later ones.

### 2.3 AI findings did not point at verifiable elements

This problem appeared only once the application was run against the real OpenAI API.

**Symptom.** On the demo home page a COMBINED scan returned 46 axe findings and 11 AI findings with no overlap at all, although both methods clearly reported some of the same problems (for example, images without a text alternative).

**Cause.** The model writes selectors in its own style and often one selector for a group of elements, for example `#main > table img:not([alt])`. axe-core and the dataset name each element individually with axe's selector generator, for example `img[src$="border_top.gif"]`. The benchmark and the merge step compare selector text, so an AI finding on the right element still counted as one false positive plus one false negative, and was never merged.

**Resolution.** After the model answers, each selector it gave is resolved in the live page, and the finding is replaced by one finding per matched element, named with the same generator axe uses (`axe.utils.getSelector`). This changes only how an element is named, never which elements the model claimed. A selector that matches nothing, or is not valid CSS, is kept as the model wrote it, so an unverifiable claim stays visible and counts as a false positive. Implemented in `AiFindingGrounder`.

**Effect on the demo home page (113 labels).**

| | Before | After |
|---|---|---|
| Findings (axe only / AI only / both) | 46 / 11 / 0 | 43 / 28 / 3 |
| Labels found by the COMBINED scan | 44 (all from axe) | 60 |

**Consequence for interpretation.** A broad selector such as `#main a` now claims every element it matches. Over-general AI findings therefore lower AI precision; this is a property of the model's output, made measurable by the fix.

### 2.4 Security and setup problems

| Problem | Resolution |
|---|---|
| The API key was compared with ordinary string comparison | Constant-time comparison |
| The backend could only run inside Docker with hand-set environment variables | It reads the project `.env` and falls back to the Compose defaults, so it runs on the host against the Compose database |
| The database port and the frontend port were fixed | Both are configurable; the database is published on the loopback interface only |
| Scan CSV export lacked the rule identifier | `ruleId` column added |

### 2.5 Environment problems on the development machine

These are not defects of the application but had to be worked around, and they are worth a note in the installation chapter.

| Problem | Cause | Handling |
|---|---|---|
| `docker compose up --build` fails with `PKIX path building failed` | The antivirus (AVG) inspects HTTPS traffic and re-signs it with its own certificate, which the build containers do not trust | Certificate checks were not disabled. Either pause HTTPS scanning during the build, or run backend and frontend on the host |
| The backend reported OpenAI as unreachable | Same cause: Java's own certificate store does not contain the antivirus certificate | The backend is started with the Windows certificate store (`-Djavax.net.ssl.trustStoreType=Windows-ROOT`); certificate checking stays on |
| The frontend container cannot bind port 80 | Port 80 is held by a Windows system process | Set `FRONTEND_PORT` in `.env` |
| AI and COMBINED scans were refused | The OpenAI key in `.env` was a placeholder | A real key was configured; the Settings page shows whether the key is missing, rejected or working |

## 3. Verification

- **Automated tests:** 980 backend tests pass; the frontend type-checks and builds.
- **Manual end-to-end run** (database in Docker, backend and frontend on the host):
  - all 10 database migrations applied and the schema validated;
  - AXE, AI and COMBINED scans of the demo home page completed;
  - an unreachable URL was stored as `FAILED` with a readable reason and no findings;
  - requests without the API key were rejected (401), an invalid method was rejected (400);
  - history, report, benchmark and settings pages were checked in the browser; CSV export worked.

### Benchmark results

Built-in dataset, five pages (the 356 labels are on the four "before" pages), model `gpt-6-luna`, 16 November 2026, after all fixes above. All methods completed on all pages.

| Method | Precision | Recall | F1 | Average time per page |
|---|---|---|---|---|
| AXE | 0.94 | 0.46 | 0.62 | 3.1 s |
| AI | 0.29 | 0.09 | 0.14 | 15.9 s |
| COMBINED | 0.68 | 0.54 | 0.60 | 4.8 s* |

\* The COMBINED time is low because the AI reply for the same page was served from the in-memory cache filled by the AI run; it is not a fair latency figure. Average AI cost reported by the application: about 0.00125 USD per page.

By WCAG principle (precision / recall):

| Principle | AXE | AI | COMBINED |
|---|---|---|---|
| Perceivable | 0.99 / 0.65 | 0.19 / 0.08 | 0.68 / 0.71 |
| Operable | 0.66 / 0.24 | 0.53 / 0.22 | 0.57 / 0.42 |
| Understandable | 1.00 / 0.08 | 0.33 / 0.02 | 0.71 / 0.10 |
| Robust | 1.00 / 0.46 | 0.00 / 0.00 | 1.00 / 0.46 |

Observations that the data supports:

- axe-core is precise but finds fewer than half of the labelled problems.
- The AI method alone is weak on this dataset, but its findings are largely different from axe's: combining the two raises recall from 0.46 to 0.54, most clearly for Operable criteria (0.24 to 0.42), at the cost of precision (0.94 to 0.68).
- These figures come from a single run with one model on one small dataset. Model output is not deterministic, so repeated runs are needed before quoting them as results.

## 4. Current status

**Working**

- AXE, AI and COMBINED scans, with stored evidence (engine versions, model, raw AI reply).
- Failed scans recorded with status `BLOCKED`, `RATE_LIMITED`, `TIMED_OUT` or `FAILED` and a reason.
- Scan history, report page, CSV export.
- Benchmark runs over the built-in dataset or custom URLs, with any subset of methods; per-method failure isolation; metrics and CSV export.
- OpenAI key status on the Settings page.

**Not verified**

- Building the Docker images on the development machine (blocked by the antivirus HTTPS inspection). The images were not rebuilt with the current code.
- Models other than `gpt-6-luna`.

**Open**

- **Issue #41:** an axe rule that maps to two WCAG criteria (for example `link-name`: 2.4.4 and 4.1.2) keeps only the first. Fixing it changes what counts as a benchmark match, so it is a methodology decision. Until then, the second criterion's labels cannot be met by axe, which lowers axe recall slightly.
- The work is committed on `feat/complete-app` and has not been merged into `main` or pushed. Issues #33, #34, #35, #40, #42, #43 and #44 are fixed in code but still open on GitHub, as are older feature tickets (#19, #20, #21, #26, #27, #29) that appear to be implemented.

## 5. Known limitations to state in the dissertation

1. **Exact matching.** A finding matches a label only when criterion and element are identical. A finding on a parent or child of the labelled element does not count.
2. **Dataset scope.** Labels cover WCAG 2.0 on four pages of one demonstration site. axe also runs WCAG 2.1/2.2 rules; such findings (for example `target-size`, 2.5.8) count as false positives.
3. **Failed pages are excluded per method.** When methods fail on different pages their recall covers different page sets; compare methods only on runs with no failures.
4. **AI variability.** The model's answers differ between runs, and replies are cached in memory per page, model and prompt version while the backend is running.
5. **AI input limit.** HTML sent to the model is capped at 200,000 characters; scripts, styles and comments are removed first on larger pages.
6. **Grounding depends on reopening the page.** AI selectors are resolved in a second page load; on pages whose content changes between loads, some selectors may no longer match and are kept as written.
