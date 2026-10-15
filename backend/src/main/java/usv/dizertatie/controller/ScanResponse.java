package usv.dizertatie.controller;

import usv.dizertatie.scoring.ConformanceScores;

import java.time.Instant;
import java.util.List;

public record ScanResponse(
        Long id,
        String url,
        String method,
        Instant timestamp,
        double severityScore,
        ConformanceScores conformanceScores,
        List<FindingResponse> findings
) {
}
