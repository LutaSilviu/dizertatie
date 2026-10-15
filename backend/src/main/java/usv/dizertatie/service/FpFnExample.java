package usv.dizertatie.service;

/**
 * A single false-positive or false-negative example surfaced during metric computation.
 */
public record FpFnExample(
        String method,
        String url,
        String wcagCriterion,
        String elementSelector,
        String description
) {}
