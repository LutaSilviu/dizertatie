package usv.dizertatie.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
