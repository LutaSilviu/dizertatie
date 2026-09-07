package ro.sluta.accessibility.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.sluta.accessibility.benchmark.BenchmarkCatalog;
import ro.sluta.accessibility.domain.GroundTruthIssue;
import ro.sluta.accessibility.persistence.ExperimentConfigurationRecord;
import ro.sluta.accessibility.persistence.ResearchPersistenceService;

@Component
public class ExperimentPlanRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final ResearchPersistenceService research;
    private final BenchmarkCatalog benchmark;
    public ExperimentPlanRepository(JdbcClient jdbc, ObjectMapper mapper, ResearchPersistenceService research,
            BenchmarkCatalog benchmark) {
        this.jdbc = jdbc; this.mapper = mapper; this.research = research; this.benchmark = benchmark;
    }

    @Transactional
    public ExperimentPlan saveIdempotently(ExperimentConfigurationSnapshot configuration, ExperimentPlan plan) {
        synchronizeGroundTruth();
        long configurationCount = jdbc.sql("select count(*) from experiment_configuration where configuration_id=:id")
                .param("id", configuration.configurationId()).query(Long.class).single();
        if (configurationCount == 0) research.saveExperimentConfiguration(new ExperimentConfigurationRecord(
                configuration.configurationId(), "experiment-" + configuration.configurationId(),
                configuration.configurationVersion(), json(configuration), configuration.configurationHash(),
                configuration.frozenAt(), 0));
        Optional<ExperimentPlan> existing = find(plan.planId());
        if (existing.isPresent()) return existing.get();
        jdbc.sql("""
                insert into experiment_plan(plan_id,configuration_id,mode,random_seed,ai_concurrency,hard_budget_usd,
                  max_cost_per_ai_call_usd,planned_cases,planned_axe_runs,planned_ai_text_runs,
                  planned_ai_multimodal_runs,planned_hybrid_results,estimated_max_cost_usd,manifest_json,manifest_hash,
                  status,created_at,updated_at,version)
                values(:id,:configuration,:mode,:seed,:concurrency,:budget,:maxCall,:cases,:axe,:text,:multimodal,
                  :hybrid,:estimated,:manifest,:hash,:status,:created,:updated,0)
                """).param("id", plan.planId()).param("configuration", plan.configurationId())
                .param("mode", plan.mode().name()).param("seed", plan.randomSeed()).param("concurrency", plan.aiConcurrency())
                .param("budget", plan.hardBudgetUsd()).param("maxCall", plan.maxCostPerAiCallUsd())
                .param("cases", plan.plannedCases()).param("axe", plan.plannedAxeRuns()).param("text", plan.plannedAiTextRuns())
                .param("multimodal", plan.plannedAiMultimodalRuns()).param("hybrid", plan.plannedHybridResults())
                .param("estimated", plan.estimatedMaxCostUsd()).param("manifest", json(plan.executions()))
                .param("hash", plan.manifestHash()).param("status", plan.status().name())
                .param("created", utc(plan.createdAt())).param("updated", utc(plan.createdAt())).update();
        for (PlannedExecution execution : plan.executions()) insert(execution);
        return find(plan.planId()).orElseThrow();
    }

    private void synchronizeGroundTruth() {
        for (GroundTruthIssue truth : benchmark.cases().stream().map(value -> value.groundTruth()).toList()) {
            List<TruthIdentity> existing = jdbc.sql("""
                    select fixture_version,fixture_hash from ground_truth_issue where ground_truth_id=:id
                    """).param("id", truth.groundTruthId()).query((rs, row) ->
                            new TruthIdentity(rs.getString(1), rs.getString(2))).list();
            if (existing.isEmpty()) {
                research.saveGroundTruth(truth);
            } else if (!existing.getFirst().equals(new TruthIdentity(truth.fixtureVersion(), truth.fixtureHash()))) {
                throw new IllegalStateException("Ground truth existent cu versiune sau hash diferit: " + truth.groundTruthId());
            }
        }
    }

    public Optional<ExperimentPlan> find(UUID planId) {
        Optional<ExperimentPlan> header = jdbc.sql("select * from experiment_plan where plan_id=:id").param("id", planId)
                .query((rs, row) -> new ExperimentPlan(uuid(rs, "plan_id"), uuid(rs, "configuration_id"),
                        ExperimentMode.valueOf(rs.getString("mode")), rs.getLong("random_seed"), rs.getInt("ai_concurrency"),
                        decimal(rs, "hard_budget_usd"), decimal(rs, "max_cost_per_ai_call_usd"), rs.getInt("planned_cases"),
                        rs.getInt("planned_axe_runs"), rs.getInt("planned_ai_text_runs"), rs.getInt("planned_ai_multimodal_runs"),
                        rs.getInt("planned_hybrid_results"), decimal(rs, "estimated_max_cost_usd"), rs.getString("manifest_hash"),
                        PlanStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), List.of())).optional();
        return header.map(value -> new ExperimentPlan(value.planId(), value.configurationId(), value.mode(),
                value.randomSeed(), value.aiConcurrency(), value.hardBudgetUsd(), value.maxCostPerAiCallUsd(),
                value.plannedCases(), value.plannedAxeRuns(), value.plannedAiTextRuns(), value.plannedAiMultimodalRuns(),
                value.plannedHybridResults(), value.estimatedMaxCostUsd(), value.manifestHash(), value.status(),
                value.createdAt(), executions(planId)));
    }

    @Transactional
    public Optional<PlannedExecution> reserveNext(UUID planId, boolean paidActionConfirmed, Instant now) {
        ExperimentPlan plan = find(planId).orElseThrow();
        if (plan.status() == PlanStatus.COMPLETED || plan.status() == PlanStatus.PAUSED_BUDGET) return Optional.empty();
        long runningAi = jdbc.sql("""
                select count(*) from experiment_execution where plan_id=:plan and status='RUNNING'
                  and execution_type in ('AI_TEXT','AI_MULTIMODAL')
                """).param("plan", planId).query(Long.class).single();
        List<PlannedExecution> candidates = jdbc.sql("""
                select * from experiment_execution where plan_id=:plan and status='PLANNED'
                order by execution_order limit 1
                """).param("plan", planId).query(this::execution).list();
        if (candidates.isEmpty()) { updatePlanStatus(planId, PlanStatus.COMPLETED, now); return Optional.empty(); }
        PlannedExecution candidate = candidates.getFirst();
        if (candidate.paid() && !paidActionConfirmed) throw new IllegalStateException("Rezervarea unui apel AI cere confirmare plătită explicită.");
        if (candidate.paid() && runningAi >= plan.aiConcurrency()) return Optional.empty();
        BigDecimal committed = jdbc.sql("""
                select coalesce(sum(case when status='RUNNING' then reserved_cost_usd else actual_cost_usd end),0)
                from experiment_execution where plan_id=:plan
                """).param("plan", planId).query(BigDecimal.class).single();
        BigDecimal reservation = candidate.paid() ? plan.maxCostPerAiCallUsd() : BigDecimal.ZERO;
        if (committed.add(reservation).compareTo(plan.hardBudgetUsd()) > 0) {
            updatePlanStatus(planId, PlanStatus.PAUSED_BUDGET, now); return Optional.empty();
        }
        int changed = jdbc.sql("""
                update experiment_execution set status='RUNNING',reserved_cost_usd=:reserved,started_at=:started,
                  version=version+1 where execution_id=:id and status='PLANNED'
                """).param("reserved", reservation).param("started", utc(now)).param("id", candidate.executionId()).update();
        if (changed != 1) return Optional.empty();
        updatePlanStatus(planId, PlanStatus.RUNNING, now);
        return findExecution(candidate.executionId());
    }

    @Transactional
    public PlannedExecution complete(UUID executionId, ExecutionStatus status, BigDecimal actualCostUsd,
            UUID runId, String errorCode, Instant now) {
        if (status == ExecutionStatus.PLANNED || status == ExecutionStatus.RUNNING) throw new IllegalArgumentException("Stare finală invalidă.");
        BigDecimal cost = actualCostUsd == null ? BigDecimal.ZERO : actualCostUsd;
        PlannedExecution current = findExecution(executionId).orElseThrow();
        if (cost.signum() < 0 || cost.compareTo(current.reservedCostUsd()) > 0) throw new IllegalArgumentException("Costul real depășește rezervarea.");
        int changed = jdbc.sql("""
                update experiment_execution set status=:status,actual_cost_usd=:cost,run_id=:run,error_code=:error,
                  completed_at=:completed,version=version+1 where execution_id=:id and status='RUNNING'
                """).param("status", status.name()).param("cost", cost).param("run", runId, Types.OTHER)
                .param("error", errorCode, Types.VARCHAR).param("completed", utc(now)).param("id", executionId).update();
        if (changed != 1) throw new IllegalStateException("Execuția nu este rezervată RUNNING.");
        return findExecution(executionId).orElseThrow();
    }

    public List<PlannedExecution> executions(UUID planId) {
        return jdbc.sql("select * from experiment_execution where plan_id=:plan order by execution_order")
                .param("plan", planId).query(this::execution).list();
    }

    public BigDecimal totalActualCost(UUID planId) {
        return jdbc.sql("select coalesce(sum(actual_cost_usd),0) from experiment_execution where plan_id=:plan")
                .param("plan", planId).query(BigDecimal.class).single().stripTrailingZeros();
    }
    public int invalidAiRuns(UUID planId) {
        return jdbc.sql("""
                select count(*) from experiment_execution where plan_id=:plan and status='INVALID'
                  and execution_type in ('AI_TEXT','AI_MULTIMODAL')
                """).param("plan", planId).query(Integer.class).single();
    }
    public List<FindingKeyRun> findingKeys(UUID planId) {
        return jdbc.sql("""
                select e.case_id,e.execution_type,e.model_id,e.repetition,f.finding_key
                from experiment_execution e join predicted_finding f on f.run_id=e.run_id
                where e.plan_id=:plan and e.execution_type in ('AI_TEXT','AI_MULTIMODAL')
                order by e.case_id,e.execution_type,e.model_id,e.repetition,f.finding_key
                """).param("plan", planId).query((rs,row)->new FindingKeyRun(rs.getString(1),ExecutionType.valueOf(rs.getString(2)),
                        rs.getString(3),rs.getInt(4),rs.getString(5))).list();
    }
    public String configurationJson(UUID planId) {
        return jdbc.sql("""
                select c.configuration_json from experiment_configuration c
                join experiment_plan p on p.configuration_id=c.configuration_id where p.plan_id=:plan
                """).param("plan", planId).query(String.class).single();
    }
    public record FindingKeyRun(String caseId, ExecutionType type, String modelId, int repetition, String findingKey) { }
    private record TruthIdentity(String fixtureVersion, String fixtureHash) { }

    private Optional<PlannedExecution> findExecution(UUID id) {
        return jdbc.sql("select * from experiment_execution where execution_id=:id").param("id", id).query(this::execution).optional();
    }
    private void insert(PlannedExecution value) {
        jdbc.sql("""
                insert into experiment_execution(execution_id,plan_id,execution_order,execution_key,case_id,
                  execution_type,model_id,repetition,status,reserved_cost_usd,actual_cost_usd,run_id,error_code,version)
                values(:id,:plan,:executionOrder,:executionKey,:caseId,:type,:model,:repetition,:status,0,0,null,null,0)
                """).param("id", value.executionId()).param("plan", value.planId()).param("executionOrder", value.order())
                .param("executionKey", value.executionKey()).param("caseId", value.caseId()).param("type", value.type().name())
                .param("model", value.modelId(), Types.VARCHAR).param("repetition", value.repetition(), Types.INTEGER)
                .param("status", value.status().name()).update();
    }
    private void updatePlanStatus(UUID id, PlanStatus status, Instant now) {
        jdbc.sql("update experiment_plan set status=:status,updated_at=:updated,version=version+1 where plan_id=:id")
                .param("status", status.name()).param("updated", utc(now)).param("id", id).update();
    }
    private PlannedExecution execution(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PlannedExecution(uuid(rs, "execution_id"), uuid(rs, "plan_id"), rs.getInt("execution_order"),
                rs.getString("execution_key"), rs.getString("case_id"), ExecutionType.valueOf(rs.getString("execution_type")),
                rs.getString("model_id"), integer(rs, "repetition"), ExecutionStatus.valueOf(rs.getString("status")),
                decimal(rs, "reserved_cost_usd"), decimal(rs, "actual_cost_usd"), uuidNullable(rs, "run_id"), rs.getString("error_code"));
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Serializare manifest eșuată.", e); } }
    private static OffsetDateTime utc(Instant value) { return value.truncatedTo(java.time.temporal.ChronoUnit.MICROS).atOffset(ZoneOffset.UTC); }
    private static Instant instant(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name, OffsetDateTime.class).toInstant(); }
    private static UUID uuid(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name, UUID.class); }
    private static UUID uuidNullable(java.sql.ResultSet rs, String name) throws java.sql.SQLException { return rs.getObject(name) == null ? null : rs.getObject(name, UUID.class); }
    private static Integer integer(java.sql.ResultSet rs, String name) throws java.sql.SQLException { int value = rs.getInt(name); return rs.wasNull() ? null : value; }
    private static BigDecimal decimal(java.sql.ResultSet rs, String name) throws java.sql.SQLException { BigDecimal value = rs.getBigDecimal(name); return value == null ? null : value.stripTrailingZeros(); }
}
