# Dataset: Ground-Truth Accessibility Labels

This file documents the ground truth that `MetricsService` scores benchmark runs against. The labels
live in `backend/src/main/resources/dataset/ground-truth-labels.json`. `DatasetSeeder` inserts them on
boot, skipping any row whose `url`, `wcagCriterion` and `elementSelector` already exist.
`DatasetSeederFixtureTest` checks that every label's page exists in the fixtures and that its
selector matches exactly one element there.

---

## Pages

The pages come from the W3C WAI "Before and After Demonstration" (BAD), a site built to fail WCAG on
purpose ("before") next to a repaired version ("after").

| Page URL | Role | Labels |
|----------|------|--------|
| http://localhost:8080/fixtures/wai-bad/before/home.html | inaccessible home page | 113 |
| http://localhost:8080/fixtures/wai-bad/before/news.html | inaccessible news page | 79 |
| http://localhost:8080/fixtures/wai-bad/before/tickets.html | inaccessible ticket page | 71 |
| http://localhost:8080/fixtures/wai-bad/before/survey.html | inaccessible survey/form page | 93 |
| http://localhost:8080/fixtures/wai-bad/after/home.html | accessible home page (negative example) | 0 |

**License:** [W3C Document License](https://www.w3.org/Consortium/Legal/2015/doc-license)

**Source of the pages:** the backend serves them from an unmodified local copy
(`backend/src/main/resources/static/fixtures/wai-bad/`, taken from Internet Archive snapshots of
https://www.w3.org/WAI/demos/bad/). The live site sits behind Cloudflare bot protection. It answers
headless browsers with a "Just a moment..." challenge page, so scans of the live URLs audited the
challenge page and not the demo.

**Negative example:** `after/home.html` has no labels. `MetricsService` counts a finding with no
matching label as a false positive and a label with no matching finding as a false negative. On a page
with no labels, every finding is therefore a false positive and there are no false negatives. The page
can't be derived from the labels, so it is listed in the JSON with `"negativeExample": true`.
`BenchmarkRunnerService.builtinUrls()` adds it to the built-in benchmark set. W3C's evaluation report
for this page passes every WCAG 2.0 criterion.

---

## Annotation source and protocol

The labels were rebuilt from W3C's own descriptions of the intended failures. These are the
annotated pages `before/annotated/{home,news,tickets,survey,template}.html` and the evaluation
reports `before/reports/*.html`, read from Internet Archive snapshots of
https://www.w3.org/WAI/demos/bad/ (W3C, 20 February 2012 edition). They are not part of the local
fixtures. Each finding was then checked against the local fixture markup.

* **Unit.** A label is one (page, WCAG success criterion, element) failure. A failure covering
  several criteria (for example F55, or F89 for 2.4.4 and 4.1.2) produces one label per criterion.
* **Scope.**
  * Only WCAG 2.0 Level A and AA criteria are labelled, which matches the tags `AxeService` runs.
    AAA criteria and W3C "advisory techniques" are excluded.
  * Only the demo region (`#page`) is labelled. W3C's wrapper (`#meta-header`, `#meta-footer`) is
    not, except for the page-level `html` and `body` labels.
* **Sources** (stored in the label's `annotator` column):
  * `w3c-annotation`: W3C's annotated page names this element (or, for template notes, this
    template element) as failing this criterion.
  * `manual`: another instance on the same page of exactly the same failure W3C annotated (the same
    WCAG failure technique, for example F65 "img without alt"), verified in the fixture markup.
    For colour contrast, the ratio was computed from the rendered colours: #41545D on #A9B8BF is
    3.88:1, against a required 4.5:1.
  * `w3c-report`: W3C's evaluation report marks the criterion failed for the page and the markup shows
    the element (only `3.1.1` on `html`, which has no `lang`).
* **Labels were not derived from axe.** Elements were selected by the rules above and by DOM queries
  written for this dataset (see the rationale column). axe was run only afterwards, to fill in the
  "axe" column.
* **Selector format.** `MetricsService` matches a finding to a label only when the criterion and the
  selector **string** are identical. Each element is therefore named with `axe.utils.getSelector`
  (axe-core 4.9.0), which is the generator axe uses for its own finding targets. This choice only
  names the element; it does not decide what gets labelled. AI findings are brought into the same
  format before they are stored: each selector the model writes is resolved in the page and
  replaced by the `axe.utils.getSelector` name of every element it matches (`AiFindingGrounder`).
* **Severity** is the maintainer's judgement, not axe's impact:
  * critical: the content or function is unavailable, such as unnamed links or controls,
    informative images with no alternative, or links you can't use from the keyboard.
  * serious: the alternative is misleading or the structure or contrast is insufficient.
  * moderate: decorative-image noise, presentational markup, bypass blocks, reading order.
  * minor: a non-null alt on a decorative bullet.
* **Confidence** is 0.9 for every label, unchanged from the original seed.

---

## Claims from the previous version of this file that were dropped

The original 22 labels (commits fa83c7f and 1876721) were written without access to the page markup.
None of the class names they used (`p.light-text`, `a.nav-link`, `div.accordion`, `span.date`,
`img.thumbnail`, `label.hint-text`, ...) occurs in the pages. They were removed by migration
`V6__ground_truth_drop_invented_labels.sql`.

| Old claim | Outcome |
|-----------|---------|
| home 1.4.3 (`p.light-text`, `a.nav-link`) | Replaced by the two real low-contrast teaser headings (#41545D on #A9B8BF). |
| home 1.3.1 (`font[size='+2']`), 4.1.2 (`div[onclick]`) | No such elements. 1.3.1 is relabelled on the real pseudo-headings, list and columns. 4.1.2 is relabelled on the unnamed links and QUICKMENU select. |
| after/home 1.1.1 (`img[alt]`, severity "none") | Removed. It was counted as an expected violation. The page is now a label-free negative example. |
| survey 2.4.6 (labels) | **Dropped.** 2.4.6 requires labels that exist to be descriptive. The survey's problem is controls whose labels aren't associated with them, which is 1.3.1/4.1.2 (F68) and 3.3.2 per W3C. W3C's report fails 2.4.6 site-wide but names no element. |
| survey 4.1.2 (`input[type='submit']:not([value])`) | **Dropped.** The submit button has `value="submit"`. 4.1.2 is relabelled on the unlabelled radios, text inputs and selects. |
| survey 1.4.3 (`label.hint-text`) | **Dropped.** The survey has no `label` elements and no text below 4.5:1. |
| ticket.html (4 labels) | The page is `before/tickets.html`; `ticket.html` does not exist. |
| tickets 2.4.6 (unlabelled text inputs) | **Dropped.** The tickets page has no text inputs. |
| tickets 1.3.1 (`table:not([role='presentation'])`) | Replaced by the two data tables W3C annotates (concert dates, prices) and the other 1.3.1 notes. |
| tickets 2.1.1 (`div.clickable[onclick]`) | No such element. 2.1.1 is relabelled on the `onfocus="blur()"` links (F55, template note 08). |
| tickets 1.1.1 (`img.decorative`) | Replaced by the real images (heading images of text, phone-number image, spacers). |
| news 1.3.3 (`p.instruction`) | **Dropped.** The news page has no instructions that rely on shape, colour or position, and W3C doesn't fail 1.3.3. |
| news 1.4.3 (`span.date`) | **Dropped.** Nothing on the news page falls below 4.5:1, and W3C doesn't annotate contrast there. |
| news 4.1.2 (`div.accordion`) | No accordion exists. 4.1.2 is relabelled on the unnamed navigation links and QUICKMENU select. |
| news 1.1.1 (`img.thumbnail`) | Replaced by the real images (chart with empty alt, photo with a non-equivalent alt, spacers). |

**W3C failures deliberately left unlabelled:**

* **AAA or advisory only:** 1.4.8, 2.4.9, 2.4.10, 3.1.4, and news note 08 (deprecated markup).
* **2.4.2 Page Titled:** the fixture's `<title>` carries W3C's suffix (for example
  "[Inaccessible Home Page]"), so the page as served does identify itself.
* **Survey note 05 (2.1.1 / 2.4.1):** the long country list is still keyboard-operable. The note cites
  only sufficient techniques, not a failure.
* **Template note 03's 2.1.1 (F54) on the QUICKMENU select:** only its 3.2.2 failure is labelled.
* **Template note 01 (nested layout tables):** advisory only.
* **Images of text in the navigation (1.4.5):** W3C doesn't annotate them for 1.4.5. Only the ticket
  headings and phone-number images are labelled, as W3C annotated them.
* **Site-wide report failures with no element in W3C's notes** (2.4.6, 3.2.4, 4.1.1): these are
  not labelled.

---

## Caveats for the benchmark

1. **Exact-string matching.** `MetricsService` compares selector strings, not elements. AI
   selectors are therefore resolved to elements first (`AiFindingGrounder`): `img.teaser` becomes
   `img[src$="teaser_right1.jpg"]`, and a selector that covers several elements becomes one finding
   per element. A selector the model wrote that matches nothing in the page is kept as written and
   counts as a false positive. A broad selector (`#main a`) claims every element it matches, so
   over-general AI findings lower AI precision.
2. **One criterion per axe finding.** `AxeResultParser` keeps only the first `wcagNNN` tag. For
   example, axe's `link-name` is recorded as 2.4.4, so the 4.1.2 label on the same link can't be met
   by axe.
3. **WCAG 2.2 findings outside the label scope.** `AxeService` also runs `wcag22aa`. axe's
   `target-size` (2.5.8) fires on W3C's wrapper links on every page, and on two list links in
   `after/home.html`. These count as false positives because the labels are WCAG 2.0-scoped.
4. **What the "axe" column means.** "yes" means axe-core 4.9.0, run with `AxeService`'s tags,
   reports this element under this criterion, so `MetricsService` would score it as a true positive.
   "no (axe flags this element only as ...)" means axe reports the element, but under another rule and criterion.

---

## Labels

The canonical data is `ground-truth-labels.json`. This table is generated from the same run and adds
the axe column.

### before/home.html (113 labels; axe detects 44)

| # | Criterion | Level | Severity | Selector | Source | axe | Why it fails |
|---|-----------|-------|----------|----------|--------|-----|--------------|
| 1 | 1.1.1 | A | serious | `img[src$="top_logo.gif"]` | w3c-annotation | no | template note 02 (F30): logo alt is a verbose description, not an equivalent |
| 2 | 1.1.1 | A | critical | `img[name="nav_home"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 3 | 1.1.1 | A | critical | `img[src$="nav_news.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 4 | 1.1.1 | A | critical | `img[name="nav_facts"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 5 | 1.1.1 | A | critical | `img[src$="nav_survey.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 6 | 1.1.1 | A | critical | `img[src$="teaser_right1.jpg"]` | w3c-annotation | yes | note 02 (F65): teaser image has no text alternative |
| 7 | 1.1.1 | A | critical | `img[src$="teaser_right2.jpg"]` | manual | yes | same failure as note 02 (F65): teaser image has no text alternative |
| 8 | 1.1.1 | A | serious | `.image[title="image"]:nth-child(3)` | w3c-annotation | no | note 04 (F3/F30): informative CSS background image; only "text alternative" is title="image" |
| 9 | 1.1.1 | A | serious | `.image[title="image"]:nth-child(1)` | manual | no | same failure as note 04 (F3/F30): CSS background photo with title="image" only |
| 10 | 1.1.1 | A | serious | `.image[title="image"]:nth-child(2)` | manual | no | same failure as note 04 (F3/F30): CSS background photo with title="image" only |
| 11 | 1.1.1 | A | minor | `img[src$="list_bullets.gif"][alt="bullet"][border="0"]:nth-child(1)` | w3c-annotation | no | note 08 (F39): decorative bullet image has alt="bullet" |
| 12 | 1.1.1 | A | minor | `img[src$="list_bullets.gif"][alt="bullet"][border="0"]:nth-child(4)` | w3c-annotation | no | note 08 (F39): decorative bullet image has alt="bullet" |
| 13 | 1.1.1 | A | serious | `img[src$="telefon_white_bg.gif"]` | w3c-annotation | no | note 10 (F30/F71): phone-number image has wrong alt "1234 56789" |
| 14 | 1.1.1 | A | moderate | `img[src$="border_left_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 15 | 1.1.1 | A | moderate | `img[src$="border_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 16 | 1.1.1 | A | moderate | `img[src$="border_right_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 17 | 1.1.1 | A | moderate | `img[src$="border_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 18 | 1.1.1 | A | moderate | `img[src$="top_logo_next_end.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 19 | 1.1.1 | A | moderate | `img[src$="top_weather.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 20 | 1.1.1 | A | moderate | `img[src$="top_logo_next_start.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 21 | 1.1.1 | A | moderate | `img[src$="mark.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 22 | 1.1.1 | A | moderate | `td[colspan="5"][background="./img/marker2_w.gif"][width="780px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 23 | 1.1.1 | A | moderate | `td[width="155px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 24 | 1.1.1 | A | moderate | `img[width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 25 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(3) > td[width="154px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 26 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(5) > td[width="154px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 27 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(7) > td[width="154px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 28 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(9) > td[width="154px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 29 | 1.1.1 | A | moderate | `td[width="20px"]:nth-child(2) > img[src$="blank_5x5.gif"][height="5px"][width="20px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 30 | 1.1.1 | A | moderate | `.newsheadline:nth-child(1) > img[src$="headline_middle.gif"][width="23"][height="24"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 31 | 1.1.1 | A | moderate | `.newsheadline:nth-child(2) > img[src$="headline_middle.gif"][width="23"][height="24"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 32 | 1.1.1 | A | moderate | `.newsheadline:nth-child(3) > img[src$="headline_middle.gif"][width="23"][height="24"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 33 | 1.1.1 | A | moderate | `td[width="20px"]:nth-child(4) > img[src$="blank_5x5.gif"][height="5px"][width="20px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 34 | 1.1.1 | A | moderate | `img[src$="marker2_t.gif"][height="30px"][width="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 35 | 1.1.1 | A | moderate | `td[width="151px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 36 | 1.1.1 | A | moderate | `td[background="./img/marker2_w.gif"][width="150px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 37 | 1.1.1 | A | moderate | `img[src$="border_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 38 | 1.1.1 | A | moderate | `img[src$="border_bottom_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 39 | 1.1.1 | A | moderate | `img[src$="border_bottom.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 40 | 1.1.1 | A | moderate | `img[src$="border_bottom_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 41 | 1.3.1 | A | moderate | `.headline` | w3c-annotation | no | template note 06 (F2): visual heading marked up as <p class="headline"> |
| 42 | 1.3.1 | A | moderate | `.subheadline` | manual | no | same failure as template note 06 (F2): visual sub-heading marked up as <p class="subheadline"> |
| 43 | 1.3.1 | A | moderate | `p:nth-child(7) > span` | w3c-annotation | no | note 09 (F2): "Killer bees"/"Onions" styled as a list but not marked up as one |
| 44 | 1.3.1 | A | moderate | `.newsbar:nth-child(4)` | w3c-annotation | no | note 06 (F2): three visual columns are one run of text in the markup |
| 45 | 1.3.2 | A | moderate | `.newsbar:nth-child(4)` | w3c-annotation | no | note 06 (F1/F49): reading sequence of the three story columns is not meaningful |
| 46 | 1.4.1 | A | moderate | `a[href$="tickets.html"][onfocus="blur();"]` | w3c-annotation | no | template note 09 (F73): link distinguished from surrounding text by colour only |
| 47 | 1.4.1 | A | moderate | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(2)` | manual | no | same failure as template note 09 (F73): inline link distinguished by colour only |
| 48 | 1.4.1 | A | moderate | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(5)` | manual | no | same failure as template note 09 (F73): inline link distinguished by colour only |
| 49 | 1.4.1 | A | moderate | `a[href$="survey.html"][onfocus="blur();"]` | manual | no | same failure as template note 09 (F73): inline link distinguished by colour only |
| 50 | 1.4.3 | AA | serious | `tr[height="25px"]:nth-child(2) > td[bgcolor="#A9B8BF"][width="150px"] > font[color="#41545D"][size="2"] > b` | w3c-annotation | yes | template note 10: #41545D on #A9B8BF = 3.88:1 for 13px bold text |
| 51 | 1.4.3 | AA | serious | `tr[height="25px"]:nth-child(7) > td[bgcolor="#A9B8BF"][width="150px"] > font[color="#41545D"][size="2"] > b` | w3c-annotation | yes | template note 10: #41545D on #A9B8BF = 3.88:1 for 13px bold text |
| 52 | 2.1.1 | A | critical | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 53 | 2.1.1 | A | critical | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 54 | 2.1.1 | A | critical | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 55 | 2.1.1 | A | critical | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 56 | 2.1.1 | A | critical | `.newsheadline:nth-child(1) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 57 | 2.1.1 | A | critical | `.newsheadline:nth-child(2) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 58 | 2.1.1 | A | critical | `.newsheadline:nth-child(3) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 59 | 2.1.1 | A | critical | `.story:nth-child(1) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 60 | 2.1.1 | A | critical | `.story:nth-child(2) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 61 | 2.1.1 | A | critical | `.story:nth-child(3) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 62 | 2.1.1 | A | critical | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(2)` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 63 | 2.1.1 | A | critical | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(5)` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 64 | 2.1.1 | A | critical | `a[href$="tickets.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 65 | 2.1.1 | A | critical | `a[href$="survey.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 66 | 2.4.1 | A | moderate | `body` | w3c-annotation | no | template note 05: no mechanism to bypass the repeated CityLights header/navigation |
| 67 | 2.4.4 | A | critical | `#home > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 68 | 2.4.4 | A | critical | `#news > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 69 | 2.4.4 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 70 | 2.4.4 | A | critical | `#survey > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 71 | 2.4.4 | A | critical | `.story:nth-child(1) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | yes | note 07 (F89): link whose only content is an image with alt="" |
| 72 | 2.4.4 | A | critical | `.story:nth-child(2) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | yes | note 07 (F89): link whose only content is an image with alt="" |
| 73 | 2.4.4 | A | critical | `.story:nth-child(3) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | yes | note 07 (F89): link whose only content is an image with alt="" |
| 74 | 2.4.4 | A | moderate | `a[href$="tickets.html"][onfocus="blur();"]` | w3c-annotation | no | note 05 / template note 09 (F63): link text "Read More..." does not convey its purpose |
| 75 | 2.4.4 | A | moderate | `a[href$="survey.html"][onfocus="blur();"]` | manual | no | same failure as note 05 (F63): second "Read More..." link |
| 76 | 2.4.7 | AA | serious | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 77 | 2.4.7 | AA | serious | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 78 | 2.4.7 | AA | serious | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 79 | 2.4.7 | AA | serious | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 80 | 2.4.7 | AA | serious | `.newsheadline:nth-child(1) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 81 | 2.4.7 | AA | serious | `.newsheadline:nth-child(2) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 82 | 2.4.7 | AA | serious | `.newsheadline:nth-child(3) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 83 | 2.4.7 | AA | serious | `.story:nth-child(1) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 84 | 2.4.7 | AA | serious | `.story:nth-child(2) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 85 | 2.4.7 | AA | serious | `.story:nth-child(3) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 86 | 2.4.7 | AA | serious | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(2)` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 87 | 2.4.7 | AA | serious | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(5)` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 88 | 2.4.7 | AA | serious | `a[href$="tickets.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 89 | 2.4.7 | AA | serious | `a[href$="survey.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): focus is removed so no focus indicator is visible |
| 90 | 3.1.1 | A | serious | `html` | w3c-report | yes | html element has no lang attribute |
| 91 | 3.2.1 | A | moderate | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 92 | 3.2.1 | A | moderate | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 93 | 3.2.1 | A | moderate | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 94 | 3.2.1 | A | moderate | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 95 | 3.2.1 | A | moderate | `.newsheadline:nth-child(1) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 96 | 3.2.1 | A | moderate | `.newsheadline:nth-child(2) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 97 | 3.2.1 | A | moderate | `.newsheadline:nth-child(3) > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 98 | 3.2.1 | A | moderate | `.story:nth-child(1) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 99 | 3.2.1 | A | moderate | `.story:nth-child(2) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 100 | 3.2.1 | A | moderate | `.story:nth-child(3) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 101 | 3.2.1 | A | moderate | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(2)` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 102 | 3.2.1 | A | moderate | `a[href$="offsite.html"][target="_blank"][onfocus="blur();"]:nth-child(5)` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 103 | 3.2.1 | A | moderate | `a[href$="tickets.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 104 | 3.2.1 | A | moderate | `a[href$="survey.html"][onfocus="blur();"]` | w3c-annotation | no | template note 08 (F55): receiving focus triggers a change (blur) |
| 105 | 3.2.2 | A | serious | `select` | w3c-annotation | no (axe flags this element only as select-name 4.1.2) | template note 03 (F36): selecting a QUICKMENU option navigates away |
| 106 | 4.1.2 | A | serious | `select` | manual | yes | QUICKMENU select has no accessible name (no label/title) |
| 107 | 4.1.2 | A | critical | `#home > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 108 | 4.1.2 | A | critical | `#news > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 109 | 4.1.2 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 110 | 4.1.2 | A | critical | `#survey > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 111 | 4.1.2 | A | critical | `.story:nth-child(1) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | note 07 (F89): link has no accessible name |
| 112 | 4.1.2 | A | critical | `.story:nth-child(2) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | note 07 (F89): link has no accessible name |
| 113 | 4.1.2 | A | critical | `.story:nth-child(3) > span > a[href$="news.html"][onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | note 07 (F89): link has no accessible name |

### before/news.html (79 labels; axe detects 45)

| # | Criterion | Level | Severity | Selector | Source | axe | Why it fails |
|---|-----------|-------|----------|----------|--------|-----|--------------|
| 1 | 1.1.1 | A | serious | `img[src$="top_logo.gif"]` | w3c-annotation | no | template note 02 (F30): logo alt is a verbose description, not an equivalent |
| 2 | 1.1.1 | A | critical | `img[name="nav_home"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 3 | 1.1.1 | A | critical | `img[src$="nav_news.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 4 | 1.1.1 | A | critical | `img[name="nav_facts"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 5 | 1.1.1 | A | critical | `img[src$="nav_survey.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 6 | 1.1.1 | A | moderate | `img[src$="border_left_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 7 | 1.1.1 | A | moderate | `img[src$="border_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 8 | 1.1.1 | A | moderate | `img[src$="border_right_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 9 | 1.1.1 | A | moderate | `img[src$="border_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 10 | 1.1.1 | A | moderate | `img[src$="top_logo_next_end.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 11 | 1.1.1 | A | moderate | `img[src$="top_weather.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 12 | 1.1.1 | A | moderate | `img[src$="top_logo_next_start.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 13 | 1.1.1 | A | moderate | `img[src$="mark.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 14 | 1.1.1 | A | moderate | `td[colspan="5"][width="780px"][background="./img/marker2_w.gif"] > img[width="78px"][src$="marker2_w.gif"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 15 | 1.1.1 | A | moderate | `td[width="155px"][background="./img/marker2_w.gif"] > img[width="78px"][src$="marker2_w.gif"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 16 | 1.1.1 | A | moderate | `img[height="30px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 17 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(3) > td[width="154px"][background="./img/marker2_w.gif"] > img[width="78px"][src$="marker2_w.gif"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 18 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(5) > td[width="154px"][background="./img/marker2_w.gif"] > img[width="78px"][src$="marker2_w.gif"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 19 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(7) > td[width="154px"][background="./img/marker2_w.gif"] > img[width="78px"][src$="marker2_w.gif"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 20 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(9) > td[width="154px"][background="./img/marker2_w.gif"] > img[width="78px"][src$="marker2_w.gif"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 21 | 1.1.1 | A | moderate | `img[src$="blank_5x5.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 22 | 1.1.1 | A | moderate | `table[cellspacing="0"][cellpadding="0"][width="100%"] > tbody > tr:nth-child(1) > td[background="./img/marker2_t.gif"][rowspan="9"][valign="top"] > img[height="30"][src$="marker2_t.gif"][width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 23 | 1.1.1 | A | moderate | `table[cellspacing="0"][cellpadding="0"][width="100%"] > tbody > tr:nth-child(1) > td[background="./img/marker2_w.gif"] > img[height="1"][width="78"][src$="marker2_w.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 24 | 1.1.1 | A | moderate | `table[cellspacing="0"][cellpadding="0"][width="100%"] > tbody > tr[bgcolor="#ededed"] > td:nth-child(1) > .headline > img[height="24"][src$="headline_middle.gif"][width="23"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 25 | 1.1.1 | A | moderate | `tr[bgcolor="#ededed"] > td[background="./img/marker2_t.gif"][rowspan="9"][valign="top"] > img[height="30"][src$="marker2_t.gif"][width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 26 | 1.1.1 | A | moderate | `table[cellspacing="0"][cellpadding="0"][width="100%"] > tbody > tr:nth-child(4) > td[width="275"][background="./img/marker2_w.gif"] > img[height="1"][width="78"][src$="marker2_w.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 27 | 1.1.1 | A | moderate | `table[width="49%"] > tbody > tr:nth-child(1) > td[background="./img/marker2_t.gif"][rowspan="9"][valign="top"]:nth-child(1) > img[height="30"][src$="marker2_t.gif"][width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 28 | 1.1.1 | A | moderate | `table[width="49%"] > tbody > tr:nth-child(1) > td[background="./img/marker2_w.gif"] > img[height="1"][width="78"][src$="marker2_w.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 29 | 1.1.1 | A | moderate | `table[width="49%"] > tbody > tr:nth-child(1) > td[background="./img/marker2_t.gif"][rowspan="9"][valign="top"]:nth-child(3) > img[height="30"][src$="marker2_t.gif"][width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 30 | 1.1.1 | A | moderate | `td[width="300"] > .headline > img[height="24"][src$="headline_middle.gif"][width="23"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 31 | 1.1.1 | A | moderate | `table[width="49%"] > tbody > tr:nth-child(4) > td[width="275"][background="./img/marker2_w.gif"] > img[height="1"][width="78"][src$="marker2_w.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 32 | 1.1.1 | A | moderate | `td[width="48%"] > table[cellspacing="0"][cellpadding="0"][border="0"] > tbody > tr:nth-child(1) > td[background="./img/marker2_t.gif"][rowspan="9"][valign="top"]:nth-child(1) > img[height="30"][src$="marker2_t.gif"][width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 33 | 1.1.1 | A | moderate | `td[width="48%"] > table[cellspacing="0"][cellpadding="0"][border="0"] > tbody > tr:nth-child(1) > td[background="./img/marker2_w.gif"] > img[height="1"][width="78"][src$="marker2_w.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 34 | 1.1.1 | A | moderate | `td[width="48%"] > table[cellspacing="0"][cellpadding="0"][border="0"] > tbody > tr:nth-child(1) > td[background="./img/marker2_t.gif"][rowspan="9"][valign="top"]:nth-child(3) > img[height="30"][src$="marker2_t.gif"][width="1"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 35 | 1.1.1 | A | moderate | `td > img[height="24"][src$="headline_middle.gif"][width="23"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 36 | 1.1.1 | A | moderate | `td[width="240"][background="./img/marker2_w.gif"] > img[height="1"][width="78"][src$="marker2_w.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 37 | 1.1.1 | A | moderate | `img[src$="border_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 38 | 1.1.1 | A | moderate | `img[src$="border_bottom_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 39 | 1.1.1 | A | moderate | `img[src$="border_bottom.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 40 | 1.1.1 | A | moderate | `img[src$="border_bottom_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 41 | 1.1.1 | A | serious | `img[src$="chart1.jpg"]` | w3c-annotation | no | note 09 (F30): informative chart has alt="" |
| 42 | 1.1.1 | A | serious | `img[src$="blanca.jpg"]` | w3c-annotation | no | note 10: alt carries information not conveyed visually, so it is not an equivalent |
| 43 | 1.3.1 | A | moderate | `font[size="5"][color="#FFFFFF"] > .headline` | w3c-annotation | no | note 01 (F2): "Heat wave linked to temperatures" styled as heading, not marked up |
| 44 | 1.3.1 | A | moderate | `#main > .headline` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 45 | 1.3.1 | A | moderate | `font[size="7"] > .headline` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 46 | 1.3.1 | A | moderate | `div:nth-child(1) > .headline[align="center"]` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 47 | 1.3.1 | A | moderate | `table[cellspacing="0"][cellpadding="0"][width="100%"] > tbody > tr[bgcolor="#ededed"] > td:nth-child(1) > .headline` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 48 | 1.3.1 | A | moderate | `div:nth-child(5) > .headline` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 49 | 1.3.1 | A | moderate | `td[width="300"] > .headline` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 50 | 1.3.1 | A | moderate | `div:nth-child(6) > .headline[align="center"]` | manual | no | same failure as note 01 (F2): visual heading marked up as <p class="headline"> |
| 51 | 1.3.1 | A | moderate | `div:nth-child(1) > span` | w3c-annotation | no | note 07 (F2): emphasis conveyed by italic style instead of <em> |
| 52 | 1.3.2 | A | moderate | `table[cellspacing="0"][cellpadding="0"][width="100%"]` | w3c-annotation | no | note 04 (F1): "Your Shout" box is out of place in the reading order |
| 53 | 1.4.1 | A | moderate | `div:nth-child(2) > a[href$="offsite.html"]` | w3c-annotation | no | note 06 (F73): inline link distinguished by colour only |
| 54 | 1.4.1 | A | moderate | `div:nth-child(1) > a[href$="offsite.html"]` | w3c-annotation | no | note 06 (F73): inline link distinguished by colour only |
| 55 | 2.1.1 | A | critical | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 56 | 2.1.1 | A | critical | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 57 | 2.1.1 | A | critical | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 58 | 2.1.1 | A | critical | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 59 | 2.4.1 | A | moderate | `body` | w3c-annotation | no | template note 05: no mechanism to bypass the repeated CityLights header/navigation |
| 60 | 2.4.4 | A | critical | `#home > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 61 | 2.4.4 | A | critical | `#news > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 62 | 2.4.4 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 63 | 2.4.4 | A | critical | `#survey > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 64 | 2.4.4 | A | moderate | `blockquote > a[href$="offsite.html"]` | w3c-annotation | no | note 05: image-link alt describes the picture, not the link purpose |
| 65 | 2.4.7 | AA | serious | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 66 | 2.4.7 | AA | serious | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 67 | 2.4.7 | AA | serious | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 68 | 2.4.7 | AA | serious | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 69 | 3.1.1 | A | serious | `html` | w3c-report | yes | html element has no lang attribute |
| 70 | 3.2.1 | A | moderate | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 71 | 3.2.1 | A | moderate | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 72 | 3.2.1 | A | moderate | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 73 | 3.2.1 | A | moderate | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 74 | 3.2.2 | A | serious | `select` | w3c-annotation | no (axe flags this element only as select-name 4.1.2) | template note 03 (F36): selecting a QUICKMENU option navigates away |
| 75 | 4.1.2 | A | serious | `select` | manual | yes | QUICKMENU select has no accessible name (no label/title) |
| 76 | 4.1.2 | A | critical | `#home > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 77 | 4.1.2 | A | critical | `#news > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 78 | 4.1.2 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 79 | 4.1.2 | A | critical | `#survey > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |

### before/tickets.html (71 labels; axe detects 35)

| # | Criterion | Level | Severity | Selector | Source | axe | Why it fails |
|---|-----------|-------|----------|----------|--------|-----|--------------|
| 1 | 1.1.1 | A | serious | `img[src$="top_logo.gif"]` | w3c-annotation | no | template note 02 (F30): logo alt is a verbose description, not an equivalent |
| 2 | 1.1.1 | A | critical | `img[name="nav_home"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 3 | 1.1.1 | A | critical | `img[src$="nav_news.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 4 | 1.1.1 | A | critical | `img[name="nav_facts"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 5 | 1.1.1 | A | critical | `img[src$="nav_survey.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 6 | 1.1.1 | A | critical | `p:nth-child(1) > img[border="0"]` | w3c-annotation | yes | note 01 (F65/F71): heading image of text has no alt |
| 7 | 1.1.1 | A | critical | `th > img[border="0"]` | manual | yes | same failure as note 01 (F65/F71): heading image of text has no alt |
| 8 | 1.1.1 | A | moderate | `img[src$="border_left_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 9 | 1.1.1 | A | moderate | `img[src$="border_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 10 | 1.1.1 | A | moderate | `img[src$="border_right_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 11 | 1.1.1 | A | moderate | `img[src$="border_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 12 | 1.1.1 | A | moderate | `img[src$="top_logo_next_end.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 13 | 1.1.1 | A | moderate | `img[src$="top_weather.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 14 | 1.1.1 | A | moderate | `img[src$="top_logo_next_start.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 15 | 1.1.1 | A | moderate | `img[src$="mark.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 16 | 1.1.1 | A | moderate | `td[background="./img/marker2_w.gif"][colspan="5"][width="780px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 17 | 1.1.1 | A | moderate | `td[width="155px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 18 | 1.1.1 | A | moderate | `img[src$="marker2_t.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 19 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(3) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 20 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(5) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 21 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(7) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 22 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(9) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 23 | 1.1.1 | A | moderate | `img[src$="blank_5x5.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 24 | 1.1.1 | A | moderate | `img[src$="border_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 25 | 1.1.1 | A | moderate | `img[src$="border_bottom_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 26 | 1.1.1 | A | moderate | `img[src$="border_bottom.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 27 | 1.1.1 | A | moderate | `img[src$="border_bottom_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 28 | 1.1.1 | A | serious | `div > img[src$="telefon_lightgrey_bg.gif"][alt="music line phone number"][valign="absmiddle"]` | w3c-annotation | no | note 04 (F30): alt "music line phone number" omits the number shown |
| 29 | 1.1.1 | A | serious | `td[width="120px"][align="left"][valign="middle"] > img[src$="telefon_lightgrey_bg.gif"][alt="music line phone number"][valign="absmiddle"]` | w3c-annotation | no | note 04 (F30): alt "music line phone number" omits the number shown |
| 30 | 1.3.1 | A | moderate | `#main > p:nth-child(1)` | w3c-annotation | no | note 01 (F2): image of text acts as a heading but is not marked up as one |
| 31 | 1.3.1 | A | serious | `table[width="250px"]` | w3c-annotation | no | note 02 (F43/F2): header cells of the concert-dates table are not <th> |
| 32 | 1.3.1 | A | moderate | `big` | w3c-annotation | no | note 03 (F2): emphasis via CSS bold instead of <strong> |
| 33 | 1.3.1 | A | serious | `.prices` | w3c-annotation | no | note 05: price table has no caption and no header cells |
| 34 | 1.3.1 | A | moderate | `#terms` | w3c-annotation | no | note 08: terms heading and numbered list not marked up |
| 35 | 1.4.3 | AA | serious | `table[width="250px"] > tbody > tr[bgcolor="#A9B8BF"] > td:nth-child(1) > b > font[color="41545D"]` | w3c-annotation | yes | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 36 | 1.4.3 | AA | serious | `table[width="250px"] > tbody > tr[bgcolor="#A9B8BF"] > td:nth-child(2) > b > font[color="41545D"]` | w3c-annotation | yes | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 37 | 1.4.3 | AA | serious | `.prices > tbody > tr[bgcolor="#A9B8BF"]:nth-child(1) > td:nth-child(1) > b > font[color="41545D"]` | w3c-annotation | no | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 38 | 1.4.3 | AA | serious | `.prices > tbody > tr[bgcolor="#A9B8BF"]:nth-child(1) > td:nth-child(2) > b > font[color="41545D"]` | w3c-annotation | no | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 39 | 1.4.3 | AA | serious | `td:nth-child(3) > b > font[color="41545D"]` | w3c-annotation | no | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 40 | 1.4.3 | AA | serious | `td:nth-child(4) > b > font[color="41545D"]` | w3c-annotation | no | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 41 | 1.4.3 | AA | serious | `td:nth-child(5) > b > font[color="41545D"]` | w3c-annotation | no | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 42 | 1.4.3 | AA | serious | `font[color="41545D"] > font[face="verdana"]` | w3c-annotation | yes | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 43 | 1.4.3 | AA | serious | `tr[bgcolor="#A9B8BF"]:nth-child(7) > td[colspan="5"] > b > font[color="41545D"]` | w3c-annotation | no | note 06: #41545D on #A9B8BF = 3.88:1 for 12-15px bold text |
| 44 | 1.4.5 | AA | moderate | `p:nth-child(1) > img[border="0"]` | w3c-annotation | no (axe flags this element only as image-alt 1.1.1) | note 01: image of text used for a heading |
| 45 | 1.4.5 | AA | moderate | `th > img[border="0"]` | manual | no (axe flags this element only as image-alt 1.1.1) | same failure as note 01: image of text used for a heading |
| 46 | 1.4.5 | AA | moderate | `div > img[src$="telefon_lightgrey_bg.gif"][alt="music line phone number"][valign="absmiddle"]` | w3c-annotation | no | note 04: phone number presented as an image of text |
| 47 | 1.4.5 | AA | moderate | `td[width="120px"][align="left"][valign="middle"] > img[src$="telefon_lightgrey_bg.gif"][alt="music line phone number"][valign="absmiddle"]` | w3c-annotation | no | note 04: phone number presented as an image of text |
| 48 | 2.1.1 | A | critical | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 49 | 2.1.1 | A | critical | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 50 | 2.1.1 | A | critical | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 51 | 2.1.1 | A | critical | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 52 | 2.4.1 | A | moderate | `body` | w3c-annotation | no | template note 05: no mechanism to bypass the repeated CityLights header/navigation |
| 53 | 2.4.4 | A | critical | `#home > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 54 | 2.4.4 | A | critical | `#news > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 55 | 2.4.4 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 56 | 2.4.4 | A | critical | `#survey > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 57 | 2.4.7 | AA | serious | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 58 | 2.4.7 | AA | serious | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 59 | 2.4.7 | AA | serious | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 60 | 2.4.7 | AA | serious | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 61 | 3.1.1 | A | serious | `html` | w3c-report | yes | html element has no lang attribute |
| 62 | 3.2.1 | A | moderate | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 63 | 3.2.1 | A | moderate | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 64 | 3.2.1 | A | moderate | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 65 | 3.2.1 | A | moderate | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 66 | 3.2.2 | A | serious | `select` | w3c-annotation | no (axe flags this element only as select-name 4.1.2) | template note 03 (F36): selecting a QUICKMENU option navigates away |
| 67 | 4.1.2 | A | serious | `select` | manual | yes | QUICKMENU select has no accessible name (no label/title) |
| 68 | 4.1.2 | A | critical | `#home > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 69 | 4.1.2 | A | critical | `#news > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 70 | 4.1.2 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 71 | 4.1.2 | A | critical | `#survey > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |

### before/survey.html (93 labels; axe detects 42)

| # | Criterion | Level | Severity | Selector | Source | axe | Why it fails |
|---|-----------|-------|----------|----------|--------|-----|--------------|
| 1 | 1.1.1 | A | serious | `img[src$="top_logo.gif"]` | w3c-annotation | no | template note 02 (F30): logo alt is a verbose description, not an equivalent |
| 2 | 1.1.1 | A | critical | `img[name="nav_home"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 3 | 1.1.1 | A | critical | `img[src$="nav_news.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 4 | 1.1.1 | A | critical | `img[name="nav_facts"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 5 | 1.1.1 | A | critical | `img[src$="nav_survey.gif"]` | w3c-annotation | yes | template note 07 (F65): navigation image of text has no alt |
| 6 | 1.1.1 | A | moderate | `img[src$="border_left_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 7 | 1.1.1 | A | moderate | `img[src$="border_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 8 | 1.1.1 | A | moderate | `img[src$="border_right_top.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 9 | 1.1.1 | A | moderate | `img[src$="border_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 10 | 1.1.1 | A | moderate | `img[src$="top_logo_next_end.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 11 | 1.1.1 | A | moderate | `img[src$="top_weather.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 12 | 1.1.1 | A | moderate | `img[src$="top_logo_next_start.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 13 | 1.1.1 | A | moderate | `img[src$="mark.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 14 | 1.1.1 | A | moderate | `td[colspan="5"][background="./img/marker2_w.gif"][width="780px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 15 | 1.1.1 | A | moderate | `td[width="155px"][background="./img/marker2_w.gif"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 16 | 1.1.1 | A | moderate | `img[src$="marker2_t.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 17 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(3) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 18 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(5) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 19 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(7) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 20 | 1.1.1 | A | moderate | `tr[height="1px"]:nth-child(9) > td[background="./img/marker2_w.gif"][width="154px"] > img[src$="marker2_w.gif"][width="78px"][height="1px"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 21 | 1.1.1 | A | moderate | `img[src$="blank_5x5.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 22 | 1.1.1 | A | moderate | `img[src$="border_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 23 | 1.1.1 | A | moderate | `img[src$="border_bottom_left.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 24 | 1.1.1 | A | moderate | `img[src$="border_bottom.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 25 | 1.1.1 | A | moderate | `img[src$="border_bottom_right.gif"]` | manual | yes | layout/decorative image without alt="" (F38/F65) |
| 26 | 1.3.1 | A | moderate | `#main > div > .headline:nth-child(1)` | w3c-annotation | no | template note 06 (F2): visual heading marked up as <p class="headline"> |
| 27 | 1.3.1 | A | moderate | `.headline:nth-child(2)` | w3c-annotation | no | template note 06 (F2): visual heading marked up as <p class="headline"> |
| 28 | 1.3.1 | A | moderate | `#data > .headline` | w3c-annotation | no | template note 06 (F2): visual heading marked up as <p class="headline"> |
| 29 | 1.3.1 | A | serious | `input[value="1"]` | w3c-annotation | no (axe flags this element only as label 4.1.2) | note 03 (F68): radio button not associated with its visible label |
| 30 | 1.3.1 | A | serious | `input[value="2"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 31 | 1.3.1 | A | serious | `input[value="3"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 32 | 1.3.1 | A | serious | `input[value="4"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 33 | 1.3.1 | A | serious | `input[value="5"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 34 | 1.3.1 | A | serious | `input[value="6"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 35 | 1.3.1 | A | serious | `select[name="cc"]` | manual | no (axe flags this element only as select-name 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 36 | 1.3.1 | A | serious | `input[value="mr"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 37 | 1.3.1 | A | serious | `input[value="mrs"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 38 | 1.3.1 | A | serious | `#em` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 39 | 1.3.1 | A | serious | `#n` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 40 | 1.3.1 | A | serious | `#ev` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03 (F68): form control not associated with its visible label |
| 41 | 1.3.1 | A | serious | `td[rowspan="4"]` | w3c-annotation | no | note 07 (F43): result-table row headers are <p> elements in one cell |
| 42 | 1.3.2 | A | moderate | `.bordotabella[width="90%"][cellspacing="2"]:nth-child(5) > tbody > tr > td > table[cellspacing="2"][cellpadding="0"] > tbody > tr > td:nth-child(2) > table[cellspacing="2"][cellpadding="0"] > tbody > tr:nth-child(3) > td > table[cellspacing="2"][cellpadding="0"]` | w3c-annotation | no | note 06 (F49): label/field order of the newsletter fields is wrong when linearised |
| 43 | 2.1.1 | A | critical | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 44 | 2.1.1 | A | critical | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 45 | 2.1.1 | A | critical | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 46 | 2.1.1 | A | critical | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): onfocus="blur()" makes the link unusable from the keyboard |
| 47 | 2.4.1 | A | moderate | `body` | w3c-annotation | no | template note 05: no mechanism to bypass the repeated CityLights header/navigation |
| 48 | 2.4.3 | A | moderate | `.bordotabella[width="90%"][cellspacing="2"]:nth-child(5) > tbody > tr > td > table[cellspacing="2"][cellpadding="0"] > tbody > tr > td:nth-child(2) > table[cellspacing="2"][cellpadding="0"] > tbody > tr:nth-child(3) > td > table[cellspacing="2"][cellpadding="0"]` | w3c-annotation | no | note 06: focus order does not follow the visual label order |
| 49 | 2.4.4 | A | critical | `#home > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 50 | 2.4.4 | A | critical | `#news > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 51 | 2.4.4 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 52 | 2.4.4 | A | critical | `#survey > a[onfocus="blur();"]` | manual | yes | navigation link whose only content is an image without alt has no link text |
| 53 | 2.4.7 | AA | serious | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 54 | 2.4.7 | AA | serious | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 55 | 2.4.7 | AA | serious | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 56 | 2.4.7 | AA | serious | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): focus is removed so no focus indicator is visible |
| 57 | 3.1.1 | A | serious | `html` | w3c-report | yes | html element has no lang attribute |
| 58 | 3.2.1 | A | moderate | `#home > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 59 | 3.2.1 | A | moderate | `#news > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 60 | 3.2.1 | A | moderate | `#tickets > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 61 | 3.2.1 | A | moderate | `#survey > a[onfocus="blur();"]` | w3c-annotation | no (axe flags this element only as link-name 2.4.4) | template note 08 (F55): receiving focus triggers a change (blur) |
| 62 | 3.2.2 | A | serious | `select[onchange="location.href = this.value;"]` | w3c-annotation | no (axe flags this element only as select-name 4.1.2) | template note 03 (F36): selecting a QUICKMENU option navigates away |
| 63 | 3.3.2 | A | moderate | `form` | w3c-annotation | no | note 01: no instructions for completing the form |
| 64 | 3.3.2 | A | moderate | `th[colspan="3"]` | w3c-annotation | no | note 02: radio group not grouped/described with fieldset and legend |
| 65 | 3.3.2 | A | serious | `input[value="1"]` | w3c-annotation | no (axe flags this element only as label 4.1.2) | note 03/04: control not associated with a label |
| 66 | 3.3.2 | A | serious | `select[name="cc"]` | w3c-annotation | no (axe flags this element only as select-name 4.1.2) | note 03/04: control not associated with a label |
| 67 | 3.3.2 | A | serious | `input[value="2"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 68 | 3.3.2 | A | serious | `input[value="3"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 69 | 3.3.2 | A | serious | `input[value="4"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 70 | 3.3.2 | A | serious | `input[value="5"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 71 | 3.3.2 | A | serious | `input[value="6"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 72 | 3.3.2 | A | serious | `input[value="mr"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 73 | 3.3.2 | A | serious | `input[value="mrs"]` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 74 | 3.3.2 | A | serious | `#em` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 75 | 3.3.2 | A | serious | `#n` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 76 | 3.3.2 | A | serious | `#ev` | manual | no (axe flags this element only as label 4.1.2) | same failure as note 03: control not associated with a label |
| 77 | 4.1.2 | A | serious | `select[onchange="location.href = this.value;"]` | manual | yes | QUICKMENU select has no accessible name (no label/title) |
| 78 | 4.1.2 | A | critical | `#home > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 79 | 4.1.2 | A | critical | `#news > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 80 | 4.1.2 | A | critical | `#tickets > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 81 | 4.1.2 | A | critical | `#survey > a[onfocus="blur();"]` | manual | no (axe flags this element only as link-name 2.4.4) | navigation link has no accessible name |
| 82 | 4.1.2 | A | critical | `input[value="1"]` | manual | yes | F68: form control has no accessible name |
| 83 | 4.1.2 | A | critical | `input[value="2"]` | manual | yes | F68: form control has no accessible name |
| 84 | 4.1.2 | A | critical | `input[value="3"]` | manual | yes | F68: form control has no accessible name |
| 85 | 4.1.2 | A | critical | `input[value="4"]` | manual | yes | F68: form control has no accessible name |
| 86 | 4.1.2 | A | critical | `input[value="5"]` | manual | yes | F68: form control has no accessible name |
| 87 | 4.1.2 | A | critical | `input[value="6"]` | manual | yes | F68: form control has no accessible name |
| 88 | 4.1.2 | A | critical | `select[name="cc"]` | manual | yes | F68: form control has no accessible name |
| 89 | 4.1.2 | A | critical | `input[value="mr"]` | manual | yes | F68: form control has no accessible name |
| 90 | 4.1.2 | A | critical | `input[value="mrs"]` | manual | yes | F68: form control has no accessible name |
| 91 | 4.1.2 | A | critical | `#em` | manual | yes | F68: form control has no accessible name |
| 92 | 4.1.2 | A | critical | `#n` | manual | yes | F68: form control has no accessible name |
| 93 | 4.1.2 | A | critical | `#ev` | manual | yes | F68: form control has no accessible name |
