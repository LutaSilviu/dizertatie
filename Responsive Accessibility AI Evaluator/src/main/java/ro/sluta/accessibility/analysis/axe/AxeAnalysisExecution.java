package ro.sluta.accessibility.analysis.axe;

import ro.sluta.accessibility.domain.PageSnapshot;

public record AxeAnalysisExecution(PageSnapshot snapshot, AxeAnalysisResult axeResult) {
}
