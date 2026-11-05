package usv.dizertatie.controller;

import java.util.List;

/**
 * One page of scan history.
 *
 * @param page          zero-based page index
 * @param totalPages    number of pages available at this page size (at least 1)
 * @param number        same as {@code page}; Spring Data's name for it, which clients may expect
 */
public record ScanHistoryResponse(
        List<ScanSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int number
) {
    public ScanHistoryResponse(List<ScanSummaryResponse> content, int page, int size, long totalElements) {
        this(content, page, size, totalElements,
                size > 0 ? (int) Math.max(1, (totalElements + size - 1) / size) : 1,
                page);
    }
}
