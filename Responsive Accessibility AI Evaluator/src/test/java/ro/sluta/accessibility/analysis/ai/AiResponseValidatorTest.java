package ro.sluta.accessibility.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiResponseValidatorTest {
    private final AiResponseValidator validator = new AiResponseValidator(new ObjectMapper());

    @Test void acceptsTheVersionedContractAndEmptyFindings() {
        assertThat(validator.validate(AiTestFixtures.VALID).valid()).isTrue();
        assertThat(validator.validate("{\"analysisSummary\":\"Nimic\",\"limitations\":[],\"findings\":[]}").valid()).isTrue();
    }

    @Test void rejectsAdditionalPropertiesInvalidWcagAndLimits() {
        assertThat(validator.validate(AiTestFixtures.VALID.replace("\"analysisSummary\"", "\"extra\":1,\"analysisSummary\"")).valid()).isFalse();
        assertThat(validator.validate(AiTestFixtures.VALID.replace("1.1.1", "WCAG 1.1.1")).valid()).isFalse();
        assertThat(validator.validate(AiTestFixtures.VALID.replace("0.9", "1.1")).valid()).isFalse();
    }
}
