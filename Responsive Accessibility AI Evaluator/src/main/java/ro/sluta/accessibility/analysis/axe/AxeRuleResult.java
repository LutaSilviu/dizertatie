package ro.sluta.accessibility.analysis.axe;

import java.util.List;

public record AxeRuleResult(
        String id,
        String impact,
        List<String> tags,
        String description,
        String help,
        String helpUrl,
        List<AxeNodeResult> nodes) {
    public AxeRuleResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
