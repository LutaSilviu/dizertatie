package usv.dizertatie.controller;

import usv.dizertatie.ai.AiModel;

import java.util.List;

/**
 * Request body for {@code POST /benchmark-runs}.
 *
 * @param name          human-readable name for this run
 * @param urls          explicit list of URLs; ignored when {@code useBuiltinSet=true}
 * @param useBuiltinSet when {@code true}, URLs are loaded from seeded ground-truth labels
 * @param model         the AI model to use for AI and COMBINED scans; defaults to GPT-4o-mini
 */
public record BenchmarkRunRequest(
        String name,
        List<String> urls,
        boolean useBuiltinSet,
        AiModel model
) {
    public BenchmarkRunRequest {
        if (model == null) {
            model = AiModel.GPT4O_MINI;
        }
        if (urls == null) {
            urls = List.of();
        }
    }
}
