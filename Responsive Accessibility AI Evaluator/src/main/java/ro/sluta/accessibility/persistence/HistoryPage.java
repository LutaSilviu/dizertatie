package ro.sluta.accessibility.persistence;

import java.util.List;
public record HistoryPage(List<AnalysisRecord> items, long total, int page, int size) {
    public HistoryPage { items = List.copyOf(items); }
}
