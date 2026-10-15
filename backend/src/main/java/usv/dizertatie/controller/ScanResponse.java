package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Full scan result including all accessibility findings")
public record ScanResponse(
        @Schema(description = "Unique scan identifier", example = "42")
        Long id,
        @Schema(description = "The URL that was scanned", example = "https://example.com")
        String url,
        @Schema(description = "Scan method used: AXE or AI", example = "AXE")
        String method,
        @Schema(description = "Timestamp when the scan was completed (ISO-8601 UTC)")
        Instant timestamp,
        @Schema(description = "List of accessibility findings discovered during the scan")
        List<FindingResponse> findings
) {
}
