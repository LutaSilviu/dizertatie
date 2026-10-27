package usv.dizertatie.controller;

public record FindingResponse(
        String sourceMethod,
        String wcagCriterion,
        String wcagLevel,
        String ruleId,
        String severity,
        String elementSelector,
        String description
) {
}
