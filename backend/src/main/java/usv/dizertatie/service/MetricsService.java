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

/**
 * Computes precision, recall and F1 for each scan method (AXE / AI / COMBINED) by comparing
 * the findings recorded in a {@link BenchmarkRun} against the seeded ground-truth labels.
 *
 * <p>A predicted finding <em>matches</em> a ground-truth label when both share the same
 * {@code wcagCriterion} AND {@code elementSelector} for the same page URL.
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    static final String AXE      = "AXE";
    static final String AI       = "AI";
    static final String COMBINED = "COMBINED";

    static final List<String> METHODS = List.of(AXE, AI, COMBINED);

    static final int MAX_EXAMPLES = 10;

    private final BenchmarkPageRepository benchmarkPageRepository;
    private final FindingRepository       findingRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;

    /**
     * Computes a {@link BenchmarkMetrics} for the given completed run.
     *
     * @param run the benchmark run to evaluate
     * @return aggregated metrics (overall + per WCAG category + FP/FN examples)
     */
    public BenchmarkMetrics compute(BenchmarkRun run) {
        List<BenchmarkPage> pages = benchmarkPageRepository.findByRunId(run.getId());

        // Counters: method -> category -> {tp, fp, fn}
        Map<String, Map<String, int[]>> catCounters = new LinkedHashMap<>();
        Map<String, int[]> overallCounters = new LinkedHashMap<>();
        for (String m : METHODS) {
            overallCounters.put(m, new int[3]); // [tp, fp, fn]
            catCounters.put(m, new LinkedHashMap<>());
        }

        List<FpFnExample> fpExamples = new ArrayList<>();
        List<FpFnExample> fnExamples = new ArrayList<>();

        for (BenchmarkPage page : pages) {
            String url = page.getUrl();
            List<GroundTruthLabel> labels = groundTruthLabelRepository.findByUrl(url);

            // Build a lookup key set from labels: "wcagCriterion|elementSelector"
            Set<String> labelKeys = new HashSet<>();
            for (GroundTruthLabel l : labels) {
                labelKeys.add(labelKey(l.getWcagCriterion(), l.getElementSelector()));
            }

            // Load findings for each method
            Map<String, List<Finding>> methodFindings = new LinkedHashMap<>();
            methodFindings.put(AXE,      loadFindings(page.getAxeScanId()));
            methodFindings.put(AI,       loadFindings(page.getAiScanId()));
            methodFindings.put(COMBINED, loadFindings(page.getCombinedScanId()));

            for (String method : METHODS) {
                List<Finding> findings = methodFindings.get(method);

                Set<String> findingKeys = new HashSet<>();
                for (Finding f : findings) {
                    findingKeys.add(labelKey(f.getWcagCriterion(), f.getElementSelector()));
                }

                // TP + FP from predicted findings
                for (Finding f : findings) {
                    String key = labelKey(f.getWcagCriterion(), f.getElementSelector());
                    String category = wcagCategory(f.getWcagCriterion());
                    int[] overall = overallCounters.get(method);
                    int[] catArr  = catCounters.get(method).computeIfAbsent(category, k -> new int[3]);

                    if (labelKeys.contains(key)) {
                        overall[0]++; // TP
                        catArr[0]++;
                    } else {
                        overall[1]++; // FP
                        catArr[1]++;
                        if (fpExamples.size() < MAX_EXAMPLES) {
                            fpExamples.add(new FpFnExample(method, url,
                                    f.getWcagCriterion(), f.getElementSelector(), f.getDescription()));
                        }
                    }
                }

                // FN from labels not covered by findings
                for (GroundTruthLabel l : labels) {
                    String key = labelKey(l.getWcagCriterion(), l.getElementSelector());
                    if (!findingKeys.contains(key)) {
                        String category = wcagCategory(l.getWcagCriterion());
                        int[] overall = overallCounters.get(method);
                        int[] catArr  = catCounters.get(method).computeIfAbsent(category, k -> new int[3]);
                        overall[2]++; // FN
                        catArr[2]++;
                        if (fnExamples.size() < MAX_EXAMPLES) {
                            fnExamples.add(new FpFnExample(method, url,
                                    l.getWcagCriterion(), l.getElementSelector(), null));
                        }
                    }
                }
            }
        }

        // Build overall method metrics
        Map<String, MethodMetrics> methods = new LinkedHashMap<>();
        for (String m : METHODS) {
            int[] c = overallCounters.get(m);
            methods.put(m, MethodMetrics.of(c[0], c[1], c[2]));
        }

        // Build per-category metrics
        Map<String, Map<String, MethodMetrics>> byCategory = new LinkedHashMap<>();
        Set<String> allCategories = new LinkedHashSet<>();
        for (String m : METHODS) {
            allCategories.addAll(catCounters.get(m).keySet());
        }
        for (String cat : allCategories) {
            Map<String, MethodMetrics> catMap = new LinkedHashMap<>();
            for (String m : METHODS) {
                int[] c = catCounters.get(m).getOrDefault(cat, new int[3]);
                catMap.put(m, MethodMetrics.of(c[0], c[1], c[2]));
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

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private List<Finding> loadFindings(Long scanId) {
        if (scanId == null) return Collections.emptyList();
        return findingRepository.findByScanId(scanId);
    }

    static String labelKey(String wcagCriterion, String elementSelector) {
        return (wcagCriterion == null ? "" : wcagCriterion)
                + "|"
                + (elementSelector == null ? "" : elementSelector);
    }

    /**
     * Maps a WCAG criterion to a top-level principle category based on its numeric prefix.
     * <ul>
     *   <li>1.x.x → Perceivable</li>
     *   <li>2.x.x → Operable</li>
     *   <li>3.x.x → Understandable</li>
     *   <li>4.x.x → Robust</li>
     * </ul>
     */
    static String wcagCategory(String criterion) {
        if (criterion == null || criterion.isBlank()) return "Unknown";
        if (criterion.startsWith("1.")) return "Perceivable";
        if (criterion.startsWith("2.")) return "Operable";
        if (criterion.startsWith("3.")) return "Understandable";
        if (criterion.startsWith("4.")) return "Robust";
        return "Unknown";
    }
}
