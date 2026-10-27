package usv.dizertatie.axe;

/** One affected element belonging to an axe-core manual-review rule. */
public record ManualReviewElement(String target, String html, String failureSummary) {
}
