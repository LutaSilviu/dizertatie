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
 * and the same element selector. In that case the Axe finding is preferred (it carries
 * a structured selector and level), and its {@code sourceMethod} is set to
 * {@code "COMBINED"} to indicate that both tools flagged the same issue.
 */
@Service
public class FindingMergeService {

    /**
     * Merges Axe and AI findings, de-duplicating by (wcagCriterion, elementSelector).
     *
     * <p>Unique Axe findings are included as-is (sourceMethod = "AXE").
     * Unique AI findings are included as-is (sourceMethod = "AI").
     * When both tools flag the same criterion + selector, the Axe finding is kept
     * with sourceMethod set to "COMBINED".
     *
     * @param axeFindings findings from the Axe scan
     * @param aiFindings  findings from the AI scan
     * @return merged list of findings
     */
    public List<Finding> merge(List<Finding> axeFindings, List<Finding> aiFindings) {
        List<Finding> result = new ArrayList<>();

        // Track which AI findings have been matched
        boolean[] aiMatched = new boolean[aiFindings.size()];

        for (Finding axeFinding : axeFindings) {
            boolean matched = false;
            for (int i = 0; i < aiFindings.size(); i++) {
                if (!aiMatched[i] && isSameFinding(axeFinding, aiFindings.get(i))) {
                    // Duplicate: prefer Axe data but set sourceMethod=COMBINED
                    Finding merged = Finding.builder()
                            .scan(axeFinding.getScan())
                            .sourceMethod("COMBINED")
                            .wcagCriterion(axeFinding.getWcagCriterion())
                            .wcagLevel(axeFinding.getWcagLevel())
                            .ruleId(axeFinding.getRuleId())
                            .severity(axeFinding.getSeverity())
                            .elementSelector(axeFinding.getElementSelector())
                            .description(axeFinding.getDescription())
                            .suggestion(axeFinding.getSuggestion())
                            .confidence(axeFinding.getConfidence())
                            .build();
                    result.add(merged);
                    aiMatched[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // Unique to Axe: keep sourceMethod=AXE
                result.add(axeFinding);
            }
        }

        // Add remaining unmatched AI findings
        for (int i = 0; i < aiFindings.size(); i++) {
            if (!aiMatched[i]) {
                result.add(aiFindings.get(i));
            }
        }

        return result;
    }

    private boolean isSameFinding(Finding a, Finding b) {
        return Objects.equals(a.getWcagCriterion(), b.getWcagCriterion())
                && Objects.equals(a.getElementSelector(), b.getElementSelector());
    }
}
