package usv.dizertatie.service;

/**
 * Precision, recall and F1 score for a single scan method.
 */
public record MethodMetrics(double precision, double recall, double f1) {

    public static final MethodMetrics ZERO = new MethodMetrics(0.0, 0.0, 0.0);

    public static MethodMetrics of(int tp, int fp, int fn) {
        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1        = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;
        return new MethodMetrics(precision, recall, f1);
    }
}
