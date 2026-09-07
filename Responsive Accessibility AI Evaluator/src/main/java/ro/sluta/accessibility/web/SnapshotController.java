package ro.sluta.accessibility.web;

import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ro.sluta.accessibility.application.CapturePageSnapshotCommand;
import ro.sluta.accessibility.application.CapturePageSnapshotUseCase;
import ro.sluta.accessibility.domain.PageSnapshot;
import ro.sluta.accessibility.domain.Viewport;

@Controller
public class SnapshotController {
    private final CapturePageSnapshotUseCase captureUseCase;
    private final HomeController homeController;

    public SnapshotController(CapturePageSnapshotUseCase captureUseCase, HomeController homeController) {
        this.captureUseCase = captureUseCase;
        this.homeController = homeController;
    }

    @PostMapping("/capture-snapshot")
    public String capture(@RequestParam String url,
                          @RequestParam Viewport viewport,
                          @RequestParam(defaultValue = "none") String interactionScenarioId,
                          Model model) {
        PageSnapshot snapshot = captureUseCase.capture(new CapturePageSnapshotCommand(
                UUID.randomUUID(), url, viewport, interactionScenarioId));
        model.addAttribute("snapshot", snapshot);
        model.addAttribute("url", url);
        model.addAttribute("selectedViewport", viewport);
        model.addAttribute("selectedScenario", interactionScenarioId);
        homeController.prepare(model);
        return "home";
    }
}
