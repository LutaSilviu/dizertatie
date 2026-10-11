package usv.dizertatie.wcag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WcagCriterionRegistry {

    private static final String RESOURCE_PATH = "wcag/wcag22-criteria.json";

    private List<WcagCriterion> criteria = Collections.emptyList();
    private Map<String, WcagCriterion> byId = Collections.emptyMap();

    @PostConstruct
    public void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStream in = resource.getInputStream()) {
            List<CriterionDto> dtos = mapper.readValue(in, new TypeReference<>() {});
            criteria = dtos.stream()
                    .map(dto -> new WcagCriterion(dto.id(), dto.name(), WcagLevel.valueOf(dto.level())))
                    .collect(Collectors.toUnmodifiableList());
            byId = criteria.stream()
                    .collect(Collectors.toUnmodifiableMap(WcagCriterion::id, Function.identity()));
        }
    }

    public Optional<WcagLevel> getLevel(String criterionId) {
        return Optional.ofNullable(byId.get(criterionId)).map(WcagCriterion::level);
    }

    public List<WcagCriterion> getAll() {
        return criteria;
    }

    public List<WcagCriterion> getByLevel(WcagLevel level) {
        return criteria.stream()
                .filter(c -> c.level() == level)
                .collect(Collectors.toUnmodifiableList());
    }

    private record CriterionDto(String id, String name, String level) {}
}
