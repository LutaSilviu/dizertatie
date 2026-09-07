package ro.sluta.accessibility.analysis.axe;

import java.util.List;

public record AxeCheckResult(
        String id,
        String impact,
        String message,
        Object data,
        List<AxeRelatedNode> relatedNodes) {
    public AxeCheckResult {
        relatedNodes = relatedNodes == null ? List.of() : List.copyOf(relatedNodes);
    }
}
