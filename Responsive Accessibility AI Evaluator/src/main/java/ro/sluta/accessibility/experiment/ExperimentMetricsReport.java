package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;

public record ExperimentMetricsReport(String metricsVersion, int tp, int fp, int fn, int tn, int dup, int ov,
        double precision, StatisticalAnalysis.Interval precisionWilson95, double recall,
        StatisticalAnalysis.Interval recallWilson95, double f1, double specificity,
        StatisticalAnalysis.Interval specificityWilson95, BigDecimal totalCostUsd, BigDecimal costPerTpUsd,
        int invalidAiRuns, int plannedAiRuns, double invalidRate, Double meanLocalizationScore,
        Double meanWcagScore, Double meanExplanationQuality, Double meanJaccard, String zeroDivisionRule) { }
