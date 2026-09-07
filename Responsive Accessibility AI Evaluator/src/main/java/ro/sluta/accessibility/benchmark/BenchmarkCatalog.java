package ro.sluta.accessibility.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.GroundTruthIssue;
import ro.sluta.accessibility.domain.Viewport;

@Component
public class BenchmarkCatalog {
    public static final String FIXTURE_VERSION = "BENCHMARK_FIXTURE_V1";
    public static final String GROUND_TRUTH_VERSION = "GROUND_TRUTH_V1";
    private final ObjectMapper mapper;
    private Map<String, BenchmarkScenario> scenarios = Map.of();
    private List<BenchmarkFixture> fixtures = List.of();
    private List<BenchmarkCase> cases = List.of();

    public BenchmarkCatalog(ObjectMapper mapper) { this.mapper = mapper; }

    @PostConstruct
    void loadAndValidate() {
        try {
            byte[] bytes = new ClassPathResource("benchmark/ground-truth-v1.json").getInputStream().readAllBytes();
            List<BenchmarkScenario> loaded = mapper.readValue(bytes, new TypeReference<>() {});
            Map<String, BenchmarkScenario> byId = new LinkedHashMap<>();
            for (BenchmarkScenario scenario : loaded) {
                if (byId.putIfAbsent(scenario.scenarioId(), scenario) != null) {
                    throw new IllegalStateException("Scenariu duplicat: " + scenario.scenarioId());
                }
            }
            if (byId.size() != 12) throw new IllegalStateException("Catalogul trebuie să conțină exact 12 scenarii.");
            for (int index = 1; index <= 12; index++) {
                String expected = "S%02d".formatted(index);
                if (!byId.containsKey(expected)) throw new IllegalStateException("Lipsește scenariul " + expected);
            }
            scenarios = Map.copyOf(byId);
            fixtures = createFixtures(bytes, loaded);
            cases = createCases(fixtures);
            if (fixtures.size() != 24 || cases.size() != 48) throw new IllegalStateException("Dimensiune benchmark invalidă.");
        } catch (Exception exception) {
            throw new IllegalStateException("Catalogul benchmark nu poate fi încărcat și validat.", exception);
        }
    }

    public List<BenchmarkScenario> scenarios() { return scenarios.values().stream().sorted(Comparator.comparing(BenchmarkScenario::scenarioId)).toList(); }
    public List<BenchmarkFixture> fixtures() { return fixtures; }
    public List<BenchmarkCase> cases() { return cases; }
    public BenchmarkFixture fixture(String scenarioId, String variant) {
        BenchmarkVariant parsed;
        try { parsed = BenchmarkVariant.valueOf(variant.toUpperCase(Locale.ROOT)); }
        catch (Exception exception) { throw new IllegalArgumentException("Varianta benchmark trebuie să fie BAD sau GOOD."); }
        return fixtures.stream().filter(value -> value.scenario().scenarioId().equals(scenarioId.toUpperCase(Locale.ROOT))
                && value.variant() == parsed).findFirst().orElseThrow(() -> new IllegalArgumentException("Fixture benchmark inexistent."));
    }

    private List<BenchmarkFixture> createFixtures(byte[] catalogBytes, List<BenchmarkScenario> loaded) {
        String catalogHash = AtomicArtifactStore.sha256(catalogBytes); List<BenchmarkFixture> result = new ArrayList<>();
        for (BenchmarkScenario scenario : loaded.stream().sorted(Comparator.comparing(BenchmarkScenario::scenarioId)).toList()) {
            for (BenchmarkVariant variant : BenchmarkVariant.values()) {
                byte[] identity = (catalogHash + "|" + scenario.scenarioId() + "|" + variant + "|" + FIXTURE_VERSION)
                        .getBytes(StandardCharsets.UTF_8);
                result.add(new BenchmarkFixture(scenario, variant, FIXTURE_VERSION,
                        AtomicArtifactStore.sha256(identity), GROUND_TRUTH_VERSION));
            }
        }
        return List.copyOf(result);
    }

    private List<BenchmarkCase> createCases(List<BenchmarkFixture> values) {
        List<BenchmarkCase> result = new ArrayList<>();
        for (BenchmarkFixture fixture : values) {
            List<Viewport> viewports = fixture.scenario().scenarioId().equals("S12")
                    ? List.of(Viewport.DESKTOP, Viewport.REFLOW_320) : List.of(Viewport.DESKTOP, Viewport.MOBILE);
            for (Viewport viewport : viewports) {
                boolean responsiveBarrierAbsent = fixture.scenario().responsiveOnly() && viewport == Viewport.DESKTOP;
                GroundTruthIssue.ExpectedPresence expected = fixture.variant() == BenchmarkVariant.BAD && !responsiveBarrierAbsent
                        ? GroundTruthIssue.ExpectedPresence.PRESENT : GroundTruthIssue.ExpectedPresence.ABSENT;
                String caseId = fixture.scenario().scenarioId() + "-" + fixture.variant() + "-" + viewport;
                GroundTruthIssue truth = new GroundTruthIssue("GT-" + caseId, fixture.scenario().scenarioId(),
                        fixture.variant().name(), viewport, fixture.scenario().stateId(), fixture.scenario().targetElement(),
                        fixture.scenario().barrier(), fixture.scenario().wcagCriterion(), fixture.scenario().conformanceLevel(),
                        expected, fixture.scenario().manualEvidence(), fixture.fixtureVersion(), fixture.fixtureHash());
                result.add(new BenchmarkCase(caseId, fixture, viewport, truth));
            }
        }
        return List.copyOf(result);
    }
}
