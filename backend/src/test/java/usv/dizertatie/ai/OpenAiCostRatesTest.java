package usv.dizertatie.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link OpenAiCostRates#estimateCost(AiModel, int)}.
 */
class OpenAiCostRatesTest {

    @Test
    void estimateCost_gpt4o_withTypicalHtml_returnsPositiveCost() {
        // 15,000 chars → ~3,750 prompt tokens; 500 completion tokens
        double cost = OpenAiCostRates.estimateCost(AiModel.GPT4O, 15_000);

        assertThat(cost).isPositive();
    }

    @Test
    void estimateCost_gpt4oMini_withTypicalHtml_returnsPositiveCost() {
        double cost = OpenAiCostRates.estimateCost(AiModel.GPT4O_MINI, 15_000);

        assertThat(cost).isPositive();
    }

    @Test
    void estimateCost_gpt4o_isMoreExpensiveThanMini() {
        int htmlLength = 15_000;
        double costGpt4o = OpenAiCostRates.estimateCost(AiModel.GPT4O, htmlLength);
        double costMini = OpenAiCostRates.estimateCost(AiModel.GPT4O_MINI, htmlLength);

        assertThat(costGpt4o).isGreaterThan(costMini);
    }

    @Test
    void estimateCost_gpt4o_matchesManualCalculation() {
        // promptTokens = 8000 / 4 = 2000; completionTokens = 500
        // cost = (2000 * 0.0025 + 500 * 0.010) / 1000 = (5.0 + 5.0) / 1000 = 0.010
        double cost = OpenAiCostRates.estimateCost(AiModel.GPT4O, 8_000);

        assertThat(cost).isCloseTo(0.010, within(1e-9));
    }

    @Test
    void estimateCost_gpt4oMini_matchesManualCalculation() {
        // promptTokens = 8000 / 4 = 2000; completionTokens = 500
        // cost = (2000 * 0.00015 + 500 * 0.00060) / 1000 = (0.30 + 0.30) / 1000 = 0.0006
        double cost = OpenAiCostRates.estimateCost(AiModel.GPT4O_MINI, 8_000);

        assertThat(cost).isCloseTo(0.0006, within(1e-9));
    }

    @Test
    void estimateCost_zeroHtmlLength_returnsCompletionTokenCostOnly() {
        // promptTokens = 0; completionTokens = 500
        // GPT-4o: (0 + 500 * 0.010) / 1000 = 5.0 / 1000 = 0.005
        double cost = OpenAiCostRates.estimateCost(AiModel.GPT4O, 0);

        assertThat(cost).isCloseTo(0.005, within(1e-9));
    }

    @Test
    void estimateCost_largerHtmlLength_producesHigherCost() {
        double costSmall = OpenAiCostRates.estimateCost(AiModel.GPT4O, 5_000);
        double costLarge = OpenAiCostRates.estimateCost(AiModel.GPT4O, 50_000);

        assertThat(costLarge).isGreaterThan(costSmall);
    }
}
