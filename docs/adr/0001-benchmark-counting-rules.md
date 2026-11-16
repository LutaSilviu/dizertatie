# ADR 0001: How benchmark true positives, false positives and false negatives are counted

Status: accepted (2026-11-16)

## Context

Benchmark metrics compare each method's findings on a page against that page's ground-truth labels. A finding matches a label when both have the same WCAG criterion and the same element selector.

Two behaviours made the reported numbers wrong:

- Two findings with the same criterion and selector (for example axe's `aria-allowed-attr` and `aria-prohibited-attr`, both 4.1.2 on one element) each counted as a true positive against one label. True positives could exceed the number of labels, inflating precision and recall (issue #34).
- A scan that was blocked, rate limited, timed out or otherwise failed has no findings. Its page was still scored, so every label on it counted as a false negative and recall was understated for pages the scanner never reached (issue #33).

## Decision

1. **One prediction per key.** For each method and page, findings are reduced to distinct `criterion|selector` keys before counting. A label is matched at most once. Extra findings with the same key are neither true nor false positives: they describe the same element and criterion, so they carry no additional claim.
2. **No evidence, no score.** A method's scan that did not complete on a page is left out of that method's counts for that page. The run's error summary reports how many pages each method failed on, so the denominator is visible. A method with no completed page is omitted from the metrics.
3. **Run and page status.** A benchmark page is `FAILED` when none of the requested methods completed on it. A run is `FAILED` when no method completed on any page, otherwise `COMPLETE` with an error summary.

4. **AI findings name elements the way axe does.** Before an AI finding is stored, its selector is resolved in the page and replaced by the `axe.utils.getSelector` name of each element it matches, one finding per element. Matching stays an exact comparison of criterion and selector, but no longer depends on the model guessing axe's selector style. A selector matching nothing is kept as written.

## Consequences

- AI and COMBINED results from before rule 4 are not comparable with later ones: AI findings could almost never match a label or merge with an axe finding.

- Precision and recall from runs made before this change are not comparable with later runs when a page had duplicate keys or failed scans. Re-run benchmarks whose numbers are quoted.
- Recall is now computed over the pages a method actually scanned. When methods fail on different pages, their recall figures cover different page sets; compare methods on runs where the error summary is empty.

## Not decided here

Issue #41: an axe rule that maps to two WCAG criteria (for example `link-name`: 2.4.4 and 4.1.2) keeps only the first criterion. Whether to emit one finding per criterion or store a list of criteria changes what counts as a match, and is left open. With rule 1 above, emitting one finding per criterion would no longer double-count a label, but the extra criterion would count as a false positive wherever the dataset labels only one of the two.
