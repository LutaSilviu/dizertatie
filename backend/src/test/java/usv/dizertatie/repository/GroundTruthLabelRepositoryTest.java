package usv.dizertatie.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import usv.dizertatie.domain.GroundTruthLabel;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GroundTruthLabelRepositoryTest {

    @Autowired
    private GroundTruthLabelRepository repository;

    @Test
    void saveLabelAndFindByUrl() {
        GroundTruthLabel label = GroundTruthLabel.builder()
                .url("https://example.com/page1")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("critical")
                .elementSelector("img.hero")
                .annotator("tester")
                .verifiedBy("reviewer")
                .confidence(0.9)
                .build();
        repository.save(label);

        List<GroundTruthLabel> found = repository.findByUrl("https://example.com/page1");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getWcagCriterion()).isEqualTo("1.1.1");
        assertThat(found.get(0).getUrl()).isEqualTo("https://example.com/page1");
    }

    @Test
    void deleteByIdRemovesLabel() {
        GroundTruthLabel label = GroundTruthLabel.builder()
                .url("https://example.com/delete-test")
                .wcagCriterion("2.1.1")
                .wcagLevel("A")
                .severity("serious")
                .elementSelector("button.nav")
                .annotator("tester")
                .confidence(0.8)
                .build();
        GroundTruthLabel saved = repository.save(label);
        Long id = saved.getId();

        repository.deleteById(id);

        Optional<GroundTruthLabel> result = repository.findById(id);
        assertThat(result).isEmpty();
    }

    @Test
    void findByUrlReturnsBothLabelsWithSameUrlDifferentCriteria() {
        GroundTruthLabel label1 = GroundTruthLabel.builder()
                .url("https://example.com/shared")
                .wcagCriterion("1.1.1")
                .wcagLevel("A")
                .severity("critical")
                .elementSelector("img.logo")
                .annotator("tester")
                .confidence(0.95)
                .build();

        GroundTruthLabel label2 = GroundTruthLabel.builder()
                .url("https://example.com/shared")
                .wcagCriterion("2.4.6")
                .wcagLevel("AA")
                .severity("moderate")
                .elementSelector("h1.title")
                .annotator("tester")
                .confidence(0.85)
                .build();

        repository.save(label1);
        repository.save(label2);

        List<GroundTruthLabel> found = repository.findByUrl("https://example.com/shared");

        assertThat(found).hasSize(2);
        assertThat(found).extracting(GroundTruthLabel::getWcagCriterion)
                .containsExactlyInAnyOrder("1.1.1", "2.4.6");
    }
}
