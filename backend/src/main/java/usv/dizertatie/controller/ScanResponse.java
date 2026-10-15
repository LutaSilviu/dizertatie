package usv.dizertatie.controller;

import java.time.LocalDateTime;

public record ScanResponse(
        Long id,
        String url,
        String method,
        String model,
        LocalDateTime timestamp,
        Double severityScore,
        long findingCount
) {}
