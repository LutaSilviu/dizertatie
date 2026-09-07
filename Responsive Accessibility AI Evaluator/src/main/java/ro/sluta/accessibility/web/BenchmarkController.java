package ro.sluta.accessibility.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import ro.sluta.accessibility.benchmark.BenchmarkCatalog;
import ro.sluta.accessibility.benchmark.BenchmarkFixture;

@Controller
public class BenchmarkController {
    private final BenchmarkCatalog catalog;
    public BenchmarkController(BenchmarkCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/benchmark")
    public String index(Model model) {
        model.addAttribute("fixtures", catalog.fixtures());
        model.addAttribute("cases", catalog.cases());
        return "benchmark-index";
    }

    @GetMapping("/benchmark/{scenarioId}/{variant}")
    public String fixture(@PathVariable String scenarioId, @PathVariable String variant, Model model) {
        BenchmarkFixture fixture = catalog.fixture(scenarioId, variant);
        model.addAttribute("fixture", fixture); model.addAttribute("scenario", fixture.scenario());
        model.addAttribute("bad", fixture.variant().name().equals("BAD"));
        return "benchmark-fixture";
    }

    @ResponseBody
    @GetMapping("/api/v1/benchmark/manifest")
    public ResponseEntity<?> manifest() {
        return ResponseEntity.ok(java.util.Map.of("fixtureVersion", BenchmarkCatalog.FIXTURE_VERSION,
                "groundTruthVersion", BenchmarkCatalog.GROUND_TRUTH_VERSION, "fixtures", catalog.fixtures().size(),
                "cases", catalog.cases().size(), "caseManifest", catalog.cases()));
    }
}
