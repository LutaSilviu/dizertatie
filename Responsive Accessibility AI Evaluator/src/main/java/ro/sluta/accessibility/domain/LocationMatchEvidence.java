package ro.sluta.accessibility.domain;

import java.util.List;

public record LocationMatchEvidence(
        double selectorScore,
        double roleScore,
        double accessibleNameScore,
        double visibleTextScore,
        double domProximityScore,
        double totalScore,
        List<String> reasons,
        String matcherVersion) {
    public LocationMatchEvidence {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
