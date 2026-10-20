package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Summary of a single scan in the history list")
public record ScanSummaryResponse(
        @Schema(description = "Unique scan identifier", example = "42")
        Long id,
        @Schema(description = "The URL that was scanned", example = "https://example.com")
        String url,
        @Schema(description = "Scan method: AXE or AI", example = "AXE")
        String method,
        @Schema(description = "AI model used (null for AXE scans)", example = "gpt-6-luna")
        String model,
        @Schema(description = "Timestamp when the scan was completed (ISO-8601 UTC)")
        Instant timestamp,
        @Schema(description = "Aggregate severity score (0–100, higher is better)", example = "76.3")
        Double severityScore,
        @Schema(description = "Total number of accessibility findings", example = "12")
        long findingCount
) {}
