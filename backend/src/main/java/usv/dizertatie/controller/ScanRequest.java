package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for triggering an accessibility scan")
public record ScanRequest(
        @Schema(description = "The URL to scan for accessibility issues", example = "https://example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(description = "Scan method: AXE (rule-based) or AI (LLM-based)", example = "AXE", allowableValues = {"AXE", "AI"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String method,
        @Schema(description = "AI model to use when method=AI (e.g. GPT4O_MINI, GPT4O). Ignored for AXE.", example = "GPT4O_MINI")
        String model
) {
}
