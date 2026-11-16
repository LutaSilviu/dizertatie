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
import org.springframework.http.HttpStatus;
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

    private static final int MAX_PAGE_SIZE = 100;

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
        if (page < 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
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
            @ApiResponse(responseCode = "400", description = "Invalid URL, unsupported method or model", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content),
            @ApiResponse(responseCode = "502", description = "Target site blocked or unreachable, or the AI call failed", content = @Content),
            @ApiResponse(responseCode = "503", description = "OpenAI API key missing or rejected", content = @Content),
            @ApiResponse(responseCode = "504", description = "Target page did not load in time", content = @Content)
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
            scan = scanService.runAxeScan(TargetUrls.normalize(request.url()));
        } else if ("AI".equalsIgnoreCase(request.method())) {
            AiModel model = parseModel(request.model());
            scan = scanService.runAiScan(TargetUrls.normalize(request.url()), model);
        } else if ("COMBINED".equalsIgnoreCase(request.method())) {
            AiModel model = parseModel(request.model());
            scan = scanService.runCombinedScan(TargetUrls.normalize(request.url()), model);
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported method: " + request.method() + " (expected AXE, AI or COMBINED)"
            );
        }

        // Respond with the persisted findings so the response matches what GET /scans/{id} reports.
        ScanResponse response = toResponse(scan, scanService.findingsForScan(scan));
        return ResponseEntity.ok(response);
    }

    private AiModel parseModel(String model) {
        try {
            return AiModel.fromId(model);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported model: " + model + " (expected gpt-6-luna, gpt-4o or gpt-4o-mini)"
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

        // Impact and source totals cover every finding, so the summary describes the same set as
        // the findings table. Only axe findings carry a rule id, so the rule count is axe-only.
        Map<String, Long> affectedElementsByImpact = new TreeMap<>();
        Map<String, Long> findingsBySource = new TreeMap<>();
        findings.forEach(f -> {
            affectedElementsByImpact.merge(
                    f.getSeverity() == null ? "unknown" : f.getSeverity().toLowerCase(java.util.Locale.ROOT),
                    1L, Long::sum);
            findingsBySource.merge(
                    f.getSourceMethod() == null ? "UNKNOWN" : f.getSourceMethod().toUpperCase(java.util.Locale.ROOT),
                    1L, Long::sum);
        });
        int distinctRuleCount = (int) findings.stream()
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
                    scan.getId(), scan.getUrl(), scan.getMethod(), scan.getModelId(),
                    scan.getAxeVersion(), scan.getBrowserVersion(), timestamp,
                    status, scan.getFailureReason(), null, null, null, null, List.of(), List.of());
        }

        return new ScanResponse(
                scan.getId(),
                scan.getUrl(),
                scan.getMethod(),
                scan.getModelId(),
                scan.getAxeVersion(),
                scan.getBrowserVersion(),
                timestamp,
                status,
                null,
                affectedElementsByImpact,
                findingsBySource,
                distinctRuleCount,
                wcagCriteria,
                findingResponses,
                manualReviewItems
        );
    }
}
