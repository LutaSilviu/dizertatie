package usv.dizertatie.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import usv.dizertatie.axe.ManualReviewItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Completed scan evidence and accessibility findings")
public record ScanResponse(
        Long id,
        String url,
        String method,
        Instant timestamp,
        Map<String, Long> affectedElementsByImpact,
        int distinctRuleCount,
        List<String> wcagCriteriaWithFindings,
        List<FindingResponse> findings,
        List<ManualReviewItem> manualReviewItems
) {
}
