package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import usv.dizertatie.ai.AiModel;
import usv.dizertatie.axe.AxeResultParser;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;
import usv.dizertatie.domain.ScanStatus;
import usv.dizertatie.export.CsvSerializer;
import usv.dizertatie.service.ScanService;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@RestController
@RequestMapping("/scans")
@RequiredArgsConstructor
@Tag(name = "Scans", description = "Accessibility scan management endpoints")
public class ScanController {

    private final ScanService scanService;
    private final AxeResultParser axeResultParser;
    private final CsvSerializer csvSerializer;

    @Operation(
            summary = "List all scans (paginated)",
            description = "Returns a paginated list of accessibility scan summaries ordered by most recent first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of scan summaries",
                    content = @Content(schema = @Schema(implementation = ScanHistoryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content)
    })
    @GetMapping
    public ScanHistoryResponse getScans(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max results per page)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return scanService.findAll(page, size);
    }

    @Operation(
            summary = "Trigger a new accessibility scan",
            description = "Runs an accessibility scan on the given URL using either the AXE rule engine or an AI model. " +
                    "The attempt runs synchronously and returns either completed evidence or a persisted failure status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan completed successfully",
                    content = @Content(schema = @Schema(implementation = ScanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported method or model", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ScanResponse> createScan(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Scan configuration specifying the URL and method",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ScanRequest.class))
            )
            @RequestBody ScanRequest request) {
        Scan scan;
        if ("AXE".equalsIgnoreCase(request.method())) {
            scan = scanService.runAxeScan(request.url());
        } else if ("AI".equalsIgnoreCase(request.method())) {
            AiModel model = parseModel(request.model());
            scan = scanService.runAiScan(request.url(), model);
        } else if ("COMBINED".equalsIgnoreCase(request.method())) {
            AiModel model = parseModel(request.model());
            scan = scanService.runCombinedScan(request.url(), model);
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
        try {
            return AiModel.fromId(model);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported model: " + model
            );
        }
    }

    @Operation(
            summary = "Get a scan by ID",
            description = "Returns the scan outcome and, for completed scans, all accessibility findings."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan found",
                    content = @Content(schema = @Schema(implementation = ScanResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan not found", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ScanResponse> getScan(
            @Parameter(description = "Scan ID", example = "42", required = true)
            @PathVariable Long id) {
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
                        f.getRuleId(),
                        f.getSeverity(),
                        f.getElementSelector(),
                        f.getDescription()
                ))
                .toList();

        java.time.Instant timestamp = scan.getTimestamp() != null
                ? scan.getTimestamp().atZone(java.time.ZoneOffset.UTC).toInstant()
                : null;

        List<Finding> axeFindings = findings.stream()
                .filter(f -> "AXE".equalsIgnoreCase(f.getSourceMethod())
                        || "COMBINED".equalsIgnoreCase(f.getSourceMethod()))
                .toList();
        Map<String, Long> affectedElementsByImpact = new TreeMap<>();
        axeFindings.forEach(f -> affectedElementsByImpact.merge(
                f.getSeverity() == null ? "unknown" : f.getSeverity().toLowerCase(), 1L, Long::sum));
        int distinctRuleCount = (int) axeFindings.stream()
                .map(Finding::getRuleId)
                .filter(ruleId -> ruleId != null && !ruleId.isBlank())
                .distinct()
                .count();
        List<String> wcagCriteria = findings.stream()
                .map(Finding::getWcagCriterion)
                .filter(criterion -> criterion != null && !criterion.isBlank())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new))
                .stream().toList();
        List<usv.dizertatie.axe.ManualReviewItem> manualReviewItems = axeResultParser.parseIncomplete(
                scan.getRawAxeIncomplete() == null ? "[]" : scan.getRawAxeIncomplete());

        ScanStatus status = scan.getStatus() == null ? ScanStatus.COMPLETED : scan.getStatus();
        if (status != ScanStatus.COMPLETED) {
            return new ScanResponse(
                    scan.getId(), scan.getUrl(), scan.getMethod(), timestamp,
                    status, scan.getFailureReason(), null, null, null, List.of(), List.of());
        }

        return new ScanResponse(
                scan.getId(),
                scan.getUrl(),
                scan.getMethod(),
                timestamp,
                status,
                null,
                affectedElementsByImpact,
                distinctRuleCount,
                wcagCriteria,
                findingResponses,
                manualReviewItems
        );
    }
}
