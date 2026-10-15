package usv.dizertatie.service;

import org.springframework.stereotype.Service;
import usv.dizertatie.domain.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Merges findings from Axe and AI scans, de-duplicating overlapping issues.
 *
 * <p>Two findings are considered duplicates when they share the same WCAG criterion
 * and the same element selector. In that case the AI finding is preferred (it typically
 * carries a richer description and suggestion), and its {@code sourceMethod} is set to
 * {@code "COMBINED"} to indicate that both tools flagged the same issue.
 */
@Service
public class FindingMergeService {

    /**
     * Merges Axe and AI findings, de-duplicating by (wcagCriterion, elementSelector).
     *
     * <p>Unique Axe findings are included as-is (sourceMethod = "AXE").
     * Unique AI findings are included as-is (sourceMethod = "AI").
     * When both tools flag the same criterion + selector, the AI finding is kept
     * with sourceMethod set to "COMBINED".
     *
     * @param axeFindings findings from the Axe scan
     * @param aiFindings  findings from the AI scan
     * @return merged list of findings, none with a scan set (caller must set scan before persisting)
     */
    public List<Finding> merge(List<Finding> axeFindings, List<Finding> aiFindings) {
        List<Finding> result = new ArrayList<>();

        for (Finding ai : aiFindings) {
            boolean matchedByAxe = axeFindings.stream().anyMatch(axe -> isSameIssue(axe, ai));
            if (matchedByAxe) {
                // Prefer the AI finding but mark as COMBINED
                Finding combined = copyWithSourceMethod(ai, "COMBINED");
                result.add(combined);
            } else {
                result.add(ai);
            }
        }

        for (Finding axe : axeFindings) {
            boolean alreadyCovered = aiFindings.stream().anyMatch(ai -> isSameIssue(axe, ai));
            if (!alreadyCovered) {
                result.add(axe);
            }
        }

        return result;
    }

    private boolean isSameIssue(Finding a, Finding b) {
        return Objects.equals(normaliseCriterion(a.getWcagCriterion()),
                              normaliseCriterion(b.getWcagCriterion()))
                && Objects.equals(normaliseSelector(a.getElementSelector()),
                                  normaliseSelector(b.getElementSelector()));
    }

    private String normaliseCriterion(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String normaliseSelector(String s) {
        return s == null ? "" : s.trim();
    }

    private Finding copyWithSourceMethod(Finding original, String sourceMethod) {
        return Finding.builder()
                .sourceMethod(sourceMethod)
                .wcagCriterion(original.getWcagCriterion())
                .wcagLevel(original.getWcagLevel())
                .severity(original.getSeverity())
                .elementSelector(original.getElementSelector())
                .description(original.getDescription())
                .suggestion(original.getSuggestion())
                .confidence(original.getConfidence())
                .build();
    }
}
