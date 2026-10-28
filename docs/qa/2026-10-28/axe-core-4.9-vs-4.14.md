# axe-core 4.9.0 to 4.14.0 fixed-page comparison

**Date:** 28 October 2026

**Configuration:** `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa`; Chromium/Playwright; navigate at `DOMContentLoaded`, 20 s timeout, then 750 ms stabilization. The two versions were run against the same ten URLs in the same order. Full per-rule and per-incomplete evidence is in [`axe-core-4.9-vs-4.14.json`](axe-core-4.9-vs-4.14.json).

## Results

| Fixed page | axe-core 4.9.0 | axe-core 4.14.0 |
|---|---|---|
| https://www.nasa.gov/ | 0 rules / 0 elements; impact counts none; WCAG none; incomplete aria-roledescription(1), color-contrast(40) | 0 rules / 0 elements; impact counts none; WCAG none; incomplete color-contrast(40) |
| https://www.apple.com/ | 3 rules / 14 elements; critical:2, serious:12; WCAG 1.3.1, 1.4.3, 2.5.8; incomplete aria-prohibited-attr(2), color-contrast(39), duplicate-id-aria(9) | 4 rules / 15 elements; critical:2, serious:13; WCAG 1.3.1, 1.4.3, 2.5.3, 2.5.8; incomplete aria-prohibited-attr(2), aria-valid-attr-value(1), color-contrast(39), duplicate-id-aria(9) |
| https://www.microsoft.com/ | 0 rules / 0 elements; impact counts none; WCAG none; incomplete aria-allowed-attr(1), aria-prohibited-attr(5), color-contrast(35) | 1 rule / 3 elements; serious:3; WCAG 2.5.3; incomplete aria-allowed-attr(1), color-contrast(2) |
| https://www.bbc.com/ | 1 rule / 1 element; serious:1; WCAG 1.4.1; incomplete aria-prohibited-attr(3), color-contrast(45) | 2 rules / 25 elements; serious:25; WCAG 1.4.1, 2.5.3; incomplete color-contrast(48) |
| https://www.cloudflare.com/ | 1 rule / 4 elements; serious:4; WCAG 1.4.3; incomplete aria-roledescription(2), color-contrast(81) | 1 rule / 14 elements; serious:14; WCAG 1.4.3; incomplete color-contrast(64) |
| https://www.python.org/ | BLOCKED (HTTP 200) | BLOCKED (HTTP 200) |
| https://developer.mozilla.org/ | BLOCKED (HTTP 200) | BLOCKED (HTTP 200) |
| https://www.khanacademy.org/ | 0 rules / 0 elements; impact counts none; WCAG none; incomplete none | 0 rules / 0 elements; impact counts none; WCAG none; incomplete none |
| https://www.shopify.com/ | 0 rules / 0 elements; impact counts none; WCAG none; incomplete color-contrast(65), video-caption(1) | 1 rule / 1 element; serious:1; WCAG 2.5.3; incomplete aria-valid-attr-value(1), color-contrast(65), video-caption(1) |
| https://www.gnu.org/ | RATE_LIMITED (HTTP 429) | TIMED_OUT |

## Interpretation

- The 4.14.0 run reported additional rule/element findings on Apple, Microsoft, BBC, Cloudflare, and Shopify. Some findings reference WCAG 2.5.3, which was absent from those 4.9.0 result sets. This is consistent with the engine/rule-coverage change, but live pages may also have changed between sequential runs, so counts are observations rather than proof of an engine-only effect.
- NASA and Khan Academy had no violations under either version. The number and identities of `incomplete` manual-review rules varied between runs; those remain undecided checks, not violations.
- Python.org and MDN refused injected axe scripts under their Content Security Policy in both runs and are marked `BLOCKED`; no accessibility findings are claimed. GNU returned HTTP 429 in the 4.9.0 run and timed out before `DOMContentLoaded` in the 4.14.0 run, so neither is treated as a completed scan.
- The earlier 27 October assessment recorded 4.9.0 findings for ten pages, but only a narrative summary was available in the task checkout, not the original raw axe JSON or DOM snapshots. This capture provides an apples-to-apples version comparison from a new fixed run, not a byte-for-byte reproduction of the earlier assessment.
- No score or conformance claim is calculated. A missing automated finding does not establish that a WCAG criterion passes.

## Per-scan evidence

Each completed page records the exact distinct violation rules and criteria derived from axe tags, affected node counts by impact, plus each incomplete rule and affected node count in the JSON artifact. Failed and blocked attempts contain a status and reason only.

## Application browser check

On 28 October 2026, the Vite app was opened in headless Chromium at `/report/123` with a local API response using the captured 4.14.0 `label-content-name-mismatch` finding (WCAG 2.5.3) and `color-contrast` incomplete result. The report displayed the finding and a separate “Needs manual review (1)” section with its target and failure summary. The page contained no composite score or WCAG percentage. This checked the report path with a representative 4.14.0 response; the fixed-page scans above exercised axe-core directly in Chromium.

## History browser check

On 28 October 2026, a temporary Chromium/Playwright check opened `/history` and stubbed `/api/scans?page=0&size=20` with one `RATE_LIMITED` scan. It verified that history displayed the scan URL, explicit status, HTTP 429 reason, and zero findings. The temporary JUnit/Playwright test passed (1 test) and was removed after the check.
