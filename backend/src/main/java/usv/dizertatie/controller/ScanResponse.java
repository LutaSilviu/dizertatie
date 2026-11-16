package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import usv.dizertatie.axe.ManualReviewItem;
import usv.dizertatie.domain.ScanStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Scan outcome, with evidence only when the scan completed")
public record ScanResponse(
        Long id,
        String url,
        @Schema(description = "Scan method used: AXE, AI or COMBINED", example = "AXE")
        String method,
        @Schema(description = "AI model used (null for AXE scans)", example = "gpt-6-luna")
        String model,
        @Schema(description = "axe-core engine version that produced the scan (null for AI scans)", example = "4.14.0")
        String axeVersion,
        @Schema(description = "Browser version the page was rendered in (null for AI scans)")
        String browserVersion,
        @Schema(description = "Timestamp when the scan was completed (ISO-8601 UTC)")
        Instant timestamp,
        ScanStatus status,
        String failureReason,
        @Schema(description = "Reported elements per impact, across every finding of the scan (axe and AI)")
        Map<String, Long> affectedElementsByImpact,
        @Schema(description = "Reported elements per source method: AXE, AI or COMBINED (reported by both)")
        Map<String, Long> findingsBySource,
        @Schema(description = "Distinct axe-core rules with findings; AI findings carry no rule id")
        Integer distinctRuleCount,
        List<String> wcagCriteriaWithFindings,
        List<FindingResponse> findings,
        List<ManualReviewItem> manualReviewItems
) {
}
