package ro.sluta.accessibility.web;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ro.sluta.accessibility.experiment.*;

@Controller
public class ExperimentController {
    private final ExperimentRunnerService runner;
    public ExperimentController(ExperimentRunnerService runner) { this.runner = runner; }

    @GetMapping("/experiment")
    public String dashboard(Model model) {
        ExperimentFreezeRequest defaults = new ExperimentFreezeRequest(20260901L, 1,
                new BigDecimal("10.00"), new BigDecimal("0.01"), java.util.List.of("S01", "S04"));
        model.addAttribute("dryRun", runner.dryRun(defaults));
        return "experiment";
    }

    @ResponseBody
    @PostMapping("/api/v1/experiments/dry-run")
    public ResponseEntity<?> dryRun(@RequestBody ExperimentFreezeRequest request) {
        return ResponseEntity.ok(runner.dryRun(request));
    }

    @ResponseBody
    @PostMapping("/api/v1/experiments/plans/{mode}")
    public ResponseEntity<?> create(@PathVariable ExperimentMode mode, @RequestBody ExperimentFreezeRequest request,
            @RequestParam(defaultValue = "false") boolean paidActionConfirmed) {
        return ResponseEntity.status(201).body(runner.createPlan(request, mode, paidActionConfirmed));
    }

    @ResponseBody
    @GetMapping("/api/v1/experiments/plans/{planId}")
    public ResponseEntity<?> plan(@PathVariable UUID planId) {
        return runner.find(planId).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ResponseBody
    @PostMapping("/api/v1/experiments/plans/{planId}/reserve-next")
    public ResponseEntity<?> reserve(@PathVariable UUID planId,
            @RequestParam(defaultValue = "false") boolean paidActionConfirmed) {
        return runner.reserveNext(planId, paidActionConfirmed).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @ResponseBody
    @PostMapping("/api/v1/experiments/plans/{planId}/resume")
    public ResponseEntity<?> resume(@PathVariable UUID planId) {
        return ResponseEntity.ok(runner.resume(planId));
    }

    @ResponseBody
    @PostMapping("/api/v1/experiments/executions/{executionId}/complete")
    public ResponseEntity<?> complete(@PathVariable UUID executionId,
            @RequestBody ExecutionCompletionRequest request) {
        return ResponseEntity.ok(runner.complete(executionId, request.status(), request.actualCostUsd(),
                request.runId(), request.errorCode()));
    }
}
