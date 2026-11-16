package usv.dizertatie.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Seeds the benchmark's ground-truth labels from {@code dataset/ground-truth-labels.json}.
 *
 * <p>Every label is one (page, WCAG success criterion, element) failure taken from the W3C BAD
 * annotations or from manual inspection of the fixture markup; see DATASET.md for the protocol.
 * Selectors are written in the form {@code axe.utils.getSelector} produces, because
 * {@link usv.dizertatie.service.MetricsService} matches findings to labels by exact selector string.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatasetSeeder implements ApplicationRunner {

    private static final String RESOURCE = "dataset/ground-truth-labels.json";
    private static final double CONFIDENCE = 0.9;

    /**
     * Local copy of https://www.w3.org/WAI/demos/bad/ served by this backend (see
     * static/fixtures/wai-bad/NOTICE.md); the live site answers headless browsers with a bot challenge.
     */
    static final String BAD_DEMO = "http://localhost:8080/fixtures/wai-bad/";

    /**
     * Pages that have no expected violations. They carry no labels, so they must be listed here to be
     * part of the built-in benchmark set; every finding reported on them counts as a false positive.
     */
    public static final List<String> NEGATIVE_EXAMPLE_URLS = loadDataset().pages().stream()
            .filter(DatasetPage::negativeExample)
            .map(p -> BAD_DEMO + p.page())
            .toList();

    private final GroundTruthLabelRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<GroundTruthLabel> seeds = buildSeeds();
        int inserted = 0;
        int backfilled = 0;
        for (GroundTruthLabel label : seeds) {
            List<GroundTruthLabel> existing = repository.findByUrl(label.getUrl());
            var match = existing.stream()
                    .filter(e -> stringEquals(e.getWcagCriterion(), label.getWcagCriterion())
                            && stringEquals(e.getElementSelector(), label.getElementSelector()))
                    .findFirst();
            if (match.isEmpty()) {
                repository.save(label);
                inserted++;
            } else if (label.getRationale() != null && !label.getRationale().equals(match.get().getRationale())) {
                // Rows seeded before the rationale was stored (V8) get it backfilled.
                match.get().setRationale(label.getRationale());
                repository.save(match.get());
                backfilled++;
            }
        }
        log.info("DatasetSeeder: inserted {} new ground-truth labels, updated the rationale of {} ({} total seeds defined)",
                inserted, backfilled, seeds.size());
    }

    private static boolean stringEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    static List<GroundTruthLabel> buildSeeds() {
        return loadDataset().pages().stream()
                .flatMap(page -> page.labels().stream().map(l -> GroundTruthLabel.builder()
                        .url(BAD_DEMO + page.page())
                        .wcagCriterion(l.criterion())
                        .wcagLevel(l.level())
                        .severity(l.severity())
                        .elementSelector(l.selector())
                        .annotator(l.source())
                        .confidence(CONFIDENCE)
                        .rationale(l.rationale())
                        .build()))
                .toList();
    }

    static Dataset loadDataset() {
        try (InputStream in = DatasetSeeder.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            return new ObjectMapper().readValue(in, Dataset.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    record Dataset(List<DatasetPage> pages) {}

    record DatasetPage(String page, boolean negativeExample, List<DatasetLabel> labels) {}

    /** {@code source} is one of w3c-annotation, w3c-report, manual; it is stored as the label's annotator. */
    record DatasetLabel(String criterion, String level, String severity, String selector,
                        String source, String rationale) {}
}
