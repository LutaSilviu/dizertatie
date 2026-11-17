# Accessibility Analysis

This context evaluates rendered web pages for automatically detectable accessibility issues and presents results for human remediation.

## Language

**Accessibility scan**:
An automated evaluation of a rendered page state using axe-core. A scan can complete with findings, complete with no findings, or fail before producing a result; only completed scans contain accessibility findings.
_Avoid_: compliance certification

**Finding**:
One affected element reported by an accessibility rule. A rule can produce multiple findings when it affects multiple elements; the number of affected elements and the number of distinct rules are separate measures.
_Avoid_: issue type (when referring to affected elements)

**Manual review item**:
An axe-core result marked `incomplete`, where automation cannot decide whether the element passes or fails and a human must review it.
_Avoid_: violation

**WCAG criterion with findings**:
A WCAG success criterion associated with one or more automated findings. A criterion with no automated findings is not thereby confirmed as passed.
_Avoid_: passed criterion (based only on an automated scan)
