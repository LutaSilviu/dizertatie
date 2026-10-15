package usv.dizertatie.controller;

import usv.dizertatie.service.BenchmarkMetrics;
import usv.dizertatie.service.FpFnExample;
import usv.dizertatie.service.MethodMetrics;

import java.util.List;
import java.util.Map;

/**
 * JSON response for {@code GET /benchmark-runs/{id}/metrics}.
 *
 * @param runId                  id of the benchmark run
 * @param methods                overall per-method metrics
 * @param byCategory             per-WCAG-principle per-method metrics
 * @param falsePositiveExamples  representative false positives
 * @param falseNegativeExamples  representative false negatives
 * @param avgCostPerPageUsd      average estimated AI cost per page (USD)
 * @param avgLatencyMs           average latency per method (ms)
 */
public record BenchmarkMetricsResponse(
        Long runId,
        Map<String, MethodMetrics> methods,
        Map<String, Map<String, MethodMetrics>> byCategory,
        List<FpFnExample> falsePositiveExamples,
        List<FpFnExample> falseNegativeExamples,
        Double avgCostPerPageUsd,
        Map<String, Double> avgLatencyMs
) {

    /**
     * Maps a {@link BenchmarkMetrics} service object to this response record.
     *
     * @param m service metrics
     * @return response record
     */
    public static BenchmarkMetricsResponse from(BenchmarkMetrics m) {
        return new BenchmarkMetricsResponse(
                m.runId(),
                m.methods(),
                m.byCategory(),
                m.falsePositiveExamples(),
                m.falseNegativeExamples(),
                m.avgCostPerPageUsd(),
                m.avgLatencyMs()
        );
    }
}
