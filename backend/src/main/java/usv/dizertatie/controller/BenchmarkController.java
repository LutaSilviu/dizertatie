package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.BenchmarkRunRepository;
import usv.dizertatie.service.BenchmarkRunnerService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for managing benchmark runs.
 *
 * <ul>
 *   <li>{@code POST /benchmark-runs} — start a new async benchmark run</li>
 *   <li>{@code GET  /benchmark-runs} — list all runs, newest first</li>
 *   <li>{@code GET  /benchmark-runs/{id}/status} — poll run progress and, when complete, latency + cost averages</li>
 * </ul>
 */
@RestController
@RequestMapping("/benchmark-runs")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkRunnerService benchmarkRunnerService;
    private final BenchmarkRunRepository benchmarkRunRepository;
    private final BenchmarkPageRepository benchmarkPageRepository;

    /**
     * Starts a new benchmark run asynchronously.
     *
     * @param request run configuration
     * @return map containing {@code runId} of the newly created (PENDING) run
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createRun(@RequestBody BenchmarkRunRequest request) {
        BenchmarkRun run = benchmarkRunnerService.createRun(
                request.name(),
                request.urls(),
                request.useBuiltinSet(),
                request.model()
        );
        return Map.of("runId", run.getId());
    }

    /**
     * Returns all benchmark runs ordered by creation date descending.
     */
    @GetMapping
    public List<BenchmarkRunSummaryResponse> listRuns() {
        return benchmarkRunRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(run -> new BenchmarkRunSummaryResponse(
                        run.getId(),
                        run.getName(),
                        run.getStatus(),
                        run.getPagesDone(),
                        run.getTotalPages(),
                        run.getCreatedAt()
                ))
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
    public BenchmarkRunStatusResponse getStatus(@PathVariable Long id) {
        BenchmarkRun run = benchmarkRunRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found: " + id));

        Double avgAxe = null;
        Double avgAi = null;
        Double avgCombined = null;
        Double avgCost = null;

        if ("COMPLETE".equals(run.getStatus())) {
            avgAxe = benchmarkPageRepository.avgLatencyAxeMsByRunId(id);
            avgAi = benchmarkPageRepository.avgLatencyAiMsByRunId(id);
            avgCombined = benchmarkPageRepository.avgLatencyCombinedMsByRunId(id);
            avgCost = benchmarkPageRepository.avgEstimatedCostUsdByRunId(id);
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
