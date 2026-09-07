package ro.sluta.accessibility.web;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.sluta.accessibility.domain.AnalysisMethod;
import ro.sluta.accessibility.domain.AnalysisStatus;
import ro.sluta.accessibility.export.ResearchExportService;
import ro.sluta.accessibility.persistence.*;

@RestController
@RequestMapping("/api/v1/analyses")
public class HistoryExportController {
    private final ResearchPersistenceService persistence;
    private final ResearchExportService exports;
    public HistoryExportController(ResearchPersistenceService persistence, ResearchExportService exports) {
        this.persistence = persistence; this.exports = exports;
    }

    @GetMapping("/history")
    public HistoryPage history(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String url, @RequestParam(required = false) AnalysisMethod method,
            @RequestParam(required = false) String model, @RequestParam(required = false) AnalysisStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean ascending) {
        return persistence.history(new HistoryFilter(from, to, url, method, model, status, page, size, ascending));
    }

    @PostMapping("/{analysisId}/repeat")
    public ResponseEntity<AnalysisRecord> repeat(@PathVariable UUID analysisId) {
        return ResponseEntity.status(201).body(persistence.repeat(analysisId, Instant.now()));
    }

    @GetMapping("/{analysisId}/export/{format}")
    public ResponseEntity<byte[]> export(@PathVariable UUID analysisId, @PathVariable String format) {
        ResearchBundle bundle = persistence.loadBundle(analysisId);
        byte[] bytes; String file; MediaType type;
        switch (format) {
            case "json" -> { bytes = exports.json(bundle); file = "analysis.json"; type = MediaType.APPLICATION_JSON; }
            case "findings.csv" -> { bytes = exports.findingsCsv(bundle); file = "findings.csv"; type = new MediaType("text", "csv"); }
            case "evaluations.csv" -> { bytes = exports.evaluationsCsv(bundle); file = "evaluations.csv"; type = new MediaType("text", "csv"); }
            case "runs.csv" -> { bytes = exports.runsCsv(bundle); file = "runs.csv"; type = new MediaType("text", "csv"); }
            default -> { return ResponseEntity.badRequest().build(); }
        }
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file + "\"").body(bytes);
    }
}
