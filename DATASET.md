# Dataset: Ground-Truth Accessibility Labels

This file documents every page used as a source for the `GroundTruthLabel` seed data
inserted by `DatasetSeeder` on application boot.

---

## W3C "Before and After Demonstration" (BAD) Pages

The W3C Web Accessibility Initiative (WAI) publishes a set of intentionally inaccessible
("before") and corrected ("after") web pages to illustrate common WCAG failures.

| Page URL | Description | Known issues seeded |
|----------|-------------|---------------------|
| http://localhost:8080/fixtures/wai-bad/before/home.html | BAD – inaccessible home page | 1.1.1, 1.4.3, 1.3.1, 4.1.2, 2.4.1 |
| http://localhost:8080/fixtures/wai-bad/after/home.html | BAD – accessible home page (negative example) | 1.1.1 (none – alt text present) |
| http://localhost:8080/fixtures/wai-bad/before/survey.html | BAD – inaccessible survey/form page | 2.4.6, 1.3.1, 4.1.2, 1.4.3 |
| http://localhost:8080/fixtures/wai-bad/before/ticket.html | BAD – inaccessible ticket-booking page | 2.4.6, 1.1.1, 1.3.1, 2.1.1 |
| http://localhost:8080/fixtures/wai-bad/before/news.html | BAD – inaccessible news page | 1.3.3, 1.4.3, 4.1.2, 2.4.1, 1.1.1 |

**License:** [W3C Document License](https://www.w3.org/Consortium/Legal/2015/doc-license)

**Source:** The pages are served by the backend from an unmodified local copy
(`backend/src/main/resources/static/fixtures/wai-bad/`, taken from Internet Archive snapshots
of https://www.w3.org/WAI/demos/bad/). The live site sits behind Cloudflare bot protection,
which answers headless browsers with a "Just a moment..." challenge page, so scans of the live
URLs audited the challenge page instead of the demo.

**Modifications:** None to the page content. The annotations (WCAG criterion, severity,
element selector) are original work added by this project.

---

## WCAG Criteria Covered

| Criterion | Level | Description |
|-----------|-------|-------------|
| 1.1.1 | A | Non-text Content — images must have alt text |
| 1.3.1 | A | Info and Relationships — headings, tables, form groups must be marked up semantically |
| 1.3.3 | A | Sensory Characteristics — instructions must not rely on shape, colour, or position alone |
| 1.4.3 | AA | Contrast (Minimum) — text must meet 4.5:1 contrast ratio |
| 2.1.1 | A | Keyboard — all functionality must be operable by keyboard |
| 2.4.1 | A | Bypass Blocks — skip-navigation links must be provided |
| 2.4.6 | AA | Headings and Labels — form controls must have descriptive labels |
| 4.1.2 | A | Name, Role, Value — UI components must expose name, role and value to assistive technology |

---

## Seed Metadata

| Field | Value |
|-------|-------|
| `annotator` | `w3c-bad-demo` |
| `confidence` | `0.9` |
| Total labels seeded | 22 |

Labels are inserted idempotently: `DatasetSeeder` checks for an existing row with the
same `url`, `wcagCriterion`, and `elementSelector` before inserting.
