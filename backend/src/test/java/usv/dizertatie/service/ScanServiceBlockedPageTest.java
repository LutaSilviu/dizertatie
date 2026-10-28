package usv.dizertatie.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usv.dizertatie.axe.AxeResultParser;
import usv.dizertatie.axe.AxeService;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.ScanStatus;
import usv.dizertatie.repository.FindingRepository;
import usv.dizertatie.repository.ScanRepository;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test: sites behind Cloudflare answer headless browsers with a
 * "Just a moment..." challenge page. Axe used to audit that page and the scan was
 * persisted as if it were the real site (a single meta-refresh finding).
 */
class ScanServiceBlockedPageTest {

    private static final String CHALLENGE_HTML = """
            <!doctype html>
            <html lang="en">
              <head>
                <title>Just a moment...</title>
                <meta http-equiv="refresh" content="390">
              </head>
              <body>Checking your browser</body>
            </html>
            """;

    private HttpServer server;
    private Playwright playwright;
    private ScanRepository scanRepository;
    private FindingRepository findingRepository;
    private AxeService axeService;
    private ScanService scanService;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/challenge", exchange -> {
            byte[] body = CHALLENGE_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().add("cf-mitigated", "challenge");
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/normal", exchange -> {
            byte[] body = "<!doctype html><html lang='en'><title>Test</title><body>Page</body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/rate-limited", exchange -> {
            byte[] body = "Too many requests".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/server-error", exchange -> {
            byte[] body = "Service unavailable".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(17_000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException disconnected) {
                // The browser closes the timed-out request before the response is sent.
            } finally {
                exchange.close();
            }
        });
        server.start();

        playwright = Playwright.create();
        axeService = mock(AxeService.class);
        when(axeService.run(any())).thenReturn("[]");
        scanRepository = mock(ScanRepository.class);
        findingRepository = mock(FindingRepository.class);
        scanService = new ScanService(playwright.chromium(), axeService, new AxeResultParser(new ObjectMapper()),
                scanRepository, findingRepository, null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        playwright.close();
        server.stop(0);
    }

    @Test
    void runAxeScan_botChallengePage_persistsBlockedAttemptWithoutFindings() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/challenge";
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var scan = scanService.runAxeScan(url);

        assertThat(scan.getStatus()).isEqualTo(ScanStatus.BLOCKED);
        assertThat(scan.getFailureReason()).contains("bot challenge");
        verify(scanRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getUrl().equals(url) && saved.getStatus() == ScanStatus.BLOCKED));
        verify(findingRepository, never()).saveAll(any());
        verify(axeService, never()).run(any());
    }

    @Test
    void runAxeScan_http429_persistsRateLimitedAttempt() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/rate-limited";
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var scan = scanService.runAxeScan(url);

        assertThat(scan.getStatus()).isEqualTo(ScanStatus.RATE_LIMITED);
        assertThat(scan.getFailureReason()).contains("HTTP 429");
        verify(findingRepository, never()).saveAll(any());
        verify(axeService, never()).run(any());
    }

    @Test
    void runAxeScan_http503_persistsFallbackFailure() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/server-error";
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var scan = scanService.runAxeScan(url);

        assertThat(scan.getStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(scan.getFailureReason()).contains("HTTP 503");
        verify(findingRepository, never()).saveAll(any());
        verify(axeService, never()).run(any());
    }

    @Test
    void runAxeScan_navigationTimeout_persistsTimedOutAttempt() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var scan = scanService.runAxeScan(url);

        assertThat(scan.getStatus()).isEqualTo(ScanStatus.TIMED_OUT);
        assertThat(scan.getFailureReason()).contains("Timeout");
        verify(findingRepository, never()).saveAll(any());
        verify(axeService, never()).run(any());
    }

    @Test
    void runAxeScan_persistsManualReviewItemsAndRuleIdsForCompletedScan() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/normal";
        when(axeService.run(any())).thenReturn("""
                {"violations":[{"id":"image-alt","impact":"critical","description":"Images need alt text",
                  "tags":["wcag111"],"nodes":[{"target":["img.hero"]},{"target":["img.logo"]}]}],
                 "incomplete":[{"id":"color-contrast","impact":"serious","description":"Review contrast",
                   "help":"Review contrast","helpUrl":"https://example.com/help",
                   "nodes":[{"target":[".hero"],"failureSummary":"Review image background"}]}]}
                """);
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var scan = scanService.runAxeScan(url);

        assertThat(scan.getRawAxeIncomplete()).contains("color-contrast").contains("Review image background");
        org.mockito.ArgumentCaptor<java.util.List<Finding>> findings = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(findingRepository).saveAll(findings.capture());
        assertThat(findings.getValue()).hasSize(2)
                .extracting(Finding::getRuleId).containsOnly("image-alt");
    }

}
