package usv.dizertatie.dataset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DatasetSeederTest {
    @Test
    void seededUrlsReferToBundledPages() throws Exception {
        var repository = mock(GroundTruthLabelRepository.class);
        when(repository.findByUrl(anyString())).thenReturn(List.of());
        new DatasetSeeder(repository).run(null);

        var labels = ArgumentCaptor.forClass(GroundTruthLabel.class);
        verify(repository, atLeast(20)).save(labels.capture());
        for (GroundTruthLabel label : labels.getAllValues()) {
            String path = label.getUrl().replace(DatasetSeeder.BAD_DEMO, "static/fixtures/wai-bad/");
            assertThat(getClass().getClassLoader().getResource(path))
                    .as("Bundled page for %s", label.getUrl()).isNotNull();
        }
    }
}
