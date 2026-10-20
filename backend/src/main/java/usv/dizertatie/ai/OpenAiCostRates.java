package usv.dizertatie.ai;

/**
 * Cost rates for OpenAI API usage estimation.
 *
 * <p>Rates are in USD per 1,000 tokens:
 * <ul>
 *   <li>GPT-6 Luna: $0.0001 / 1K input tokens, $0.0005 / 1K output tokens</li>
 *   <li>GPT-4o: $0.0025 / 1K input tokens, $0.010 / 1K output tokens</li>
 *   <li>GPT-4o-mini: $0.00015 / 1K input tokens, $0.00060 / 1K output tokens</li>
 * </ul>
 *
 * <p>Token counts are estimated using the heuristic:
 * <ul>
 *   <li>promptTokens ≈ htmlLength / 4 (approximate characters-per-token for English/HTML)</li>
 *   <li>completionTokens ≈ 500 (typical JSON response with a few findings)</li>
 * </ul>
 *
 * <p>These estimates are intentionally conservative and provided for informational purposes only.
 * Actual billed costs may differ. Use the OpenAI usage dashboard for exact figures.
 */
public final class OpenAiCostRates {

    // GPT-4o pricing (USD per 1,000 tokens)
    public static final double GPT4O_INPUT_PER_1K = 0.0025;
    public static final double GPT4O_OUTPUT_PER_1K = 0.010;

    // GPT-6 Luna pricing (USD per 1,000 tokens)
    public static final double GPT6_LUNA_INPUT_PER_1K = 0.0001;
    public static final double GPT6_LUNA_OUTPUT_PER_1K = 0.0005;

    // GPT-4o-mini pricing (USD per 1,000 tokens)
    public static final double GPT4O_MINI_INPUT_PER_1K = 0.00015;
    public static final double GPT4O_MINI_OUTPUT_PER_1K = 0.00060;

    /**
     * Estimated completion tokens per AI response (heuristic).
     * Represents a typical JSON response containing a few accessibility findings.
     */
    public static final int ESTIMATED_COMPLETION_TOKENS = 500;

    private OpenAiCostRates() {
        // constants class — no instances
    }

    /**
     * Estimates the OpenAI API cost for a single analysis call.
     *
     * <p>Prompt token count is estimated as {@code htmlLength / 4}.
     * Completion token count uses the fixed heuristic {@link #ESTIMATED_COMPLETION_TOKENS}.
     *
     * @param model     the model used
     * @param htmlLength number of characters in the HTML sent to the model
     * @return estimated cost in USD
     */
    public static double estimateCost(AiModel model, int htmlLength) {
        // Heuristic: ~4 characters per token for HTML/English text
        int promptTokens = htmlLength / 4;
        int completionTokens = ESTIMATED_COMPLETION_TOKENS;

        double inputRate;
        double outputRate;

        if (model == AiModel.GPT6_LUNA) {
            inputRate = GPT6_LUNA_INPUT_PER_1K;
            outputRate = GPT6_LUNA_OUTPUT_PER_1K;
        } else if (model == AiModel.GPT4O) {
            inputRate = GPT4O_INPUT_PER_1K;
            outputRate = GPT4O_OUTPUT_PER_1K;
        } else {
            // Default to GPT-4o-mini rates for GPT4O_MINI and any future models
            inputRate = GPT4O_MINI_INPUT_PER_1K;
            outputRate = GPT4O_MINI_OUTPUT_PER_1K;
        }

        return (promptTokens * inputRate + completionTokens * outputRate) / 1000.0;
    }
}
