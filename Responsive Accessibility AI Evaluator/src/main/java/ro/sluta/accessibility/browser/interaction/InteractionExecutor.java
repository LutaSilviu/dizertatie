package ro.sluta.accessibility.browser.interaction;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.InteractionStepResult;

@Component
public class InteractionExecutor {
    private static final List<String> ALLOWED_KEYS = List.of("Tab", "Enter", "Escape");

    public List<InteractionStepResult> execute(Page page, InteractionScenario scenario, Duration timeout) {
        List<InteractionStepResult> results = new ArrayList<>();
        for (int index = 0; index < scenario.steps().size(); index++) {
            InteractionStep step = scenario.steps().get(index);
            long started = System.nanoTime();
            try {
                executeStep(page, step, timeout.toMillis());
                results.add(new InteractionStepResult(index + 1, step.action().name(), step.testId(), true,
                        elapsedMillis(started), "OK"));
            } catch (PlaywrightException | IllegalArgumentException exception) {
                results.add(new InteractionStepResult(index + 1, step.action().name(), step.testId(), false,
                        elapsedMillis(started), concise(exception.getMessage())));
                break;
            }
        }
        return List.copyOf(results);
    }

    private void executeStep(Page page, InteractionStep step, double timeout) {
        Locator locator = page.getByTestId(step.testId());
        switch (step.action()) {
            case CLICK -> locator.click(new Locator.ClickOptions().setTimeout(timeout));
            case FOCUS -> locator.focus(new Locator.FocusOptions().setTimeout(timeout));
            case PRESS_KEY -> {
                if (!ALLOWED_KEYS.contains(step.value())) {
                    throw new IllegalArgumentException("Tasta nu este permisă în scenariile F2.");
                }
                locator.press(step.value(), new Locator.PressOptions().setTimeout(timeout));
            }
            case FILL -> locator.fill(step.value(), new Locator.FillOptions().setTimeout(timeout));
            case SELECT_OPTION -> locator.selectOption(step.value(),
                    new Locator.SelectOptionOptions().setTimeout(timeout));
            case WAIT_FOR, ASSERT_VISIBLE -> locator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(timeout));
        }
    }

    private long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000; }

    private String concise(String message) {
        if (message == null) return "Pasul de interacțiune a eșuat.";
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
