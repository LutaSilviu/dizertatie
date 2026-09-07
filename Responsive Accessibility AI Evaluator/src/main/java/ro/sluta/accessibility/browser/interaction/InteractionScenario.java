package ro.sluta.accessibility.browser.interaction;

import java.util.List;

public record InteractionScenario(String id, String version, String displayName, List<InteractionStep> steps) {
    public static final InteractionScenario NONE = new InteractionScenario("none", "1", "Fără interacțiuni", List.of());

    public InteractionScenario {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
