package ro.sluta.accessibility.web;

import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.sluta.accessibility.analysis.ai.AiCostCalculator;
import ro.sluta.accessibility.config.AiAnalysisProperties;

@RestController
@RequestMapping("/api/v1/analyses/estimate")
public class AiEstimateController {
    private final AiAnalysisProperties properties;
    private final AiCostCalculator calculator;

    public AiEstimateController(AiAnalysisProperties properties, AiCostCalculator calculator) {
        this.properties = properties;
        this.calculator = calculator;
    }

    @GetMapping
    public ResponseEntity<EstimateResponse> estimate(@RequestParam String modelId,
            @RequestParam(defaultValue = "150000") int inputCharacters,
            @RequestParam(defaultValue = "1") int plannedCalls) {
        var model = properties.models().get(modelId);
        if (model == null || !model.enabled() || inputCharacters < 0 || plannedCalls < 1)
            return ResponseEntity.badRequest().build();
        BigDecimal perCall = calculator.estimate(inputCharacters, model, properties.maxOutputTokens()).amountUsd();
        return ResponseEntity.ok(new EstimateResponse(modelId, model.providerModel(), plannedCalls,
                perCall, perCall.multiply(BigDecimal.valueOf(plannedCalls)), true, "USD", model.effectiveFrom().toString()));
    }

    public record EstimateResponse(String modelId, String providerModel, int plannedCalls, BigDecimal estimatedPerCall,
                                   BigDecimal estimatedTotal, boolean includesSafetyMargin, String currency,
                                   String pricesEffectiveFrom) { }
}
