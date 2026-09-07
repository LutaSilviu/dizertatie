package ro.sluta.accessibility.web;

import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ro.sluta.accessibility.analysis.axe.AxeAnalysisExecution;
import ro.sluta.accessibility.analysis.axe.RunAxeAnalysisCommand;
import ro.sluta.accessibility.analysis.axe.RunAxeAnalysisUseCase;
import ro.sluta.accessibility.domain.Viewport;
import ro.sluta.accessibility.normalization.FindingNormalizerV1;
import ro.sluta.accessibility.reporting.NormalizedReportAssembler;

@Controller
public class AxeAnalysisController {
    private final RunAxeAnalysisUseCase useCase;
    private final HomeController homeController;
    private final FindingNormalizerV1 normalizer;
    private final NormalizedReportAssembler reports;

    public AxeAnalysisController(RunAxeAnalysisUseCase useCase, HomeController homeController,
            FindingNormalizerV1 normalizer, NormalizedReportAssembler reports) {
        this.useCase = useCase;
        this.homeController = homeController;
        this.normalizer = normalizer;
        this.reports = reports;
    }

    @PostMapping("/analyze-axe")
    public String analyze(@RequestParam String url,
                          @RequestParam Viewport viewport,
                          @RequestParam(defaultValue = "none") String interactionScenarioId,
                          Model model) {
        AxeAnalysisExecution execution = useCase.analyze(new RunAxeAnalysisCommand(
                UUID.randomUUID(), UUID.randomUUID(), url, viewport, interactionScenarioId));
        model.addAttribute("snapshot", execution.snapshot());
        model.addAttribute("axeResult", execution.axeResult());
        var findings = normalizer.normalizeAxe(execution.axeResult());
        model.addAttribute("normalizedReport", reports.forAxe(execution.snapshot(), execution.axeResult(), findings));
        model.addAttribute("url", url);
        model.addAttribute("selectedViewport", viewport);
        model.addAttribute("selectedScenario", interactionScenarioId);
        homeController.prepare(model);
        return "home";
    }
}
