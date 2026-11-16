package usv.dizertatie.dataset;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.wcag.WcagCriterionRegistry;
import usv.dizertatie.wcag.WcagLevel;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks every seeded ground-truth label against the W3C BAD fixture it describes: the page must
 * exist on the classpath and the label's selector must identify exactly one element in it.
 * A label that points at nothing can never be matched, so it silently turns into a false negative
 * for every scan method and makes benchmark precision/recall meaningless.
 */
class DatasetSeederFixtureTest {

    private static final String FIXTURE_ROOT = "static/fixtures/wai-bad/";

    private static Playwright playwright;
    private static Browser browser;
    private static Page page;
    private static String loadedUrl;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    static List<GroundTruthLabel> seeds() {
        return DatasetSeeder.buildSeeds();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("seeds")
    void labelSelectorIdentifiesExactlyOneElementOnItsFixturePage(GroundTruthLabel label) {
        URL fixture = fixtureFor(label.getUrl());
        assertThat(fixture)
                .as("fixture for %s must exist on the classpath", label.getUrl())
                .isNotNull();

        if (!fixture.toString().equals(loadedUrl)) {
            page.navigate(fixture.toString());
            loadedUrl = fixture.toString();
        }

        Object matches = page.evaluate(
                "s => { try { return document.querySelectorAll(s).length; } catch (e) { return -1; } }",
                label.getElementSelector());
        assertThat(((Number) matches).intValue())
                .as("elements matching selector %s on %s", label.getElementSelector(), label.getUrl())
                .isEqualTo(1);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("seeds")
    void labelLevelMatchesTheWcagCriterion(GroundTruthLabel label) throws Exception {
        WcagCriterionRegistry registry = new WcagCriterionRegistry();
        registry.load();

        assertThat(registry.getLevel(label.getWcagCriterion()))
                .as("WCAG level of %s", label.getWcagCriterion())
                .contains(WcagLevel.valueOf(label.getWcagLevel()));
        assertThat(label.getSeverity()).isIn("critical", "serious", "moderate", "minor");
    }

    @Test
    void negativeExamplePageIsNotModelledAsAnExpectedViolation() {
        String negativePage = DatasetSeeder.BAD_DEMO + "after/home.html";

        assertThat(seeds())
                .as("labels are expected violations; the accessible after/home.html must have none")
                .noneMatch(l -> l.getUrl().equals(negativePage));
        assertThat(DatasetSeeder.NEGATIVE_EXAMPLE_URLS)
                .as("unlabelled pages only reach the benchmark through the negative-example list")
                .contains(negativePage);
        assertThat(fixtureFor(negativePage)).isNotNull();
    }

    @Test
    void labelsAreUniquePerUrlCriterionAndSelector() {
        Set<String> keys = new HashSet<>();
        for (GroundTruthLabel l : seeds()) {
            assertThat(keys.add(l.getUrl() + "|" + l.getWcagCriterion() + "|" + l.getElementSelector()))
                    .as("duplicate label %s %s %s", l.getUrl(), l.getWcagCriterion(), l.getElementSelector())
                    .isTrue();
        }
    }

    private static URL fixtureFor(String url) {
        assertThat(url).startsWith(DatasetSeeder.BAD_DEMO);
        String relative = url.substring(DatasetSeeder.BAD_DEMO.length());
        return DatasetSeederFixtureTest.class.getClassLoader().getResource(FIXTURE_ROOT + relative);
    }
}
