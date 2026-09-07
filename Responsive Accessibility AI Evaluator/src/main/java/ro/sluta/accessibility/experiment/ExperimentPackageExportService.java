package ro.sluta.accessibility.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExperimentPackageExportService {
    public static final String SCHEMA_VERSION="EXPERIMENT_PACKAGE_SCHEMA_V1",EXPORT_VERSION="EXPERIMENT_PACKAGE_EXPORT_V1";
    private final ExperimentPlanRepository plans;private final EvaluationDecisionRepository evaluations;
    private final ExperimentMetricsService metrics;private final ObjectMapper mapper;
    public ExperimentPackageExportService(ExperimentPlanRepository plans,EvaluationDecisionRepository evaluations,ExperimentMetricsService metrics,ObjectMapper mapper){this.plans=plans;this.evaluations=evaluations;this.metrics=metrics;this.mapper=mapper;}
    public byte[] export(UUID planId){try{Map<String,Object> root=new LinkedHashMap<>();root.put("schemaVersion",SCHEMA_VERSION);root.put("exportVersion",EXPORT_VERSION);root.put("configuration",mapper.readTree(plans.configurationJson(planId)));root.put("plan",plans.find(planId).orElseThrow());root.put("evaluations",evaluations.byPlan(planId));root.put("metrics",metrics.regenerate(planId));return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);}catch(Exception e){throw new IllegalStateException("Pachetul experimental nu poate fi exportat.",e);}}
}
