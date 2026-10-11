package usv.dizertatie.scoring;

import org.springframework.stereotype.Service;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.wcag.WcagCriterionRegistry;
import usv.dizertatie.wcag.WcagLevel;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConformanceScoringService {

    private final WcagCriterionRegistry registry;

    public ConformanceScoringService(WcagCriterionRegistry registry) {
        this.registry = registry;
    }

    public ConformanceScores score(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return new ConformanceScores(100.0, 100.0, 100.0);
        }

        Set<String> violatedCriteria = findings.stream()
                .map(Finding::getWcagCriterion)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());

        double levelA = computePassRate(WcagLevel.A, violatedCriteria);
        double levelAA = computePassRate(WcagLevel.AA, violatedCriteria);
        double levelAAA = computePassRate(WcagLevel.AAA, violatedCriteria);

        return new ConformanceScores(levelA, levelAA, levelAAA);
    }

    private double computePassRate(WcagLevel level, Set<String> violatedCriteria) {
        List<String> criteriaAtLevel = registry.getByLevel(level).stream()
                .map(c -> c.id())
                .collect(Collectors.toList());

        if (criteriaAtLevel.isEmpty()) {
            return 100.0;
        }

        long violatedCount = criteriaAtLevel.stream()
                .filter(violatedCriteria::contains)
                .count();

        return (double) (criteriaAtLevel.size() - violatedCount) / criteriaAtLevel.size() * 100.0;
    }
}
