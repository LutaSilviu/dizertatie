package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ro.sluta.accessibility.domain.DetectionClass;

@Service
public class ExperimentMetricsService {
    public static final String VERSION = "EXPERIMENT_METRICS_V1";
    private final EvaluationDecisionRepository evaluations; private final ExperimentPlanRepository plans;
    public ExperimentMetricsService(EvaluationDecisionRepository evaluations, ExperimentPlanRepository plans) { this.evaluations=evaluations;this.plans=plans; }

    public ExperimentMetricsReport regenerate(UUID planId) {
        ExperimentPlan plan=plans.find(planId).orElseThrow();List<EvaluationDecision> selected=selectFinal(evaluations.byPlan(planId));
        int tp=count(selected,DetectionClass.TP),fp=count(selected,DetectionClass.FP),fn=count(selected,DetectionClass.FN),tn=count(selected,DetectionClass.TN),dup=count(selected,DetectionClass.DUP),ov=count(selected,DetectionClass.OV);
        double precision=ratio(tp,tp+fp),recall=ratio(tp,tp+fn),specificity=ratio(tn,tn+fp);
        double f1=precision+recall==0?0:2*precision*recall/(precision+recall);BigDecimal total=plans.totalActualCost(planId);
        BigDecimal costPerTp=tp==0?null:total.divide(BigDecimal.valueOf(tp),10,RoundingMode.HALF_UP).stripTrailingZeros();
        int invalid=plans.invalidAiRuns(planId);return new ExperimentMetricsReport(VERSION,tp,fp,fn,tn,dup,ov,precision,
                StatisticalAnalysis.wilson(tp,tp+fp),recall,StatisticalAnalysis.wilson(tp,tp+fn),f1,specificity,
                StatisticalAnalysis.wilson(tn,tn+fp),total,costPerTp,invalid,plan.plannedAiCalls(),ratio(invalid,plan.plannedAiCalls()),
                average(selected,EvaluationDecision::localizationScore),average(selected,EvaluationDecision::wcagScore),
                averageDouble(selected,EvaluationDecision::qualityScore),meanJaccard(plans.findingKeys(planId)),
                "Raportul este 0 când numitorul este 0; F1 este 0 când precision și recall sunt ambele 0.");
    }

    static List<EvaluationDecision> selectFinal(List<EvaluationDecision> values) {
        Map<String,List<EvaluationDecision>> groups=new LinkedHashMap<>();for(EvaluationDecision value:values)groups.computeIfAbsent(key(value),ignored->new ArrayList<>()).add(value);
        List<EvaluationDecision> selected=new ArrayList<>();for(List<EvaluationDecision> group:groups.values())selected.add(group.stream().filter(v->v.decisionKind()==EvaluationDecisionKind.ADJUDICATED).findFirst().orElse(group.getFirst()));return selected;
    }
    public static double jaccard(Set<String> left,Set<String> right){if(left.isEmpty()&&right.isEmpty())return 1;Set<String> union=new java.util.HashSet<>(left);union.addAll(right);Set<String> intersection=new java.util.HashSet<>(left);intersection.retainAll(right);return intersection.size()/(double)union.size();}
    private static Double meanJaccard(List<ExperimentPlanRepository.FindingKeyRun> rows){
        Map<String,Map<Integer,Set<String>>> groups=new HashMap<>();for(var row:rows)groups.computeIfAbsent(row.caseId()+"|"+row.type()+"|"+row.modelId(),ignored->new HashMap<>()).computeIfAbsent(row.repetition(),ignored->new java.util.HashSet<>()).add(row.findingKey());
        List<Double> scores=new ArrayList<>();for(var repetitions:groups.values())for(int a=1;a<=3;a++)for(int b=a+1;b<=3;b++)if(repetitions.containsKey(a)&&repetitions.containsKey(b))scores.add(jaccard(repetitions.get(a),repetitions.get(b)));
        return scores.isEmpty()?null:scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
    private static String key(EvaluationDecision v){return v.runId()+"|"+v.findingId().map(UUID::toString).orElse("-")+"|"+v.groundTruthId();}
    private static int count(List<EvaluationDecision> values,DetectionClass type){return(int)values.stream().filter(v->v.detectionClass()==type).count();}
    private static double ratio(int numerator,int denominator){return denominator==0?0:numerator/(double)denominator;}
    private static Double average(List<EvaluationDecision> values,java.util.function.Function<EvaluationDecision,Integer> getter){return averageDouble(values,v->{Integer value=getter.apply(v);return value==null?null:value.doubleValue();});}
    private static Double averageDouble(List<EvaluationDecision> values,java.util.function.Function<EvaluationDecision,Double> getter){return values.stream().map(getter).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);}
}
