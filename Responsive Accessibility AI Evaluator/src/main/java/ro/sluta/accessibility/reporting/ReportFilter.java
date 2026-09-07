package ro.sluta.accessibility.reporting;

import ro.sluta.accessibility.domain.CanonicalSeverity;
import ro.sluta.accessibility.domain.FindingSource;
import ro.sluta.accessibility.domain.Viewport;

public record ReportFilter(Viewport viewport, String method, String model, FindingSource source,
                           String wcagCriterion, CanonicalSeverity severity, ReportStatus status) { }
