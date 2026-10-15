package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.BenchmarkRun;
import usv.dizertatie.service.BenchmarkRunnerService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/benchmark-runs")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkRunnerService benchmarkRunnerService;

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

    @GetMapping("/{id}/status")
    public ResponseEntity<BenchmarkRunStatusResponse> getRunStatus(@PathVariable Long id) {
        BenchmarkRun run = benchmarkRunnerService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BenchmarkRun not found: " + id));
        return ResponseEntity.ok(toStatusResponse(run));
    }

    @GetMapping
    public List<BenchmarkRunStatusResponse> listRuns() {
        return benchmarkRunnerService.findAllNewestFirst().stream()
                .map(this::toStatusResponse)
                .toList();
    }

    private BenchmarkRunStatusResponse toStatusResponse(BenchmarkRun run) {
        return new BenchmarkRunStatusResponse(
                run.getId(),
                run.getName(),
                run.getStatus(),
                run.getPagesDone(),
                run.getTotalPages(),
                run.getCreatedAt()
        );
    }
}
