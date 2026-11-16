package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test, with a real browser: one sub-resource that never finishes (here an image)
 * kept the {@code load} event from firing and failed the whole scan with a timeout.
 */
class PageNavigatorHangingResourceTest {

    private static final String PAGE = """
            <!doctype html><html lang="en"><head><title>Hanging image</title></head>
            <body><h1>Content</h1><img src="/never" alt=""></body></html>
            """;

    private HttpServer server;
    private ExecutorService executor;
    private final CountDownLatch release = new CountDownLatch(1);
    private Playwright playwright;
    private Browser browser;

    @BeforeEach
    void setUp() throws Exception {
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/page", exchange -> {
            byte[] body = PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/never", exchange -> {
            try {
                release.await(60, TimeUnit.SECONDS); // never answers while the test runs
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.createContext("/slow-document", exchange -> {
            try {
                release.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        browser.close();
        playwright.close();
        server.stop(0);
        executor.shutdownNow();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void hangingSubResource_doesNotFailNavigation_pageIsAuditedAsRendered() {
        Page page = browser.newPage();

        long start = System.currentTimeMillis();
        PageNavigator.open(page, url("/page"), 10_000, 1_500, 1_000);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(page.content()).contains("<h1>Content</h1>");
        assertThat(elapsed).as("only the soft load grace is spent, not the network-idle one").isLessThan(8_000);
    }

    @Test
    void documentThatNeverArrives_stillFailsWithTimeout() {
        Page page = browser.newPage();

        assertThatThrownBy(() -> PageNavigator.open(page, url("/slow-document"), 1_500, 1_000, 1_000))
                .isInstanceOf(TimeoutError.class);
    }
}
