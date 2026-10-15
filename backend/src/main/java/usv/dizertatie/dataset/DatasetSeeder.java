package usv.dizertatie.dataset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatasetSeeder implements ApplicationRunner {

    private static final String ANNOTATOR = "w3c-bad-demo";
    private static final double CONFIDENCE = 0.9;

    private final GroundTruthLabelRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<GroundTruthLabel> seeds = buildSeeds();
        int inserted = 0;
        for (GroundTruthLabel label : seeds) {
            List<GroundTruthLabel> existing = repository.findByUrl(label.getUrl());
            boolean alreadyExists = existing.stream()
                    .anyMatch(e -> e.getWcagCriterion().equals(label.getWcagCriterion())
                            && stringEquals(e.getElementSelector(), label.getElementSelector()));
            if (!alreadyExists) {
                repository.save(label);
                inserted++;
            }
        }
        log.info("DatasetSeeder: inserted {} new ground-truth labels ({} total seeds defined)", inserted, seeds.size());
    }

    private static boolean stringEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private List<GroundTruthLabel> buildSeeds() {
        return List.of(

                // ── W3C BAD before/home.html ─────────────────────────────────────────────

                // 1.1.1 – Missing alt text on images
                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "1.1.1", "A", "critical",
                        "img[src='../img/w3c_home_nb.png']"),

                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "1.1.1", "A", "critical",
                        "img[src='../img/logo.gif']"),

                // 1.4.3 – Insufficient colour contrast
                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "1.4.3", "AA", "serious",
                        "p.light-text"),

                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "1.4.3", "AA", "serious",
                        "a.nav-link"),

                // 1.3.1 – Heading structure / info and relationships
                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "1.3.1", "A", "serious",
                        "font[size='+2']"),

                // 4.1.2 – Broken ARIA roles / name, role, value
                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "4.1.2", "A", "serious",
                        "div[onclick]"),

                // 2.4.1 – Bypass blocks
                label("https://www.w3.org/WAI/demos/bad/before/home.html",
                        "2.4.1", "A", "moderate",
                        "body"),

                // ── W3C BAD after/home.html (accessible version – negative examples) ───

                // 1.1.1 – alt text present on accessible version (included for contrast)
                label("https://www.w3.org/WAI/demos/bad/after/home.html",
                        "1.1.1", "A", "none",
                        "img[alt]"),

                // ── W3C BAD before/survey.html ───────────────────────────────────────────

                // 2.4.6 – Labels for form fields
                label("https://www.w3.org/WAI/demos/bad/before/survey.html",
                        "2.4.6", "AA", "serious",
                        "input[type='text']:not([id])"),

                label("https://www.w3.org/WAI/demos/bad/before/survey.html",
                        "2.4.6", "AA", "serious",
                        "select:not([id])"),

                // 1.3.1 – Missing fieldset/legend grouping
                label("https://www.w3.org/WAI/demos/bad/before/survey.html",
                        "1.3.1", "A", "serious",
                        "form > input[type='radio']"),

                // 4.1.2 – Input with no accessible name
                label("https://www.w3.org/WAI/demos/bad/before/survey.html",
                        "4.1.2", "A", "critical",
                        "input[type='submit']:not([value])"),

                // 1.4.3 – Low-contrast label text
                label("https://www.w3.org/WAI/demos/bad/before/survey.html",
                        "1.4.3", "AA", "serious",
                        "label.hint-text"),

                // ── W3C BAD before/ticket.html ───────────────────────────────────────────

                // 2.4.6 – Unlabelled form controls on ticket page
                label("https://www.w3.org/WAI/demos/bad/before/ticket.html",
                        "2.4.6", "AA", "serious",
                        "input[type='text']:not([aria-label])"),

                // 1.1.1 – Decorative images without empty alt
                label("https://www.w3.org/WAI/demos/bad/before/ticket.html",
                        "1.1.1", "A", "moderate",
                        "img.decorative:not([alt=''])"),

                // 1.3.1 – Table used for layout without role="presentation"
                label("https://www.w3.org/WAI/demos/bad/before/ticket.html",
                        "1.3.1", "A", "serious",
                        "table:not([role='presentation'])"),

                // 2.1.1 – Keyboard: click handler on non-interactive element
                label("https://www.w3.org/WAI/demos/bad/before/ticket.html",
                        "2.1.1", "A", "critical",
                        "div.clickable[onclick]"),

                // ── W3C BAD before/news.html ─────────────────────────────────────────────

                // 1.3.3 – Sensory characteristics
                label("https://www.w3.org/WAI/demos/bad/before/news.html",
                        "1.3.3", "A", "moderate",
                        "p.instruction"),

                // 1.4.3 – Low-contrast text in news cards
                label("https://www.w3.org/WAI/demos/bad/before/news.html",
                        "1.4.3", "AA", "serious",
                        "span.date"),

                // 4.1.2 – Custom widget with no ARIA
                label("https://www.w3.org/WAI/demos/bad/before/news.html",
                        "4.1.2", "A", "serious",
                        "div.accordion:not([role])"),

                // 2.4.1 – No skip-navigation link
                label("https://www.w3.org/WAI/demos/bad/before/news.html",
                        "2.4.1", "A", "moderate",
                        "body"),

                // 1.1.1 – News thumbnail images missing alt
                label("https://www.w3.org/WAI/demos/bad/before/news.html",
                        "1.1.1", "A", "critical",
                        "img.thumbnail:not([alt])")
        );
    }

    private GroundTruthLabel label(String url, String criterion, String level,
                                    String severity, String selector) {
        return GroundTruthLabel.builder()
                .url(url)
                .wcagCriterion(criterion)
                .wcagLevel(level)
                .severity(severity)
                .elementSelector(selector)
                .annotator(ANNOTATOR)
                .confidence(CONFIDENCE)
                .build();
    }
}
