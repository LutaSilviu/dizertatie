package usv.dizertatie.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiStatusServiceTest {

    private static final String REAL_LOOKING_KEY = "sk-proj-abcdefghijklmnop1234567890";

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> authHeader = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            hits.incrementAndGet();
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private static AiStatusService service(String key, String baseUrl, Clock clock) {
        return new AiStatusService(key, baseUrl, HttpClient.newHttpClient(), clock);
    }

    @Test
    void placeholderKeys_areMissing_withoutAnyNetworkCall() throws Exception {
        String base = startServer(200);
        for (String key : new String[]{"", "  ", null, "test-key", "sk-...", "sk-placeholder-key", "PLACEHOLDER"}) {
            AiStatusService.AiStatus s = service(key, base, Clock.systemUTC()).status();
            assertThat(s.status()).as(String.valueOf(key)).isEqualTo(AiStatusService.Status.MISSING_KEY);
        }
        assertThat(hits.get()).isZero();
    }

    @Test
    void validKey_isOk_andSentAsBearerToken() throws Exception {
        String base = startServer(200);

        AiStatusService.AiStatus s = service(REAL_LOOKING_KEY, base, Clock.systemUTC()).status();

        assertThat(s.status()).isEqualTo(AiStatusService.Status.OK);
        assertThat(authHeader.get()).isEqualTo("Bearer " + REAL_LOOKING_KEY);
    }

    @Test
    void rejectedKey_isInvalid_andMessageHasNoKeyFragment() throws Exception {
        String base = startServer(401);

        AiStatusService.AiStatus s = service(REAL_LOOKING_KEY, base, Clock.systemUTC()).status();

        assertThat(s.status()).isEqualTo(AiStatusService.Status.INVALID_KEY);
        assertThat(s.message()).doesNotContain("sk-").doesNotContain("abcdef");
    }

    @Test
    void baseUrlWithoutV1_isAccepted() throws Exception {
        String base = startServer(200).replace("/v1", "");
        assertThat(service(REAL_LOOKING_KEY, base, Clock.systemUTC()).status().status())
                .isEqualTo(AiStatusService.Status.OK);
    }

    @Test
    void networkError_isUnreachable() {
        // Port 9 on localhost: nothing listens, the connection is refused.
        AiStatusService.AiStatus s = service(REAL_LOOKING_KEY, "http://127.0.0.1:9/v1", Clock.systemUTC()).status();
        assertThat(s.status()).isEqualTo(AiStatusService.Status.UNREACHABLE);
    }

    @Test
    void serverError_isUnreachable() throws Exception {
        String base = startServer(503);
        assertThat(service(REAL_LOOKING_KEY, base, Clock.systemUTC()).status().status())
                .isEqualTo(AiStatusService.Status.UNREACHABLE);
    }

    @Test
    void resultIsCachedForSixtySeconds() throws Exception {
        String base = startServer(200);
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T10:00:00Z"));
        AiStatusService service = service(REAL_LOOKING_KEY, base, clock);

        service.status();
        clock.advance(Duration.ofSeconds(59));
        service.status();
        assertThat(hits.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(2));
        service.status();
        assertThat(hits.get()).isEqualTo(2);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
