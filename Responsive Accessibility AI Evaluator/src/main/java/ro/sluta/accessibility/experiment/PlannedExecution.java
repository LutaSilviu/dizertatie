package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.util.UUID;

public record PlannedExecution(UUID executionId, UUID planId, int order, String executionKey, String caseId,
        ExecutionType type, String modelId, Integer repetition, ExecutionStatus status,
        BigDecimal reservedCostUsd, BigDecimal actualCostUsd, UUID runId, String errorCode) {
    public boolean paid() { return type == ExecutionType.AI_TEXT || type == ExecutionType.AI_MULTIMODAL; }
}
