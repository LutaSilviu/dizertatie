package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkPage;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.BenchmarkPageRepository;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.GroundTruthLabelRepository;
import usv.dizertatie.service.BenchmarkRunnerService;
import usv.dizertatie.service.MetricsService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final FindingRepository findingRepository;
    private final GroundTruthLabelRepository groundTruthLabelRepository;
    private final MetricsService metricsService;

    /**
     * Starts a new benchmark run asynchronously.
     *
     * @param request run configuration
     * @return map containing {@code runId} of the newly created (PENDING) run
     */
    @PostMapping
    public ResponseEntity<Map<String, Long>> createRun(@RequestBody BenchmarkRunRequest request) {
        AiModel model = AiModel.fromId(request.model());
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
        benchmarkRunnerService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkRun not found: " + id));

        List<BenchmarkPage> pages = benchmarkPageRepository.findByRunId(id);

        StringBuilder sb = new StringBuilder(
                "run_id,page_url,method,wcag_criterion,wcag_level,severity,element_selector,description,ground_truth_match");

        for (BenchmarkPage page : pages) {
            List<GroundTruthLabel> labels = groundTruthLabelRepository.findByUrl(page.getUrl());
            Set<String> labelKeys = labels.stream()
                    .map(l -> labelKey(l.getWcagCriterion(), l.getElementSelector()))
                    .collect(Collectors.toSet());

            appendFindingsForMethod(sb, id, page.getUrl(), "AXE", page.getAxeScanId(), labelKeys);
            appendFindingsForMethod(sb, id, page.getUrl(), "AI", page.getAiScanId(), labelKeys);
            appendFindingsForMethod(sb, id, page.getUrl(), "COMBINED", page.getCombinedScanId(), labelKeys);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/csv");
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"benchmark-" + id + ".csv\"");

        return new ResponseEntity<>(sb.toString(), headers, HttpStatus.OK);
    }

    private void appendFindingsForMethod(StringBuilder sb, Long runId, String pageUrl,
                                          String method, Long scanId, Set<String> labelKeys) {
        if (scanId == null) {
            return;
        }
        List<Finding> findings = findingRepository.findByScanId(scanId);
        for (Finding f : findings) {
            boolean match = labelKeys.contains(labelKey(f.getWcagCriterion(), f.getElementSelector()));
            sb.append('\n');
            sb.append(escapeCsv(runId == null ? "" : runId.toString()));
            sb.append(',');
            sb.append(escapeCsv(pageUrl));
            sb.append(',');
            sb.append(escapeCsv(method));
            sb.append(',');
            sb.append(escapeCsv(f.getWcagCriterion()));
            sb.append(',');
            sb.append(escapeCsv(f.getWcagLevel()));
            sb.append(',');
            sb.append(escapeCsv(f.getSeverity()));
            sb.append(',');
            sb.append(escapeCsv(f.getElementSelector()));
            sb.append(',');
            sb.append(escapeCsv(f.getDescription()));
            sb.append(',');
            sb.append(match);
        }
    }

    private static String labelKey(String wcagCriterion, String elementSelector) {
        return (wcagCriterion == null ? "" : wcagCriterion)
                + "|"
                + (elementSelector == null ? "" : elementSelector);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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
