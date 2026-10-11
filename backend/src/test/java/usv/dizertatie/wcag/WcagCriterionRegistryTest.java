package usv.dizertatie.wcag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WcagCriterionRegistryTest {

    private WcagCriterionRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        registry = new WcagCriterionRegistry();
        registry.load();
    }

    @Test
    void totalCriteriaCountIs87() {
        assertThat(registry.getAll()).hasSize(87);
    }

    @Test
    void criterion1_1_1IsLevelA() {
        assertThat(registry.getLevel("1.1.1")).isEqualTo(Optional.of(WcagLevel.A));
    }

    @Test
    void criterion1_4_3IsLevelAA() {
        assertThat(registry.getLevel("1.4.3")).isEqualTo(Optional.of(WcagLevel.AA));
    }

    @Test
    void criterion1_4_6IsLevelAAA() {
        assertThat(registry.getLevel("1.4.6")).isEqualTo(Optional.of(WcagLevel.AAA));
    }

    @Test
    void unknownCriterionReturnsEmpty() {
        assertThat(registry.getLevel("9.9.9")).isEqualTo(Optional.empty());
    }
}
