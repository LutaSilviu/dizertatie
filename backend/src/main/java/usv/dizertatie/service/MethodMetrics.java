package usv.dizertatie.service;

/**
 * Precision / recall / F1 metrics for a single detection method.
 *
 * @param precision TP / (TP + FP)
 * @param recall    TP / (TP + FN)
 * @param f1        harmonic mean of precision and recall
 */
public record MethodMetrics(double precision, double recall, double f1) {

    /**
     * Computes {@link MethodMetrics} from raw TP / FP / FN counts.
     *
     * @param tp true-positive count
     * @param fp false-positive count
     * @param fn false-negative count
     * @return computed metrics (all zero when TP+FP+FN == 0)
     */
    public static MethodMetrics of(int tp, int fp, int fn) {
        double precision = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall    = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
        double f1        = (precision + recall) == 0.0 ? 0.0
                : 2.0 * precision * recall / (precision + recall);
        return new MethodMetrics(precision, recall, f1);
    }
}
