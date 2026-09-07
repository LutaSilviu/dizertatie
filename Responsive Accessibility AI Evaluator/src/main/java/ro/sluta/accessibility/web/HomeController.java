package ro.sluta.accessibility.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ro.sluta.accessibility.browser.interaction.InteractionScenarioRegistry;
import ro.sluta.accessibility.domain.Viewport;
import ro.sluta.accessibility.config.AiAnalysisProperties;
import ro.sluta.accessibility.analysis.ai.AiCondition;
import ro.sluta.accessibility.analysis.ai.AiRunProfile;

@Controller
public class HomeController {
    private final InteractionScenarioRegistry scenarios;
    private final AiAnalysisProperties aiProperties;

    public HomeController(InteractionScenarioRegistry scenarios, AiAnalysisProperties aiProperties) {
        this.scenarios = scenarios;
        this.aiProperties = aiProperties;
    }

    @GetMapping("/")
    public String home(Model model) {
        prepare(model);
        return "home";
    }

    void prepare(Model model) {
        model.addAttribute("title", "Responsive Accessibility AI Evaluator");
        model.addAttribute("viewports", Viewport.values());
        model.addAttribute("interactionScenarios", scenarios.available());
        model.addAttribute("aiModels", aiProperties.models().entrySet().stream().filter(entry -> entry.getValue().enabled()).toList());
        model.addAttribute("aiConditions", new AiCondition[]{AiCondition.AI_TEXT, AiCondition.AI_MULTIMODAL});
        model.addAttribute("aiProfiles", AiRunProfile.values());
    }
}
