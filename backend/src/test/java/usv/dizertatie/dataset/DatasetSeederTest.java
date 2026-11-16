package usv.dizertatie.dataset;

import org.junit.jupiter.api.Test;
import usv.dizertatie.domain.GroundTruthLabel;
import usv.dizertatie.repository.GroundTruthLabelRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetSeederTest {

    @Test
    void everySeedCarriesTheDatasetRationale() {
        assertThat(DatasetSeeder.buildSeeds()).isNotEmpty()
                .allSatisfy(l -> assertThat(l.getRationale()).isNotBlank());
    }

    @Test
    void run_insertsMissingLabels_andBackfillsRationaleOfExistingRows() {
        GroundTruthLabel seed = DatasetSeeder.buildSeeds().get(0);
        GroundTruthLabel existingWithoutRationale = GroundTruthLabel.builder()
                .id(1L).url(seed.getUrl()).wcagCriterion(seed.getWcagCriterion())
                .elementSelector(seed.getElementSelector()).build();
        GroundTruthLabel userLabelWithoutCriterion = GroundTruthLabel.builder()
                .id(2L).url(seed.getUrl()).wcagCriterion(null).elementSelector("x").build();

        GroundTruthLabelRepository repository = mock(GroundTruthLabelRepository.class);
        when(repository.findByUrl(anyString())).thenAnswer(inv ->
                inv.getArgument(0).equals(seed.getUrl())
                        ? List.of(userLabelWithoutCriterion, existingWithoutRationale)
                        : List.of());
        List<GroundTruthLabel> saved = new ArrayList<>();
        when(repository.save(any(GroundTruthLabel.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        new DatasetSeeder(repository).run(null);

        assertThat(existingWithoutRationale.getRationale()).isEqualTo(seed.getRationale());
        assertThat(saved).contains(existingWithoutRationale);
        // every other seed was new and inserted
        assertThat(saved).hasSize(DatasetSeeder.buildSeeds().size());
    }
}
