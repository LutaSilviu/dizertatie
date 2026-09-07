package ro.sluta.accessibility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublicUrlValidatorTest {
    private final PublicUrlValidator validator = new PublicUrlValidator();

    @Test
    void acceptsPublicHttpUrl() {
        assertThat(validator.validate("https://example.com/page").getHost()).isEqualTo("example.com");
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatThrownBy(() -> validator.validate("file:///tmp/page.html"))
                .isInstanceOf(UrlValidationException.class);
    }

    @Test
    void rejectsLocalAndMetadataDestinations() {
        assertThatThrownBy(() -> validator.validate("http://localhost:8080"))
                .isInstanceOf(UrlValidationException.class);
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1"))
                .isInstanceOf(UrlValidationException.class);
        assertThatThrownBy(() -> validator.validate("http://192.168.1.10"))
                .isInstanceOf(UrlValidationException.class);
        assertThatThrownBy(() -> validator.validate("http://[::1]"))
                .isInstanceOf(UrlValidationException.class);
        assertThatThrownBy(() -> validator.validate("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(UrlValidationException.class);
    }

    @Test
    void rejectsCredentialsAndMalformedUrls() {
        assertThatThrownBy(() -> validator.validate("https://user:secret@example.com"))
                .isInstanceOf(UrlValidationException.class);
        assertThatThrownBy(() -> validator.validate("not a url"))
                .isInstanceOf(UrlValidationException.class);
    }
}
