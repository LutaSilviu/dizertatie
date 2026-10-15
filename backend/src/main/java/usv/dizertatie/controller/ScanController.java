package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.export.CsvSerializer;
import usv.dizertatie.scoring.ConformanceScoringService;
import usv.dizertatie.scoring.ConformanceScores;
import usv.dizertatie.scoring.SeverityScoringService;
import usv.dizertatie.service.ScanService;

import java.util.List;

@RestController
@RequestMapping("/scans")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;
    private final SeverityScoringService severityScoringService;
    private final ConformanceScoringService conformanceScoringService;
    private final CsvSerializer csvSerializer;

    @GetMapping
    public ScanHistoryResponse getScans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scanService.findAll(page, size);
    }

    @PostMapping
    public ResponseEntity<ScanResponse> createScan(@RequestBody ScanRequest request) {
        Scan scan;
        if ("AXE".equalsIgnoreCase(request.method())) {
            scan = scanService.runAxeScan(request.url());
        } else if ("AI".equalsIgnoreCase(request.method())) {
            AiModel model = parseModel(request.model());
            scan = scanService.runAiScan(request.url(), model);
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported method: " + request.method()
            );
        }

        ScanResponse response = toResponse(scan, List.of());
        return ResponseEntity.ok(response);
    }

    private AiModel parseModel(String model) {
        if (model == null || model.isBlank()) {
            return AiModel.GPT4O_MINI;
        }
        try {
            return AiModel.valueOf(model.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported model: " + model
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScanResponse> getScan(@PathVariable Long id) {
        Scan scan = scanService.findById(id);
        List<Finding> findings = scanService.findingsForScan(scan);
        ScanResponse response = toResponse(scan, findings);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{id}/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportScanCsv(@PathVariable Long id) {
        Scan scan = scanService.findById(id);
        List<Finding> findings = scanService.findingsForScan(scan);
        String csv = csvSerializer.serialize(scan, findings);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scan-" + id + ".csv\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv);
    }

    private ScanResponse toResponse(Scan scan, List<Finding> findings) {
        List<FindingResponse> findingResponses = findings.stream()
                .map(f -> new FindingResponse(
                        f.getSourceMethod(),
                        f.getWcagCriterion(),
                        f.getWcagLevel(),
                        f.getSeverity(),
                        f.getElementSelector(),
                        f.getDescription()
                ))
                .toList();

        java.time.Instant timestamp = scan.getTimestamp() != null
                ? scan.getTimestamp().atZone(java.time.ZoneOffset.UTC).toInstant()
                : null;

        double severityScore = severityScoringService.score(findings);
        ConformanceScores conformanceScores = conformanceScoringService.score(findings);

        return new ScanResponse(
                scan.getId(),
                scan.getUrl(),
                scan.getMethod(),
                timestamp,
                severityScore,
                conformanceScores,
                findingResponses
        );
    }
}
