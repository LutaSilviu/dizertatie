package ro.sluta.accessibility.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatisticalAnalysisTest {
    @Test void implementsFrozenWilsonBootstrapMcnemarWilcoxonHolmAndKappaRules() {
        var wilson=StatisticalAnalysis.wilson(8,10);assertThat(wilson.lower()).isBetween(.49,.50);assertThat(wilson.upper()).isBetween(.94,.95);
        var bootstrap=StatisticalAnalysis.pairedBootstrap(new double[]{.8,.7,.9,.6},new double[]{.5,.5,.7,.5},10_000,42);
        assertThat(bootstrap.estimate()).isEqualTo(.2);assertThat(bootstrap.interval().lower()).isLessThanOrEqualTo(.2);assertThat(bootstrap.interval().upper()).isGreaterThanOrEqualTo(.2);
        assertThat(StatisticalAnalysis.mcnemarExactPValue(0,5)).isEqualTo(.0625);
        var wilcoxon=StatisticalAnalysis.wilcoxon(new double[]{3,4,5,8},new double[]{1,2,4,5});assertThat(wilcoxon.pairs()).isEqualTo(4);assertThat(wilcoxon.wPlus()).isGreaterThan(wilcoxon.wMinus());
        Map<String,Double> holm=StatisticalAnalysis.holm(Map.of("a",.01,"b",.03,"c",.20));assertThat(holm.get("a")).isEqualTo(.03);assertThat(holm.get("b")).isEqualTo(.06);assertThat(holm.get("c")).isEqualTo(.20);
        assertThat(StatisticalAnalysis.cohenKappa(List.of("TP","FN","TP"),List.of("TP","FN","TP"))).isEqualTo(1);
        assertThat(StatisticalAnalysis.weightedKappa(List.of(0,1,2),List.of(0,1,2),2)).isEqualTo(1);
    }

    @Test void jaccardUsesNormalizedFindingKeysAndDefinesTwoEmptySetsAsStable() {
        assertThat(ExperimentMetricsService.jaccard(java.util.Set.of("a","b"),java.util.Set.of("b","c"))).isEqualTo(1d/3d);
        assertThat(ExperimentMetricsService.jaccard(java.util.Set.of(),java.util.Set.of())).isEqualTo(1);
    }
}
