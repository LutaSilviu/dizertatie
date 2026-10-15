package usv.dizertatie.service;

import java.util.List;
import java.util.Map;

/**
 * Aggregate metrics for a completed {@link usv.dizertatie.domain.BenchmarkRun}.
 *
 * <ul>
 *   <li>{@code runId}          – id of the benchmark run</li>
 *   <li>{@code methods}        – overall precision/recall/F1 keyed by "AXE", "AI", "COMBINED"</li>
 *   <li>{@code byCategory}     – same metrics broken down by WCAG category (Perceivable/Operable/
 *                                Understandable/Robust), then by method</li>
 *   <li>{@code falsePositiveExamples} – up to {@link MetricsService#MAX_EXAMPLES} false-positive examples</li>
 *   <li>{@code falseNegativeExamples} – up to {@link MetricsService#MAX_EXAMPLES} false-negative examples</li>
 *   <li>{@code avgCostPerPageUsd} – average estimated AI cost per page in USD</li>
 *   <li>{@code avgLatencyMs}   – average latency per method in milliseconds</li>
 * </ul>
 */
public record BenchmarkMetrics(
        Long runId,
        Map<String, MethodMetrics> methods,
        Map<String, Map<String, MethodMetrics>> byCategory,
        List<FpFnExample> falsePositiveExamples,
        List<FpFnExample> falseNegativeExamples,
        Double avgCostPerPageUsd,
        Map<String, Double> avgLatencyMs
) {}
