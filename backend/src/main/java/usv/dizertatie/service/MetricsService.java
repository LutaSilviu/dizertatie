package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes precision / recall / F1 metrics for a completed
 * {@link BenchmarkRun} by comparing each scan's findings against the
 * ground-truth labels that were annotated for the same URL.
 *
 * <p>A finding is counted as a <em>true positive</em> when a ground-truth label
 * exists for the same URL and WCAG criterion; otherwise it is a
 * <em>false positive</em>.  Ground-truth labels for which no finding was
 * produced count as <em>false negatives</em>.
 *
 * <p>Metrics are computed globally (all pages × all criteria) and per
 * WCAG principle category (first word of the criterion name, e.g. "1.1.1" → "1").
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    /** Maximum number of FP/FN examples to include in the response. */
    static final int MAX_EXAMPLES = 5;

    /** Methods recognised by the benchmark runner. */
    static final List<String> METHODS = List.of("AXE", "AI", "COMBINED");

    private final BenchmarkPageRepository benchmarkPageRepository;
    private final FindingRepository findingRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;

    /**
     * Computes {@link BenchmarkMetrics} for the given run.
     *
     * @param run a <em>COMPLETE</em> benchmark run
     * @return computed metrics
     */
    public BenchmarkMetrics compute(BenchmarkRun run) {
        List<BenchmarkPage> pages = benchmarkPageRepository.findByRunId(run.getId());

        // Accumulators: method → category → Counts
        Map<String, Map<String, Counts>> acc = new HashMap<>();
        for (String m : METHODS) {
            acc.put(m, new HashMap<>());
        }

        List<FpFnExample> fpExamples = new ArrayList<>();
        List<FpFnExample> fnExamples = new ArrayList<>();

        for (BenchmarkPage page : pages) {
            if (!"DONE".equals(page.getStatus())) continue;

            String url = page.getUrl();
            List<GroundTruthLabel> labels = groundTruthLabelRepository.findByUrl(url);
            Set<String> gtCriteria = labels.stream()
                    .map(GroundTruthLabel::getWcagCriterion)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Determine scan ids per method
            Map<String, Long> scanIds = new LinkedHashMap<>();
            if (page.getAxeScanId() != null)      scanIds.put("AXE",      page.getAxeScanId());
            if (page.getAiScanId() != null)        scanIds.put("AI",       page.getAiScanId());
            if (page.getCombinedScanId() != null)  scanIds.put("COMBINED", page.getCombinedScanId());

            for (Map.Entry<String, Long> entry : scanIds.entrySet()) {
                String method = entry.getKey();
                List<Finding> findings = findingRepository.findByScanId(entry.getValue());

                Set<String> foundCriteria = new HashSet<>();
                for (Finding f : findings) {
                    String crit = f.getWcagCriterion();
                    if (crit == null) continue;
                    foundCriteria.add(crit);

                    String category = categoryOf(crit);
                    if (gtCriteria.contains(crit)) {
                        // TP
                        acc.get(method).computeIfAbsent("__all__", k -> new Counts()).tp++;
                        acc.get(method).computeIfAbsent(category,  k -> new Counts()).tp++;
                    } else {
                        // FP
                        acc.get(method).computeIfAbsent("__all__", k -> new Counts()).fp++;
                        acc.get(method).computeIfAbsent(category,  k -> new Counts()).fp++;
                        if (fpExamples.size() < MAX_EXAMPLES) {
                            fpExamples.add(new FpFnExample(method, url, crit,
                                    f.getElementSelector(), f.getDescription()));
                        }
                    }
                }

                // FN: ground-truth criteria not found by this method
                for (String crit : gtCriteria) {
                    if (!foundCriteria.contains(crit)) {
                        String category = categoryOf(crit);
                        acc.get(method).computeIfAbsent("__all__", k -> new Counts()).fn++;
                        acc.get(method).computeIfAbsent(category,  k -> new Counts()).fn++;
                        if (fnExamples.size() < MAX_EXAMPLES) {
                            // Find a matching label for element/description context
                            GroundTruthLabel lbl = labels.stream()
                                    .filter(l -> crit.equals(l.getWcagCriterion()))
                                    .findFirst().orElse(null);
                            fnExamples.add(new FpFnExample(method, url, crit,
                                    lbl != null ? lbl.getElementSelector() : null,
                                    null));
                        }
                    }
                }
            }
        }

        // Build overall per-method metrics
        Map<String, MethodMetrics> methods = new LinkedHashMap<>();
        for (String m : METHODS) {
            Counts c = acc.get(m).getOrDefault("__all__", new Counts());
            methods.put(m, MethodMetrics.of(c.tp, c.fp, c.fn));
        }

        // Build per-category per-method metrics
        Set<String> allCategories = acc.values().stream()
                .flatMap(map -> map.keySet().stream())
                .filter(k -> !"__all__".equals(k))
                .collect(Collectors.toCollection(TreeSet::new));

        Map<String, Map<String, MethodMetrics>> byCategory = new LinkedHashMap<>();
        for (String cat : allCategories) {
            Map<String, MethodMetrics> catMap = new LinkedHashMap<>();
            for (String m : METHODS) {
                Counts c = acc.get(m).getOrDefault(cat, new Counts());
                catMap.put(m, MethodMetrics.of(c.tp, c.fp, c.fn));
            }
            byCategory.put(cat, catMap);
        }

        // Avg cost and latency from repository
        Double avgCost = benchmarkPageRepository.avgEstimatedCostUsdByRunId(run.getId());
        Map<String, Double> avgLatency = new LinkedHashMap<>();
        Double avgAxe      = benchmarkPageRepository.avgLatencyAxeMsByRunId(run.getId());
        Double avgAi       = benchmarkPageRepository.avgLatencyAiMsByRunId(run.getId());
        Double avgCombined = benchmarkPageRepository.avgLatencyCombinedMsByRunId(run.getId());
        if (avgAxe      != null) avgLatency.put("AXE",      avgAxe);
        if (avgAi       != null) avgLatency.put("AI",       avgAi);
        if (avgCombined != null) avgLatency.put("COMBINED", avgCombined);

        return new BenchmarkMetrics(
                run.getId(),
                methods,
                byCategory,
                fpExamples,
                fnExamples,
                avgCost,
                avgLatency
        );
    }

    // ---- helpers ----

    /**
     * Maps a WCAG criterion (e.g. "1.1.1") to its principle number ("1").
     * Unmapped criteria fall into the "Other" category.
     */
    private static String categoryOf(String criterion) {
        if (criterion == null || criterion.isBlank()) return "Other";
        String first = criterion.split("\\.")[0];
        return switch (first) {
            case "1" -> "Perceivable";
            case "2" -> "Operable";
            case "3" -> "Understandable";
            case "4" -> "Robust";
            default  -> "Other";
        };
    }

    /** Mutable TP / FP / FN accumulator. */
    private static class Counts {
        int tp, fp, fn;
    }
}
