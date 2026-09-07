package ro.sluta.accessibility.experiment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
    private final EvaluationDecisionRepository repository;
    public EvaluationService(EvaluationDecisionRepository repository) { this.repository = repository; }
    public EvaluationDecision create(EvaluationDecisionRequest request) {
        Double quality = average(request.e1(), request.e2(), request.e3(), request.e4(), request.e5());
        return repository.save(new EvaluationDecision(UUID.randomUUID(), request.runId(), request.findingId(),
                request.groundTruthId(), request.detectionClass(), request.localizationScore(), request.wcagScore(),
                request.e1(), request.e2(), request.e3(), request.e4(), request.e5(), quality, request.reviewer(),
                request.decisionKind(), request.blind(), request.sourceDecisionIds(), request.notes(),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }
    private static Double average(Integer... values) {
        List<Integer> present = java.util.Arrays.stream(values).filter(java.util.Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }
}
