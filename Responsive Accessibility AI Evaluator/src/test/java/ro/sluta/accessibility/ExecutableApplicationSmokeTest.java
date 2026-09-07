package ro.sluta.accessibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExecutableApplicationSmokeTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;

    @Test
    void startsTheRealEmbeddedServerAndReportsHealthUp() {
        var response = rest.getForEntity("http://127.0.0.1:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void servesTheThymeleafApplicationFromTheRealEmbeddedServer() {
        var response = rest.getForEntity("http://127.0.0.1:" + port + "/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Responsive Accessibility AI Evaluator")
                .contains("Capturează și rulează axe-core")
                .contains("Analiză AI pe un snapshot nou");
    }
}
