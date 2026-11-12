package usv.dizertatie.dataset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DatasetSeederTest {
    @Test
    void repeatedStartupDoesNotDuplicateSeedLabels() throws Exception {
        var repository = mock(GroundTruthLabelRepository.class);
        List<GroundTruthLabel> saved = new ArrayList<>();
        when(repository.findByUrl(anyString())).thenAnswer(invocation -> saved.stream()
                .filter(label -> label.getUrl().equals(invocation.getArgument(0))).toList());
        when(repository.save(any(GroundTruthLabel.class))).thenAnswer(invocation -> {
            GroundTruthLabel label = invocation.getArgument(0);
            saved.add(label);
            return label;
        });
        var seeder = new DatasetSeeder(repository);

        seeder.run(null);
        int count = saved.size();
        seeder.run(null);

        assertThat(count).isGreaterThanOrEqualTo(20);
        assertThat(saved).hasSize(count);
    }

    @Test
    void seededUrlsReferToBundledPages() throws Exception {
        var repository = mock(GroundTruthLabelRepository.class);
        when(repository.findByUrl(anyString())).thenReturn(List.of());
        new DatasetSeeder(repository).run(null);

        var labels = ArgumentCaptor.forClass(GroundTruthLabel.class);
        verify(repository, atLeast(20)).save(labels.capture());
        assertThat(labels.getAllValues().stream().map(GroundTruthLabel::getUrl).distinct().count())
                .as("distinct benchmark pages").isGreaterThanOrEqualTo(20);
        for (GroundTruthLabel label : labels.getAllValues()) {
            String path = label.getUrl().replace("http://localhost:8080/", "static/");
            assertThat(getClass().getClassLoader().getResource(path))
                    .as("Bundled page for %s", label.getUrl()).isNotNull();
            if (path.startsWith("static/fixtures/synthetic/")) {
                try (var page = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertThat(new String(page.readAllBytes(), StandardCharsets.UTF_8)).contains("id=\"defect\"");
                    assertThat(label.getElementSelector()).isEqualTo("#defect");
                }
            }
        }
    }
}
