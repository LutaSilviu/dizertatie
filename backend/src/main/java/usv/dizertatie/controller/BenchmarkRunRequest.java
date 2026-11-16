package usv.dizertatie.controller;

import java.util.List;

/**
 * Request body for {@code POST /benchmark-runs}.
 *
 * @param name          human-readable name for this run
 * @param urls          explicit list of URLs; ignored when {@code useBuiltinSet=true}
 * @param useBuiltinSet when {@code true}, URLs are loaded from seeded ground-truth labels
 * @param model         the AI model ID to use (defaults to "gpt-6-luna")
 * @param methods       scan methods to run, any of AXE, AI, COMBINED (case-insensitive);
 *                      {@code null} means all three, an empty list is rejected
 */
public record BenchmarkRunRequest(
        String name,
        List<String> urls,
        boolean useBuiltinSet,
        String model,
        List<String> methods
) {
    public BenchmarkRunRequest {
        if (name == null || name.isBlank()) {
            name = "Benchmark run";
        }
        if (urls == null) {
            urls = List.of();
        }
    }
}
