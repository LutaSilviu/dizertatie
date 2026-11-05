package usv.dizertatie.scoring;

import org.springframework.stereotype.Service;
import usv.dizertatie.domain.Finding;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregate severity score for one scan: 0–100, higher is better, exactly 100 when there are no
 * (weighted) findings.
 *
 * <h2>Formula</h2>
 * Findings are grouped by WCAG success criterion (findings without one are grouped by their
 * description). Each violated criterion contributes a penalty
 * <pre>  p = w(worst severity in the group) × (1 + log10(number of findings in the group))</pre>
 * with w = critical 4, serious 3, moderate 2, minor 1, unknown 0. The score is
 * <pre>  score = 100 × exp(−Σp / {@value #DECAY})</pre>
 * rounded to one decimal.
 *
 * <h2>Why not "100 − Σweights / (all criteria × 4)"</h2>
 * The previous formula divided by the weight of every criterion in the registry (87 × 4), so one
 * critical issue cost 1.1 points and a dozen serious ones barely moved the score. It also counted
 * every DOM node separately, so axe-core (one finding per node) was penalised several times more
 * than the AI method (one finding per issue) for the same defect. Grouping by criterion with a
 * logarithmic instance factor makes the methods comparable and makes the score respond visibly:
 * one minor issue ≈ 96, one critical ≈ 85, thirty contrast failures ≈ 74, five serious criteria
 * with five instances each ≈ 36. The exponential keeps the result in (0, 100] without clamping.
 */
@Service
public class SeverityScoringService {

    /** Penalty at which the score falls to 100/e ≈ 36.8. */
    static final double DECAY = 25.0;

    public double score(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return 100.0;
        }

        Map<String, double[]> groups = new LinkedHashMap<>(); // key -> {max weight, count}
        for (Finding f : findings) {
            double[] g = groups.computeIfAbsent(groupKey(f), k -> new double[2]);
            g[0] = Math.max(g[0], weightFor(f.getSeverity()));
            g[1]++;
        }

        double penalty = 0.0;
        for (double[] g : groups.values()) {
            penalty += g[0] * (1.0 + Math.log10(g[1]));
        }

        double raw = 100.0 * Math.exp(-penalty / DECAY);
        return Math.round(raw * 10.0) / 10.0;
    }

    private static String groupKey(Finding f) {
        String criterion = f.getWcagCriterion();
        if (criterion != null && !criterion.isBlank()) {
            return "sc:" + criterion.strip();
        }
        String description = f.getDescription();
        return "unmapped:" + (description == null ? "" : description.strip().toLowerCase(Locale.ROOT));
    }

    static double weightFor(String severity) {
        if (severity == null) {
            return 0.0;
        }
        return switch (severity.toLowerCase(Locale.ROOT)) {
            case "critical" -> 4.0;
            case "serious"  -> 3.0;
            case "moderate" -> 2.0;
            case "minor"    -> 1.0;
            default         -> 0.0;
        };
    }
}
