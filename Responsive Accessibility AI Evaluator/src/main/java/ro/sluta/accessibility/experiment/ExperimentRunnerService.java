package ro.sluta.accessibility.experiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExperimentRunnerService {
    private final ExperimentConfigurationFactory configurations;
    private final ExperimentPlanBuilder plans;
    private final ExperimentPlanRepository repository;
    public ExperimentRunnerService(ExperimentConfigurationFactory configurations, ExperimentPlanBuilder plans,
            ExperimentPlanRepository repository) { this.configurations = configurations; this.plans = plans; this.repository = repository; }

    public DryRun dryRun(ExperimentFreezeRequest request) {
        var configuration = configurations.freeze(request);
        return new DryRun(configuration, plans.build(configuration, ExperimentMode.DRY_RUN), false, false);
    }
    public ExperimentPlan createPlan(ExperimentFreezeRequest request, ExperimentMode mode, boolean paidActionConfirmed) {
        if (mode == ExperimentMode.DRY_RUN) throw new IllegalArgumentException("Folosește dryRun pentru planul fără execuție.");
        if (mode == ExperimentMode.FINAL && !paidActionConfirmed) {
            throw new IllegalStateException("Experimentul final de 576 apeluri cere confirmare plătită explicită.");
        }
        var configuration = configurations.freeze(request); return repository.saveIdempotently(configuration, plans.build(configuration, mode));
    }
    public Optional<PlannedExecution> reserveNext(UUID planId, boolean paidActionConfirmed) {
        return repository.reserveNext(planId, paidActionConfirmed, Instant.now());
    }
    public ExperimentPlan resume(UUID planId) {
        return repository.find(planId).orElseThrow(() -> new IllegalArgumentException("Planul experimental nu există."));
    }
    public PlannedExecution complete(UUID executionId, ExecutionStatus status, BigDecimal actualCostUsd,
            UUID runId, String errorCode) {
        return repository.complete(executionId, status, actualCostUsd, runId, errorCode, Instant.now());
    }
    public Optional<ExperimentPlan> find(UUID planId) { return repository.find(planId); }
    public record DryRun(ExperimentConfigurationSnapshot configuration, ExperimentPlan plan,
            boolean browserStarted, boolean apiCalled) { }
}
