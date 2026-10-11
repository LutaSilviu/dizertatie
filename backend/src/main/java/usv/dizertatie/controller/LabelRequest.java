package usv.dizertatie.controller;

public record LabelRequest(
        String url,
        String wcagCriterion,
        String wcagLevel,
        String severity,
        String elementSelector,
        String annotator,
        String verifiedBy,
        Double confidence
) {}
