package ro.sluta.accessibility.analysis.axe;

import java.util.List;

public record AxeNodeResult(
        Object target,
        String html,
        String failureSummary,
        String impact,
        List<AxeCheckResult> any,
        List<AxeCheckResult> all,
        List<AxeCheckResult> none) {
    public AxeNodeResult {
        any = any == null ? List.of() : List.copyOf(any);
        all = all == null ? List.of() : List.copyOf(all);
        none = none == null ? List.of() : List.copyOf(none);
    }
}
