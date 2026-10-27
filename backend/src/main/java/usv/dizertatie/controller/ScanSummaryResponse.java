package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Summary of a single scan in the history list")
public record ScanSummaryResponse(
        Long id,
        String url,
        String method,
        String model,
        Instant timestamp,
        long findingCount
) {}
