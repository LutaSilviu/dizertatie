package ro.sluta.accessibility.persistence;

import java.time.Instant;
import ro.sluta.accessibility.domain.AnalysisMethod;
import ro.sluta.accessibility.domain.AnalysisStatus;

public record HistoryFilter(Instant from, Instant to, String urlContains, AnalysisMethod method, String modelId,
                            AnalysisStatus status, int page, int size, boolean ascending) {
    public HistoryFilter {
        page = Math.max(0, page); size = Math.max(1, Math.min(100, size));
    }
}
