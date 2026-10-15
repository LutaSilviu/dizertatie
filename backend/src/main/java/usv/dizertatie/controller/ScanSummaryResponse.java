package usv.dizertatie.controller;

import java.time.Instant;

public record ScanSummaryResponse(
        Long id,
        String url,
        String method,
        String model,
        Instant timestamp,
        Double severityScore,
        long findingCount
) {}
