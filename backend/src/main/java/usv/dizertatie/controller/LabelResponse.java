package usv.dizertatie.controller;

public record LabelResponse(
        Long id,
        String url,
        String wcagCriterion,
        String wcagLevel,
        String severity,
        String elementSelector,
        String annotator,
        String verifiedBy,
        Double confidence
) {}
