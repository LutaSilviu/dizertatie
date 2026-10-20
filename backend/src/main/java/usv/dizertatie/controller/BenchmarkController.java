package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.export.BenchmarkCsvExporter;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.service.BenchmarkRunnerService;
import usv.dizertatie.service.MetricsService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for managing benchmark runs.
 *
 * <ul>
 *   <li>{@code POST /benchmark-runs} — start a new async benchmark run</li>
 *   <li>{@code GET  /benchmark-runs} — list all runs, newest first</li>
 *   <li>{@code GET  /benchmark-runs/{id}/status} — poll run progress and, when complete, latency + cost averages</li>
 *   <li>{@code GET  /benchmark-runs/{id}/export.csv} — export all findings as CSV</li>
 * </ul>
 */
@RestController
@RequestMapping("/benchmark-runs")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkRunnerService benchmarkRunnerService;
    private final BenchmarkPageRepository benchmarkPageRepository;
    private final BenchmarkCsvExporter benchmarkCsvExporter;
    private final MetricsService metricsService;

    /**
     * Starts a new benchmark run asynchronously.
     *
     * @param request run configuration
     * @return map containing {@code runId} of the newly created (PENDING) run
     */
    @PostMapping
    public ResponseEntity<Map<String, Long>> createRun(@RequestBody BenchmarkRunRequest request) {
        AiModel model;
        try {
            model = AiModel.fromId(request.model());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        BenchmarkRun run = benchmarkRunnerService.createRun(
                request.name(),
                request.urls(),
                request.useBuiltinSet(),
                model
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("runId", run.getId()));
    }

    /**
     * Returns all benchmark runs ordered by creation date descending.
     */
    @GetMapping
    public List<BenchmarkRunStatusResponse> listRuns() {
        return benchmarkRunnerService.findAllNewestFirst().stream()
                .map(run -> toStatusResponse(run, false))
                .toList();
    }

    /**
     * Returns the current status of a benchmark run.
     *
     * <p>When {@code status=COMPLETE}, the response also includes average latency and cost
     * metrics computed over all successfully-scanned pages.
     *
     * @param id run identifier
     * @return status response; latency/cost fields are {@code null} unless status=COMPLETE
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<BenchmarkRunStatusResponse> getRunStatus(@PathVariable Long id) {
        BenchmarkRun run = benchmarkRunnerService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found: " + id));
        return ResponseEntity.ok(toStatusResponse(run, true));
    }

    /**
     * Exports all findings for a benchmark run as a CSV file.
     *
     * <p>One row per finding across all pages and all three methods (AXE, AI, COMBINED).
     * The {@code ground_truth_match} column is {@code true} when the finding matches a
     * ground truth label on both {@code wcagCriterion} and {@code elementSelector}.
     *
     * @param id run identifier
     * @return CSV download; 404 if run not found
     */
    @GetMapping("/{id}/export.csv")
    public ResponseEntity<String> exportCsv(@PathVariable Long id) {
        BenchmarkRun run = benchmarkRunnerService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found: " + id));
        if (!"COMPLETE".equals(run.getStatus()) && !"FAILED".equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run is still in progress");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/csv");
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"benchmark-" + id + ".csv\"");

        return new ResponseEntity<>(benchmarkCsvExporter.export(run.getId()), headers, HttpStatus.OK);
    }

    /**
     * Returns full benchmark metrics for a completed run.
     *
     * <p>Returns 404 when the run does not exist, and 409 when the run is not yet COMPLETE.
     *
     * @param id run identifier
     * @return metrics response
     */
    @GetMapping("/{id}/metrics")
    public ResponseEntity<BenchmarkMetricsResponse> getRunMetrics(@PathVariable Long id) {
        BenchmarkRun run = benchmarkRunnerService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found: " + id));

        if (!"COMPLETE".equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run is still in progress");
        }

        BenchmarkMetricsResponse response = BenchmarkMetricsResponse.from(metricsService.compute(run));
        return ResponseEntity.ok(response);
    }

    private BenchmarkRunStatusResponse toStatusResponse(BenchmarkRun run, boolean includeAvgs) {
        Double avgAxe = null;
        Double avgAi = null;
        Double avgCombined = null;
        Double avgCost = null;

        if (includeAvgs && "COMPLETE".equals(run.getStatus())) {
            avgAxe = benchmarkPageRepository.avgLatencyAxeMsByRunId(run.getId());
            avgAi = benchmarkPageRepository.avgLatencyAiMsByRunId(run.getId());
            avgCombined = benchmarkPageRepository.avgLatencyCombinedMsByRunId(run.getId());
            avgCost = benchmarkPageRepository.avgEstimatedCostUsdByRunId(run.getId());
        }

        return new BenchmarkRunStatusResponse(
                run.getId(),
                run.getName(),
                run.getStatus(),
                run.getPagesDone(),
                run.getTotalPages(),
                run.getCreatedAt(),
                avgAxe,
                avgAi,
                avgCombined,
                avgCost
        );
    }
}
