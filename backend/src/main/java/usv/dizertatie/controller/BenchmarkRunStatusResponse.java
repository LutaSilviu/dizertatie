package usv.dizertatie.controller;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code GET /benchmark-runs/{id}/status}.
 *
 * <p>The average latency and cost fields are populated only when {@code status=COMPLETE}
 * and at least one page has been successfully processed; otherwise they are {@code null}.
 */
public record BenchmarkRunStatusResponse(
        Long id,
        String name,
        String status,
        int pagesDone,
        int totalPages,
        /** Creation time, ISO-8601 UTC. */
        Instant createdAt,

        /** Average wall-clock latency of AXE-only scans, in milliseconds. Null if not yet complete. */
        Double avgLatencyAxeMs,

        /** Average wall-clock latency of AI-only scans, in milliseconds. Null if not yet complete. */
        Double avgLatencyAiMs,

        /** Average wall-clock latency of COMBINED (AXE + AI) scans, in milliseconds. Null if not yet complete. */
        Double avgLatencyCombinedMs,

        /** Average estimated OpenAI cost per page in USD. Null if not yet complete. */
        Double avgCostPerPageUsd,

        /** Scan methods requested for this run, in canonical order (AXE, AI, COMBINED). */
        List<String> methods,

        /** Human-readable summary of failed scans, e.g. "AI failed on 2/2 pages: ..."; null when none failed. */
        String error
) {
}
