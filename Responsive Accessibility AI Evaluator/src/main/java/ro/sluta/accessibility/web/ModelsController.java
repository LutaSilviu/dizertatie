package ro.sluta.accessibility.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.sluta.accessibility.config.AiAnalysisProperties;

@RestController
@RequestMapping("/api/v1/models")
public class ModelsController {
    private final AiAnalysisProperties properties;

    public ModelsController(AiAnalysisProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public List<ModelDescriptor> listModels() {
        return properties.models().entrySet().stream().filter(entry -> entry.getValue().enabled())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ModelDescriptor(entry.getKey(), entry.getValue().providerModel(),
                        entry.getValue().displayName(), entry.getValue().modalities(),
                        entry.getValue().acceptedParameters(), entry.getValue().effectiveFrom().toString()))
                .toList();
    }

    public record ModelDescriptor(String id, String providerModel, String displayName, List<String> modalities,
                                  List<String> acceptedParameters, String pricesEffectiveFrom) {
    }
}
