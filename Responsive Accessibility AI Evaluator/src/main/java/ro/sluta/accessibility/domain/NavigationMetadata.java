package ro.sluta.accessibility.domain;

import java.time.Instant;

public record NavigationMetadata(
        Instant startedAt,
        Instant domContentLoadedAt,
        Instant completedAt,
        long loadDurationMs,
        int redirectCount,
        Integer responseStatus,
        int resourceCount) {
}
