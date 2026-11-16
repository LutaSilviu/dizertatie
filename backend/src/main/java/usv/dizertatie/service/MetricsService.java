package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.domain.ScanStatus;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;
import usv.dizertatie.repository.ScanRepository;

import java.util.*;

/**
 * Computes precision, recall and F1 for each scan method (AXE / AI / COMBINED) by comparing
 * the findings recorded in a {@link BenchmarkRun} against the seeded ground-truth labels.
 *
 * <p>A predicted finding <em>matches</em> a ground-truth label when both share the same
 * {@code wcagCriterion} AND {@code elementSelector} for the same page URL.
 *
 * <p>Counting rules (see docs/adr/0001-benchmark-counting-rules.md):
 * <ul>
 *   <li>Findings are counted per distinct {@code criterion|selector} key per method and page, so
 *       two rules flagging the same element under the same criterion are one prediction.</li>
 *   <li>A scan that did not complete has no evidence; that page is left out of the method's
 *       counts instead of scoring every label as a false negative.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    static final String AXE      = "AXE";
    static final String AI       = "AI";
    static final String COMBINED = "COMBINED";

    static final List<String> METHODS = List.of(AXE, AI, COMBINED);

    /** Maximum FP examples and FN examples kept per method. */
    static final int MAX_EXAMPLES = 10;

    private final BenchmarkPageRepository benchmarkPageRepository;
    private final FindingRepository       findingRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;
    private final ScanRepository scanRepository;

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

        // Every candidate example; a diverse sample is drawn at the end (see sampleExamples).
        List<FpFnExample> fpCandidates = new ArrayList<>();
        List<FpFnExample> fnCandidates = new ArrayList<>();
        Set<String> methodsWithResults = new LinkedHashSet<>();

        for (BenchmarkPage page : pages) {
            String url = page.getUrl();
            List<GroundTruthLabel> labels = groundTruthLabelRepository.findByUrl(url);

            // Build a lookup key set from labels: "wcagCriterion|elementSelector"
            Set<String> labelKeys = new HashSet<>();
            for (GroundTruthLabel l : labels) {
                labelKeys.add(labelKey(l.getWcagCriterion(), l.getElementSelector()));
            }

            for (String method : METHODS) {
                // A method that failed on this page has no scan: it neither found nor missed
                // anything there, so the page is left out of that method's counts.
                Long scanId = scanIdFor(page, method);
                if (scanId == null || !completed(scanId)) {
                    continue;
                }
                methodsWithResults.add(method);

                // One prediction per distinct key: duplicates (two axe rules on the same element
                // and criterion) must not turn one label into several true positives.
                Map<String, Finding> distinctFindings = new LinkedHashMap<>();
                for (Finding f : findingRepository.findByScanId(scanId)) {
                    distinctFindings.putIfAbsent(labelKey(f.getWcagCriterion(), f.getElementSelector()), f);
                }
                Set<String> findingKeys = distinctFindings.keySet();

                // TP + FP from predicted findings
                for (Map.Entry<String, Finding> entry : distinctFindings.entrySet()) {
                    String key = entry.getKey();
                    Finding f = entry.getValue();
                    String category = wcagCategory(f.getWcagCriterion());
                    int[] overall = overallCounters.get(method);
                    int[] catArr  = catCounters.get(method).computeIfAbsent(category, k -> new int[3]);

                    if (labelKeys.contains(key)) {
                        overall[0]++; // TP
                        catArr[0]++;
                    } else {
                        overall[1]++; // FP
                        catArr[1]++;
                        fpCandidates.add(new FpFnExample(method, url,
                                f.getWcagCriterion(), f.getElementSelector(), f.getDescription()));
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
                        // The label's rationale says what the method missed.
                        fnCandidates.add(new FpFnExample(method, url,
                                l.getWcagCriterion(), l.getElementSelector(), l.getRationale()));
                    }
                }
            }
        }

        // Build overall method metrics
        // Methods with no successful page (e.g. AI without a valid OpenAI key) are omitted.
        Map<String, MethodMetrics> methods = new LinkedHashMap<>();
        for (String m : METHODS) {
            if (!methodsWithResults.contains(m)) continue;
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
                if (!methodsWithResults.contains(m)) continue;
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
        if (avgAxe      != null && methodsWithResults.contains(AXE))      avgLatency.put("AXE",      avgAxe);
        if (avgAi       != null && methodsWithResults.contains(AI))       avgLatency.put("AI",       avgAi);
        if (avgCombined != null && methodsWithResults.contains(COMBINED)) avgLatency.put("COMBINED", avgCombined);

        return new BenchmarkMetrics(
                run.getId(),
                methods,
                byCategory,
                sampleExamples(fpCandidates),
                sampleExamples(fnCandidates),
                avgCost,
                avgLatency
        );
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Picks at most {@link #MAX_EXAMPLES} examples per method, round-robin across distinct WCAG
     * criteria (in first-seen order), so one frequent issue (e.g. dozens of 2.5.8 target-size
     * findings) cannot fill every slot and different kinds of errors show up.
     */
    static List<FpFnExample> sampleExamples(List<FpFnExample> candidates) {
        List<FpFnExample> sample = new ArrayList<>();
        for (String method : METHODS) {
            Map<String, Deque<FpFnExample>> byCriterion = new LinkedHashMap<>();
            for (FpFnExample e : candidates) {
                if (method.equals(e.method())) {
                    String criterion = e.wcagCriterion() == null ? "" : e.wcagCriterion();
                    byCriterion.computeIfAbsent(criterion, k -> new ArrayDeque<>()).add(e);
                }
            }
            int taken = 0;
            while (taken < MAX_EXAMPLES && !byCriterion.isEmpty()) {
                Iterator<Deque<FpFnExample>> groups = byCriterion.values().iterator();
                while (groups.hasNext() && taken < MAX_EXAMPLES) {
                    Deque<FpFnExample> group = groups.next();
                    sample.add(group.poll());
                    taken++;
                    if (group.isEmpty()) {
                        groups.remove();
                    }
                }
            }
        }
        return sample;
    }

    /** A scan that was blocked, rate limited, timed out or failed carries no evidence to score. */
    private boolean completed(Long scanId) {
        return scanRepository.findById(scanId)
                .map(scan -> scan.getStatus() == null || scan.getStatus() == ScanStatus.COMPLETED)
                .orElse(true);
    }

    private static Long scanIdFor(BenchmarkPage page, String method) {
        return switch (method) {
            case AXE -> page.getAxeScanId();
            case AI -> page.getAiScanId();
            default -> page.getCombinedScanId();
        };
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
