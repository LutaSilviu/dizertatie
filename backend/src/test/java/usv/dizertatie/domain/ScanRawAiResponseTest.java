package usv.dizertatie.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POJO test verifying that the rawAiResponse field can be set and retrieved on Scan.
 * No database or Spring context required.
 */
class ScanRawAiResponseTest {

    @Test
    void rawAiResponse_canBeSetAndRetrieved() {
        String rawJson = "{\"choices\":[{\"message\":{\"content\":\"test\"}}]}";

        Scan scan = Scan.builder()
                .url("https://example.com")
                .method("AI")
                .timestamp(LocalDateTime.of(2026, 10, 11, 12, 0))
                .rawAiResponse(rawJson)
                .build();

        assertThat(scan.getRawAiResponse()).isEqualTo(rawJson);
    }

    @Test
    void rawAiResponse_isNullableByDefault() {
        Scan scan = Scan.builder()
                .url("https://example.com")
                .method("AXE")
                .timestamp(LocalDateTime.of(2026, 10, 11, 12, 0))
                .build();

        assertThat(scan.getRawAiResponse()).isNull();
    }

    @Test
    void rawAiResponse_canBeUpdatedViaSetter() {
        Scan scan = Scan.builder()
                .url("https://example.com")
                .method("AI")
                .timestamp(LocalDateTime.of(2026, 10, 11, 12, 0))
                .build();

        String rawJson = "{\"id\":\"chatcmpl-abc\",\"object\":\"chat.completion\"}";
        scan.setRawAiResponse(rawJson);

        assertThat(scan.getRawAiResponse()).isEqualTo(rawJson);
    }
}
