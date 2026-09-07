package ro.sluta.accessibility.browser.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class InteractionScenarioRegistryTest {
    private final InteractionScenarioRegistry registry = new InteractionScenarioRegistry();

    @Test
    void exposesOnlyVersionedInternalScenariosWithApprovedActions() {
        assertThat(registry.available()).isNotEmpty();
        assertThat(registry.available()).allSatisfy(scenario -> {
            assertThat(scenario.id()).isNotBlank();
            assertThat(scenario.version()).isNotBlank();
            assertThat(scenario.steps()).hasSizeLessThanOrEqualTo(20);
            assertThat(scenario.steps()).allSatisfy(step ->
                    assertThat(EnumSet.allOf(InteractionAction.class)).contains(step.action()));
        });
    }

    @Test
    void rejectsUnknownScenarioInsteadOfAcceptingSelectorsOrScripts() {
        assertThatThrownBy(() -> registry.resolve("document.querySelector('body').remove()", 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInternalScenarioThatExceedsTheConfiguredStepLimit() {
        assertThatThrownBy(() -> registry.resolve("fixture-reveal-v1", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limita de pași");
    }
}
