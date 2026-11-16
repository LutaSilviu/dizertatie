package usv.dizertatie.error;

import org.junit.jupiter.api.Test;
import usv.dizertatie.ai.AiAnalysisException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureMessagesTest {

    @Test
    void aiAuthFailure_isDetected_andDescribedWithoutKeyFragment() {
        AiAnalysisException e = new AiAnalysisException("AI analysis failed for model gpt-6-luna",
                new RuntimeException("401: Incorrect API key provided: sk-place***-key"));

        assertThat(FailureMessages.isAiAuthFailure(e)).isTrue();
        assertThat(FailureMessages.describe(e)).isEqualTo(FailureMessages.OPENAI_KEY_REJECTED);
    }

    @Test
    void otherAiFailures_keepTheirReason_butKeyFragmentsAreRedacted() {
        AiAnalysisException e = new AiAnalysisException("AI analysis failed for model gpt-4o",
                new RuntimeException("429: Rate limit reached for sk-proj-ab***yz\nmore"));

        assertThat(FailureMessages.isAiAuthFailure(e)).isFalse();
        assertThat(FailureMessages.describe(e))
                .isEqualTo("AI analysis failed for model gpt-4o: 429: Rate limit reached for [redacted]");
    }

    @Test
    void nonAiExceptions_areNeverAuthFailures() {
        assertThat(FailureMessages.isAiAuthFailure(new RuntimeException("401"))).isFalse();
    }
}
