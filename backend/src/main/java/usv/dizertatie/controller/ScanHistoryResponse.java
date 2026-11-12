package usv.dizertatie.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScanHistoryResponse(
        List<ScanSummaryResponse> content,
        int page,
        int size,
        long totalElements
) {
    @JsonProperty("number")
    public int number() {
        return page;
    }

    @JsonProperty("totalPages")
    public int totalPages() {
        return size > 0 ? (int) Math.min(Integer.MAX_VALUE, Math.ceil((double) totalElements / size)) : 0;
    }
}
