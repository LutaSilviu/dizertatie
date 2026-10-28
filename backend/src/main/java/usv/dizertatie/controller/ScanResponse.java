package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import usv.dizertatie.axe.ManualReviewItem;
import usv.dizertatie.domain.ScanStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Scan outcome, with evidence only when the scan completed")
public record ScanResponse(
        Long id,
        String url,
        String method,
        Instant timestamp,
        ScanStatus status,
        String failureReason,
        Map<String, Long> affectedElementsByImpact,
        Integer distinctRuleCount,
        List<String> wcagCriteriaWithFindings,
        List<FindingResponse> findings,
        List<ManualReviewItem> manualReviewItems
) {
}
