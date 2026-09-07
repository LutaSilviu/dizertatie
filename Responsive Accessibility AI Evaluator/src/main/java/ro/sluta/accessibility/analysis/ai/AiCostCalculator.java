package ro.sluta.accessibility.analysis.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.config.AiAnalysisProperties;

@Component
public class AiCostCalculator {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public AiCost actual(AiUsage usage, AiAnalysisProperties.Model model) {
        long uncached = Math.max(0, usage.inputTokens() - usage.cachedInputTokens());
        BigDecimal amount = priced(uncached, model.inputPricePerMillion())
                .add(priced(usage.cachedInputTokens(), model.cachedInputPricePerMillion()))
                .add(priced(usage.outputTokens(), model.outputPricePerMillion()));
        return new AiCost(amount.setScale(8, RoundingMode.HALF_UP), false);
    }

    public AiCost estimate(int inputCharacters, AiAnalysisProperties.Model model, int maxOutputTokens) {
        long estimatedInput = Math.max(1, (inputCharacters + 3L) / 4L);
        BigDecimal base = priced(estimatedInput, model.inputPricePerMillion())
                .add(priced(maxOutputTokens, model.outputPricePerMillion()));
        return new AiCost(base.multiply(BigDecimal.valueOf(1.20)).setScale(8, RoundingMode.HALF_UP), true);
    }

    private BigDecimal priced(long tokens, BigDecimal price) {
        return BigDecimal.valueOf(tokens).multiply(price == null ? BigDecimal.ZERO : price).divide(MILLION, 12, RoundingMode.HALF_UP);
    }
}
