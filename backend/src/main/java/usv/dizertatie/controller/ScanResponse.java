package usv.dizertatie.controller;

import java.time.Instant;
import java.util.List;

public record ScanResponse(
        Long id,
        String url,
        String method,
        Instant timestamp,
        List<FindingResponse> findings
) {
}
