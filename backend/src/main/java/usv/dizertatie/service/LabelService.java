package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import usv.dizertatie.controller.LabelRequest;
import usv.dizertatie.controller.LabelResponse;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final GroundTruthLabelRepository repository;

    public List<LabelResponse> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<LabelResponse> findByUrl(String url) {
        return repository.findByUrl(url).stream()
                .map(this::toResponse)
                .toList();
    }

    public LabelResponse create(LabelRequest request) {
        GroundTruthLabel label = GroundTruthLabel.builder()
                .url(request.url())
                .wcagCriterion(request.wcagCriterion())
                .wcagLevel(request.wcagLevel())
                .severity(request.severity())
                .elementSelector(request.elementSelector())
                .annotator(request.annotator())
                .verifiedBy(request.verifiedBy())
                .confidence(request.confidence())
                .build();
        return toResponse(repository.save(label));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found: " + id);
        }
        repository.deleteById(id);
    }

    private LabelResponse toResponse(GroundTruthLabel label) {
        return new LabelResponse(
                label.getId(),
                label.getUrl(),
                label.getWcagCriterion(),
                label.getWcagLevel(),
                label.getSeverity(),
                label.getElementSelector(),
                label.getAnnotator(),
                label.getVerifiedBy(),
                label.getConfidence()
        );
    }
}
