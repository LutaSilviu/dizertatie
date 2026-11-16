# Development history: problems found and current status

Date of this record: 16 November 2026. All work described here is on `main`.

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
| #41 | An axe rule that names two WCAG criteria (for example `link-name`: 2.4.4 and 4.1.2) kept only the first | A label on the second criterion could never be matched by axe, lowering its recall | One finding per criterion per element. axe recall on the dataset rose from 0.46 to 0.52; for the Robust principle from 0.46 to 1.00 |
| #44 | Page-capture tests failed after a navigation change | Test stubs were out of date | Tests updated; a missing main-document response is now an explicit failure |
| (found during testing with a real key) | AI findings could almost never match an axe finding or a dataset label | See 2.3 | See 2.3 |

The counting rules that follow from #33, #34 and #41 are recorded in [ADR 0001](adr/0001-benchmark-counting-rules.md). Benchmark numbers produced before these changes are not comparable with later ones.

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

### 2.3.1 Prompt revision (v1 to v2)

With selectors verified, the first benchmark showed that the original prompt (v1) produced low AI precision, mainly because the model reported one broad selector for a group of elements. Prompt v2 adds two sets of rules: *selector rules* (one finding per failing element; build selectors only from ids, classes and attribute values present in the HTML; never a selector that also matches elements without the problem) and *evidence rules* (report only failures visible in the HTML or screenshot; one criterion per finding). The prompt version is stored with every scan and can be selected with `AI_PROMPT_VERSION`, so both versions can be run under identical conditions. Results are in section 3.

### 2.3.2 Benchmark measurement problems

| Problem | Why it mattered | Resolution |
|---|---|---|
| AI replies were cached for the lifetime of the backend | Repeating a benchmark returned the same AI reply, so repeated runs were not independent | Each benchmark run clears the cache when it starts. Within a run, AI and COMBINED still share one reply per page, so COMBINED is exactly the axe findings plus that run's AI findings |
| COMBINED time per page excluded the model's time when the reply came from the cache | COMBINED appeared about three times faster than AI | The recorded COMBINED time includes the model time of the reused reply |

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
| `docker compose up --build` fails with `PKIX path building failed` | The antivirus (AVG) inspects HTTPS traffic and re-signs it with its own certificate, which the build containers do not trust | Certificate checks were not disabled. The builds accept optional extra root certificates from a `certs/` folder and add them to the Java, Node and Chromium trust stores inside the images. The certificate is machine-specific and not committed. With the antivirus certificate in place, both images build and the stack runs in Docker |
| The backend reported OpenAI as unreachable | Same cause: Java's own certificate store does not contain the antivirus certificate | The backend is started with the Windows certificate store (`-Djavax.net.ssl.trustStoreType=Windows-ROOT`); certificate checking stays on |
| The frontend container cannot bind port 80 | Port 80 is held by a Windows system process | Set `FRONTEND_PORT` in `.env` |
| AI and COMBINED scans were refused | The OpenAI key in `.env` was a placeholder | A real key was configured; the Settings page shows whether the key is missing, rejected or working |

## 3. Verification

- **Automated tests:** 984 backend tests pass; the frontend type-checks and builds.
- **Manual end-to-end run on the host** (database in Docker, backend and frontend as local processes):
  - all 10 database migrations applied and the schema validated;
  - AXE, AI and COMBINED scans of the demo home page completed;
  - an unreachable URL was stored as `FAILED` with a readable reason and no findings;
  - requests without the API key were rejected (401), an invalid method was rejected (400);
  - history, report, benchmark and settings pages were checked in the browser; CSV export worked.
- **Full stack in Docker** (`docker compose up`, images built from the current code): the UI loads, the Settings check reaches OpenAI, AXE and COMBINED scans of the demo page complete, external HTTPS pages can be scanned, and requests without the API key are rejected.

### Benchmark results

Conditions: built-in dataset, five pages (the 356 labels are on the four "before" pages); 16 November 2026; all fixes and counting rules above in force; three independent runs per configuration; every method completed on every page in every run. Values are the mean over the three runs, ± the standard deviation. axe-core is deterministic, so its figures are identical in every run.

**Main result (prompt v2, model `gpt-6-luna`)**

| Method | Precision | Recall | F1 |
|---|---|---|---|
| AXE | 0.944 | 0.517 | 0.668 |
| AI | 0.797 ± 0.036 | 0.289 ± 0.046 | 0.423 ± 0.051 |
| COMBINED | 0.867 ± 0.013 | 0.677 ± 0.006 | 0.760 ± 0.001 |

**Effect of the prompt revision (model `gpt-6-luna`)**

| Prompt | AI precision | AI recall | AI F1 | COMBINED F1 |
|---|---|---|---|---|
| v1 | 0.431 ± 0.102 | 0.145 ± 0.023 | 0.217 ± 0.039 | 0.668 ± 0.022 |
| v2 | 0.797 ± 0.036 | 0.289 ± 0.046 | 0.423 ± 0.051 | 0.760 ± 0.001 |

**Comparison of models (prompt v2)**

| Model | AI precision | AI recall | AI F1 | COMBINED precision | COMBINED recall | COMBINED F1 | Estimated cost per page (USD) |
|---|---|---|---|---|---|---|---|
| `gpt-6-luna` | 0.797 ± 0.036 | 0.289 ± 0.046 | 0.423 ± 0.051 | 0.867 ± 0.013 | 0.677 ± 0.006 | 0.760 ± 0.001 | 0.0013 |
| `gpt-4o` | 0.293 ± 0.023 | 0.044 ± 0.004 | 0.076 ± 0.005 | 0.791 ± 0.023 | 0.522 ± 0.000 | 0.629 ± 0.007 | 0.0288 |
| `gpt-4o-mini` | 0.487 ± 0.013 | 0.088 ± 0.001 | 0.149 ± 0.002 | 0.814 ± 0.006 | 0.540 ± 0.001 | 0.649 ± 0.002 | 0.0017 |

The cost column is the application's own estimate from a fixed page size and the published token prices, not a measured bill.

**By WCAG principle (prompt v2, `gpt-6-luna`; mean precision / recall)**

| Principle | AXE | AI | COMBINED |
|---|---|---|---|
| Perceivable | 0.99 / 0.65 | 0.90 / 0.37 | 0.94 / 0.79 |
| Operable | 0.66 / 0.24 | 0.66 / 0.40 | 0.65 / 0.62 |
| Understandable | 1.00 / 0.08 | 0.00 / 0.00 | 1.00 / 0.08 |
| Robust | 1.00 / 1.00 | 0.00 / 0.00 | 0.97 / 1.00 |

**Time per page.** In runs with nothing else running on the machine, AXE took about 2 to 6 s per page, AI about 17 to 36 s, and COMBINED about 20 to 26 s. Several runs overlapped with a Docker image build, so the timings were not collected under controlled conditions and should be re-measured before they are quoted.

Observations that the data supports:

- axe-core is precise (0.94) but finds about half of the labelled problems (0.52).
- With `gpt-6-luna` and prompt v2, combining the two methods is better than axe alone on both recall (0.68 against 0.52) and F1 (0.76 against 0.67), with a moderate loss of precision (0.87 against 0.94).
- The gain is concentrated in the Operable principle, where the AI finds problems axe cannot (recall 0.62 combined against 0.24 for axe), and in Perceivable. The AI contributed nothing for Understandable and Robust on this dataset.
- The prompt matters as much as the method: the selector and evidence rules roughly doubled AI precision and recall. With prompt v1 the combined method was no better than axe alone (F1 0.668 for both).
- The model matters: with `gpt-4o` and `gpt-4o-mini` the AI method adds little, and COMBINED scores below axe alone on F1 because the few extra true positives do not offset the false positives.
- The AI method alone is not a substitute for axe-core with any tested model.

Earlier single runs reported during development (AI F1 0.14, COMBINED F1 0.60) were made before the #41 change and with prompt v1; they are superseded by the tables above.

## 4. Current status

**Working**

- AXE, AI and COMBINED scans, with stored evidence (engine versions, model, prompt version, raw AI reply).
- Failed scans recorded with status `BLOCKED`, `RATE_LIMITED`, `TIMED_OUT` or `FAILED` and a reason.
- Scan history, report page, CSV export.
- Benchmark runs over the built-in dataset or custom URLs, with any subset of methods; per-method failure isolation; metrics and CSV export.
- OpenAI key status on the Settings page.
- Running with `docker compose up` (images built from the current code) and running on the host.

**Not verified**

- Timings under controlled conditions (see section 3).
- A build on a machine without HTTPS inspection, where the `certs/` folder stays empty. The certificate step is written to do nothing in that case, but this path was not exercised.

**Open**

- No open bug reports. Issues #33, #34, #35, #40, #41, #42, #43 and #44 and the older feature tickets are closed.

## 5. Known limitations to state in the dissertation

1. **Exact matching.** A finding matches a label only when criterion and element are identical. A finding on a parent or child of the labelled element does not count.
2. **Dataset scope.** Labels cover WCAG 2.0 on four pages of one demonstration site. axe also runs WCAG 2.1/2.2 rules; such findings (for example `target-size`, 2.5.8) count as false positives. The site is public and well known, so a language model may have seen it, and its faults, during training; results on unseen sites may be lower.
3. **Failed pages are excluded per method.** When methods fail on different pages their recall covers different page sets; compare methods only on runs with no failures.
4. **AI variability and sample size.** The model's answers differ between runs. The figures in section 3 are means of three runs; three runs give only a rough idea of the spread. Outside benchmark runs, replies are cached in memory per page, model and prompt version while the backend is running.
5. **Prompt tuned on the evaluation data.** Prompt v2 was written after looking at v1's results on this same dataset. Its improvement is therefore partly fitted to these pages and should be confirmed on pages that played no part in writing it.
6. **AI input limit.** HTML sent to the model is capped at 200,000 characters; scripts, styles and comments are removed first on larger pages.
7. **Grounding depends on reopening the page.** AI selectors are resolved in a second page load; on pages whose content changes between loads, some selectors may no longer match and are kept as written.
