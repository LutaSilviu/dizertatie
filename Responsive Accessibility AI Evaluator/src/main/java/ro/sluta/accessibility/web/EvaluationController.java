package ro.sluta.accessibility.web;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ro.sluta.accessibility.domain.DetectionClass;
import ro.sluta.accessibility.experiment.*;

@Controller
public class EvaluationController {
    private final EvaluationService service;private final EvaluationDecisionRepository decisions;
    private final ExperimentMetricsService metrics;private final ExperimentPackageExportService exports;
    public EvaluationController(EvaluationService service,EvaluationDecisionRepository decisions,ExperimentMetricsService metrics,ExperimentPackageExportService exports){this.service=service;this.decisions=decisions;this.metrics=metrics;this.exports=exports;}

    @GetMapping("/experiment/evaluations")
    public String page(Model model){model.addAttribute("classes",DetectionClass.values());model.addAttribute("kinds",EvaluationDecisionKind.values());model.addAttribute("decisions",decisions.recent(100));return "experiment-evaluations";}
    @PostMapping("/experiment/evaluations")
    public String submit(@RequestParam UUID runId,@RequestParam(required=false)String findingId,@RequestParam(required=false)String groundTruthId,
            @RequestParam DetectionClass detectionClass,@RequestParam(required=false)Integer localizationScore,@RequestParam(required=false)Integer wcagScore,
            @RequestParam(required=false)Integer e1,@RequestParam(required=false)Integer e2,@RequestParam(required=false)Integer e3,@RequestParam(required=false)Integer e4,@RequestParam(required=false)Integer e5,
            @RequestParam String reviewer,@RequestParam EvaluationDecisionKind decisionKind,@RequestParam(defaultValue="false")boolean blind,
            @RequestParam(required=false)String sourceDecisionIds,@RequestParam(required=false)String notes){
        service.create(new EvaluationDecisionRequest(runId,optionalUuid(findingId),blankToNull(groundTruthId),detectionClass,localizationScore,wcagScore,e1,e2,e3,e4,e5,reviewer,decisionKind,blind,uuidList(sourceDecisionIds),notes));return "redirect:/experiment/evaluations";}
    @ResponseBody @PostMapping("/api/v1/experiments/evaluations") public ResponseEntity<?> create(@RequestBody EvaluationDecisionRequest request){return ResponseEntity.status(201).body(service.create(request));}
    @ResponseBody @GetMapping("/api/v1/experiments/plans/{planId}/metrics") public ResponseEntity<?> metrics(@PathVariable UUID planId){return ResponseEntity.ok(metrics.regenerate(planId));}
    @ResponseBody @GetMapping("/api/v1/experiments/plans/{planId}/export") public ResponseEntity<byte[]> export(@PathVariable UUID planId){return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"experiment-package.json\"").body(exports.export(planId));}
    private static Optional<UUID> optionalUuid(String value){return value==null||value.isBlank()?Optional.empty():Optional.of(UUID.fromString(value));}
    private static java.util.List<UUID> uuidList(String value){return value==null||value.isBlank()?java.util.List.of():java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item->!item.isBlank()).map(UUID::fromString).toList();}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value;}
}
