package usv.dizertatie.scoring;

import org.springframework.stereotype.Service;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.wcag.WcagCriterionRegistry;

import java.util.List;

@Service
public class SeverityScoringService {

    private final WcagCriterionRegistry registry;

    public SeverityScoringService(WcagCriterionRegistry registry) {
        this.registry = registry;
    }

    public double score(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return 100.0;
        }

        int totalCriteria = registry.getAll().size();
        double maxPossibleWeight = totalCriteria * 4.0;

        double sumOfWeights = findings.stream()
                .mapToDouble(f -> weightFor(f.getSeverity()))
                .sum();

        double raw = 100.0 - (sumOfWeights / maxPossibleWeight * 100.0);
        return Math.max(0.0, Math.min(100.0, raw));
    }

    private static double weightFor(String severity) {
        if (severity == null) {
            return 0.0;
        }
        return switch (severity.toLowerCase()) {
            case "critical" -> 4.0;
            case "serious"  -> 3.0;
            case "moderate" -> 2.0;
            case "minor"    -> 1.0;
            default         -> 0.0;
        };
    }
}
