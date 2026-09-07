package ro.sluta.accessibility.browser.interaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InteractionScenarioRegistry {
    private final Map<String, InteractionScenario> scenarios;

    public InteractionScenarioRegistry() {
        Map<String, InteractionScenario> configured = new LinkedHashMap<>();
        configured.put("fixture-reveal-v1", new InteractionScenario(
                "fixture-reveal-v1", "1", "Fixture: afișează panoul",
                List.of(
                        new InteractionStep(InteractionAction.CLICK, "reveal", null),
                        new InteractionStep(InteractionAction.ASSERT_VISIBLE, "revealed-panel", null))));
        configured.put("fixture-form-v1", new InteractionScenario(
                "fixture-form-v1", "1", "Fixture: completează formularul",
                List.of(
                        new InteractionStep(InteractionAction.FILL, "name", "Evaluator"),
                        new InteractionStep(InteractionAction.SELECT_OPTION, "category", "accessibility"),
                        new InteractionStep(InteractionAction.FOCUS, "submit", null),
                        new InteractionStep(InteractionAction.PRESS_KEY, "submit", "Enter"),
                        new InteractionStep(InteractionAction.WAIT_FOR, "submitted", null))));
        scenarios = Map.copyOf(configured);
    }

    public InteractionScenario resolve(String id, int maxSteps) {
        if (id == null || id.isBlank() || id.equals("none")) return InteractionScenario.NONE;
        InteractionScenario scenario = scenarios.get(id);
        if (scenario == null) throw new IllegalArgumentException("Scenariul de interacțiune nu este aprobat.");
        if (scenario.steps().size() > maxSteps) {
            throw new IllegalArgumentException("Scenariul depășește limita de pași aprobată.");
        }
        return scenario;
    }

    public List<InteractionScenario> available() { return List.copyOf(scenarios.values()); }
}
