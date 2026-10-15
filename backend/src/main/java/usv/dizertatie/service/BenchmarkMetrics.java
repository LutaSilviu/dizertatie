package usv.dizertatie.service;

import java.util.List;
import java.util.Map;

/**
 * Full benchmark metrics computed over a completed {@link usv.dizertatie.domain.BenchmarkRun}.
 *
 * @param runId                  id of the benchmark run
 * @param methods                overall per-method metrics: method-name → {@link MethodMetrics}
 * @param byCategory             per-WCAG-principle per-method metrics:
 *                               category → method-name → {@link MethodMetrics}
 * @param falsePositiveExamples  up to {@code MAX_EXAMPLES} representative false positives
 * @param falseNegativeExamples  up to {@code MAX_EXAMPLES} representative false negatives
 * @param avgCostPerPageUsd      average estimated AI cost per page in USD
 * @param avgLatencyMs           average latency per method in milliseconds:
 *                               method-name → millis (may be null when no pages have data)
 */
public record BenchmarkMetrics(
        Long runId,
        Map<String, MethodMetrics> methods,
        Map<String, Map<String, MethodMetrics>> byCategory,
        List<FpFnExample> falsePositiveExamples,
        List<FpFnExample> falseNegativeExamples,
        Double avgCostPerPageUsd,
        Map<String, Double> avgLatencyMs
) {
}
