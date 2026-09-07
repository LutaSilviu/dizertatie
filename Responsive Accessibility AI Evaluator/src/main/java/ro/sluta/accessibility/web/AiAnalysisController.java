package ro.sluta.accessibility.web;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ro.sluta.accessibility.analysis.ai.*;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.application.CapturePageSnapshotUseCase;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.Viewport;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;
import ro.sluta.accessibility.reporting.NormalizedReportAssembler;

@Controller
public class AiAnalysisController {
    private final CapturePageSnapshotUseCase capture;
    private final RunAiAnalysisUseCase ai;
    private final HomeController home;
    private final FindingNormalizerV1 normalizer;
    private final NormalizedReportAssembler reports;

    public AiAnalysisController(CapturePageSnapshotUseCase capture, RunAiAnalysisUseCase ai, HomeController home,
            FindingNormalizerV1 normalizer, NormalizedReportAssembler reports) {
        this.capture = capture; this.ai = ai; this.home = home; this.normalizer = normalizer; this.reports = reports;
    }

    @PostMapping("/analyze-ai")
    public String analyze(@RequestParam String url, @RequestParam Viewport viewport,
            @RequestParam(defaultValue = "none") String interactionScenarioId,
            @RequestParam String modelId, @RequestParam AiCondition aiCondition,
            @RequestParam(defaultValue = "APP_DEFAULT_V1") AiRunProfile aiProfile,
            @RequestParam BigDecimal maximumCostUsd,
            @RequestParam(defaultValue = "false") boolean paidActionConfirmed, Model model) {
        UUID runId = UUID.randomUUID();
        PageSnapshot snapshot = capture.capture(new CapturePageSnapshotCommand(runId, url, viewport, interactionScenarioId));
        AiAnalysisResult result = ai.run(new RunAiAnalysisCommand(runId, snapshot, modelId, aiCondition,
                aiProfile, maximumCostUsd, paidActionConfirmed));
        model.addAttribute("snapshot", snapshot); model.addAttribute("aiResult", result);
        var findings = normalizer.normalizeAi(result, viewport);
        model.addAttribute("normalizedReport", reports.forAi(snapshot, result, findings));
        model.addAttribute("url", url); model.addAttribute("selectedViewport", viewport);
        model.addAttribute("selectedScenario", interactionScenarioId); model.addAttribute("selectedAiModel", modelId);
        model.addAttribute("selectedAiCondition", aiCondition); home.prepare(model);
        return "home";
    }
}
