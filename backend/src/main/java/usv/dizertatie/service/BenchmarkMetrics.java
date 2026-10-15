package usv.dizertatie.service;

import java.util.List;
import java.util.Map;

/**
 * Aggregate metrics for a completed {@link usv.dizertatie.domain.BenchmarkRun}.
 *
 * <ul>
 *   <li>{@code methods}     – overall precision/recall/F1 keyed by "AXE", "AI", "COMBINED"</li>
 *   <li>{@code byCategory}  – same metrics broken down by WCAG category (Perceivable/Operable/
 *                              Understandable/Robust), then by method</li>
 *   <li>{@code falsePositiveExamples} – up to 10 false-positive examples per method</li>
 *   <li>{@code falseNegativeExamples} – up to 10 false-negative examples per method</li>
 * </ul>
 */
public record BenchmarkMetrics(
        Map<String, MethodMetrics> methods,
        Map<String, Map<String, MethodMetrics>> byCategory,
        List<FpFnExample> falsePositiveExamples,
        List<FpFnExample> falseNegativeExamples
) {}
