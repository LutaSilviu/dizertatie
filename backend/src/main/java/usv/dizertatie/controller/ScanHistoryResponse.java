package usv.dizertatie.controller;

import java.util.List;

public record ScanHistoryResponse(
        List<ScanSummaryResponse> content,
        int page,
        int size,
        long totalElements
) {}
