# Accessibility scan report trustworthiness

## Problem Statement

People using the accessibility analyzer cannot reliably interpret its report. The UI presents a custom severity score as a severity level, displays WCAG percentages that can exceed 100%, implies AAA coverage although the configured scan does not run AAA rules, and omits axe-core results that require human review. Failed navigation attempts can surface as generic errors, while the current `networkidle` wait can time out on pages that remain active or never become idle.

## Solution

Present evidence from completed axe-core scans without a composite score or conformance percentages. Show affected elements by severity, distinct rules, WCAG criteria associated with findings, and `incomplete` results in a separate manual-review section. Represent unsuccessful attempts as explicit persisted scan statuses with a reason and no findings or score. Start navigation at `DOMContentLoaded`, allow a bounded stabilization period, and classify attempts that cannot reach a scannable state. After these behavior changes, upgrade axe-core in a separate step and compare a fixed baseline before and after the upgrade.

## User Stories

1. As an accessibility analyst, I want to see the number of affected elements by axe-core impact, so that I can prioritize remediation using the engine's own severity categories.
2. As an accessibility analyst, I want to see distinct rules separately from affected elements, so that multiple instances of one rule are not mistaken for multiple rule types.
3. As an accessibility analyst, I want to see which WCAG success criteria have automated findings, so that I can trace findings to relevant criteria without interpreting absence as a pass.
4. As an accessibility analyst, I want no A, AA, or AAA pass percentages or composite score in the report, so that the UI does not imply conformance beyond the evidence collected.
5. As an accessibility analyst, I want axe-core `incomplete` results in a separate manual-review section, so that I can follow up on checks automation could not decide.
6. As an accessibility analyst, I want completed scans with no findings distinguished from attempts that failed, so that a blocked or timed-out page is never presented as a clean result.
7. As an accessibility analyst, I want a failed attempt to show its status and reason in the result and History, so that I can identify timeouts, rate limiting, blocked pages, and other navigation failures later.
8. As an accessibility analyst, I want failed attempts retained without findings or scores, so that History records the attempt without suggesting an accessibility assessment completed.
9. As an accessibility analyst, I want scans to begin after DOM content is loaded with only a bounded stabilization wait, so that active network connections do not prevent otherwise usable pages from being scanned.
10. As an accessibility analyst, I want the scan to report a timeout when no scannable state is reached within the bound, so that incomplete attempts have a clear and truthful outcome.
11. As an accessibility analyst, I want the axe-core version and scan configuration recorded for a comparison run, so that changes in results can be interpreted against engine and rule-set changes.
12. As a maintainer, I want the axe-core upgrade compared against a fixed set of pages and captured result measures, so that regressions or expanded rule coverage are visible before treating the upgrade as an improvement.

## Implementation Decisions

- A completed scan remains the only scan state that can contain findings or manual-review items. A failed scan has an explicit status and human-readable reason, and does not expose a score or findings.
- Replace the severity score with counts of affected elements grouped by axe-core impact, plus a separate count of distinct rules.
- Replace WCAG A/AA/AAA percentages with the number/list of distinct WCAG criteria associated with findings. Do not label criteria without findings as passed. Do not imply AAA coverage.
- Preserve axe-core `incomplete` results in the scan response and display them separately as “Needs manual review”; they are not violations and do not contribute to violation counts.
- Persist failed attempts in History, including the submitted URL, status, reason, and timestamp. Completed scans retain their findings and manual-review data.
- Classify known outcomes such as timed out, rate limited (HTTP 429), and blocked distinctly where evidence supports that classification. Keep a safe fallback failure status and reason for other navigation or execution failures.
- Navigate using `DOMContentLoaded`, then apply a bounded stabilization period for dynamic content. If the page cannot reach a scannable state within the bound, complete the attempt as a timeout failure.
- Keep the axe-core dependency upgrade separate from the report and navigation behavior changes. Use the fixed baseline to compare version, configured tags, distinct violation rules, affected elements by impact, WCAG criteria with findings, and incomplete/manual-review results. Treat counts as observations, not a pass/fail score.
- Do not change the semantics of the accessibility scan into a site-wide audit or WCAG certification; it remains an automated evaluation of one rendered page state.

## Testing Decisions

- Test externally observable response and persistence behavior at the existing backend controller/service seams. A successful scan must expose findings/manual-review data and no score; a failed scan must persist and return its explicit status and reason without findings or a score.
- Use the existing controller and scan-service tests as prior art. Cover completed, empty-result, timeout, rate-limit, blocked, and fallback failure outcomes, including History retrieval.
- At the navigation seam, verify that `DOMContentLoaded` plus the bounded stabilization policy permits scanning without waiting for global network idle, and that exhaustion of the bound produces a timeout status.
- Verify the React report in the browser using representative completed-with-findings, completed-with-no-findings, manual-review, and failed-history examples. Confirm legacy score and percentage displays are absent and that failed attempts do not resemble completed scans.
- Do not add a frontend test framework for this work. Do not add test seams below the existing backend and browser seams unless implementation reveals those seams cannot express the approved external behavior.
- For the axe-core upgrade, compare the same fixed pages and configurations before and after; record engine version, configured tags, rules, affected elements by impact, WCAG criteria with findings, and incomplete results. Do not assert identical counts across engine versions.

## Out of Scope

- Declaring a page or site WCAG conformant or certified.
- Treating a missing automated finding as proof that a WCAG criterion passes.
- Running AAA rules as part of this change.
- Crawling every page of a site or testing every interactive state.
- Changing the configured rule tags except as required to preserve the existing scan scope during the engine upgrade.
- Replacing axe-core or Sitefolio, or making their scores numerically comparable.
- Building a new frontend test framework.

## Further Notes

- The current comparison record and implementation analysis are in `docs/qa/2026-10-27/`.
- Existing open issues #17, #18, #19, and #27 encode older score/conformance behavior. Their overlap should be reviewed during issue publication; this spec does not authorize editing or closing those issues.
- Proposed delivery order: (1) trustworthy completed-scan report data and presentation; (2) persisted failure outcomes and bounded navigation behavior; (3) axe-core upgrade and fixed-baseline comparison.
