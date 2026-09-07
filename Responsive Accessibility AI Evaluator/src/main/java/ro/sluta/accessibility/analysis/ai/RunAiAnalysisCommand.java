package ro.sluta.accessibility.analysis.ai;

import java.math.BigDecimal;
import java.util.UUID;
import ro.sluta.accessibility.domain.PageSnapshot;

public record RunAiAnalysisCommand(
        UUID runId,
        PageSnapshot snapshot,
        String modelId,
        AiCondition condition,
        AiRunProfile profile,
        BigDecimal maximumCostUsd,
        boolean paidActionConfirmed) {
}
