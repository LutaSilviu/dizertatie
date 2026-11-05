package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for triggering an accessibility scan")
public record ScanRequest(
        @Schema(description = "The URL to scan for accessibility issues", example = "https://example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(description = "Scan method: AXE (rule-based), AI (LLM-based) or COMBINED (both, merged)", example = "AXE", allowableValues = {"AXE", "AI", "COMBINED"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String method,
        @Schema(description = "AI model to use when method=AI or COMBINED (e.g. gpt-6-luna). Defaults to GPT-6 Luna. Ignored for AXE.", example = "gpt-6-luna")
        String model
) {
}
