package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecutionCompletionRequest(ExecutionStatus status, BigDecimal actualCostUsd,
        UUID runId, String errorCode) { }
