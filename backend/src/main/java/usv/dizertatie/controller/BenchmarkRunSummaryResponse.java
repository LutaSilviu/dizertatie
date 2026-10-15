package usv.dizertatie.controller;

import java.time.LocalDateTime;

/**
 * Summary item returned in the {@code GET /benchmark-runs} list.
 */
public record BenchmarkRunSummaryResponse(
        Long id,
        String name,
        String status,
        int pagesDone,
        int totalPages,
        LocalDateTime createdAt
) {
}
