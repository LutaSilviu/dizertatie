package usv.dizertatie.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PageCaptureServiceTest {

    @Mock
    private BrowserType browserType;

    @Mock
    private Browser browser;

    @Mock
    private Page page;

    private PageCaptureService service;

    @BeforeEach
    void setUp() {
        service = new PageCaptureService(browserType);
    }

    @Test
    void capture_successfulNavigation_returnsPageCaptureWithHtmlAndScreenshot() {
        // Arrange
        String url = "https://example.com";
        String expectedHtml = "<html><head></head><body><h1>Hello</h1></body></html>";
        byte[] expectedScreenshot = new byte[]{1, 2, 3};

        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        Response ok = okResponse();
        when(page.navigate(eq(url), any(Page.NavigateOptions.class))).thenReturn(ok);
        when(page.content()).thenReturn(expectedHtml);
        when(page.screenshot(any(Page.ScreenshotOptions.class))).thenReturn(expectedScreenshot);

        // Act
        PageCapture result = service.capture(url);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.html()).isEqualTo(expectedHtml);
        assertThat(result.screenshotPng()).isEqualTo(expectedScreenshot);
        verify(page).close();
        verify(browser).close();
    }

    @Test
    void captureHtml_successfulNavigation_returnsHtmlContent() {
        // Arrange
        String url = "https://example.com";
        String expectedHtml = "<html><head></head><body><h1>Hello</h1></body></html>";

        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        Response ok = okResponse();
        when(page.navigate(eq(url), any(Page.NavigateOptions.class))).thenReturn(ok);
        when(page.content()).thenReturn(expectedHtml);
        when(page.screenshot(any(Page.ScreenshotOptions.class))).thenReturn(new byte[]{1, 2, 3});

        // Act
        String result = service.captureHtml(url);

        // Assert
        assertThat(result).isEqualTo(expectedHtml);
        verify(page).close();
        verify(browser).close();
    }

    @Test
    void captureHtml_navigationThrowsException_throwsPageCaptureException() {
        // Arrange
        String url = "https://unreachable.invalid";

        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        when(page.navigate(eq(url), any(Page.NavigateOptions.class)))
                .thenThrow(new RuntimeException("net::ERR_NAME_NOT_RESOLVED"));

        // Act & Assert
        assertThatThrownBy(() -> service.captureHtml(url))
                .isInstanceOf(PageCaptureException.class)
                .hasMessageContaining(url)
                .hasCauseInstanceOf(RuntimeException.class);

        // Page and browser should still be closed even on failure
        verify(page).close();
        verify(browser).close();
    }

    @Test
    void capture_pageNeverGoesNetworkIdle_isStillCaptured() {
        String url = "https://busy.example";
        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        Response ok = okResponse();
        when(page.navigate(eq(url), any(Page.NavigateOptions.class))).thenReturn(ok);
        lenient().doThrow(new TimeoutError("Timeout 8000ms exceeded."))
                .when(page).waitForLoadState(eq(LoadState.NETWORKIDLE), any(Page.WaitForLoadStateOptions.class));
        when(page.content()).thenReturn("<html></html>");
        when(page.screenshot(any(Page.ScreenshotOptions.class))).thenReturn(new byte[]{1});

        assertThat(service.capture(url).html()).isEqualTo("<html></html>");
    }

    @Test
    void capture_navigationTimesOut_throwsReadablePageCaptureException() {
        String url = "https://slow.example";
        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        when(page.navigate(eq(url), any(Page.NavigateOptions.class)))
                .thenThrow(new TimeoutError("Timeout 30000ms exceeded.\n=== logs ===\nnavigating..."));

        assertThatThrownBy(() -> service.capture(url))
                .isInstanceOf(PageCaptureException.class)
                .hasMessageContaining(url)
                .hasMessageContaining("timed out")
                .hasMessageNotContaining("=== logs ===")
                .hasCauseInstanceOf(TimeoutError.class);
        verify(browser).close();
    }

    @Test
    void capture_loadEventNeverFires_isStillCaptured_withoutWaitingForNetworkIdle() {
        String url = "https://hanging-pixel.example";
        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        Response ok = okResponse();
        when(page.navigate(eq(url), any(Page.NavigateOptions.class))).thenReturn(ok);
        lenient().doThrow(new TimeoutError("Timeout 10000ms exceeded."))
                .when(page).waitForLoadState(eq(LoadState.LOAD), any(Page.WaitForLoadStateOptions.class));
        when(page.content()).thenReturn("<html><body>ok</body></html>");
        when(page.screenshot(any(Page.ScreenshotOptions.class))).thenReturn(new byte[]{1});

        assertThat(service.capture(url).html()).contains("ok");
        verify(page, never()).waitForLoadState(eq(LoadState.NETWORKIDLE), any(Page.WaitForLoadStateOptions.class));
        verify(page).navigate(eq(url), argThat((Page.NavigateOptions o) ->
                o.waitUntil == com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
    }

    /** The main-document response of a page that loaded normally. */
    private static Response okResponse() {
        Response response = mock(Response.class);
        when(response.status()).thenReturn(200);
        when(response.headers()).thenReturn(java.util.Map.of());
        return response;
    }

    @Test
    void capture_navigationWithoutMainDocumentResponse_isAFailure() {
        String url = "https://example.com";
        when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenReturn(browser);
        when(browser.newPage()).thenReturn(page);
        when(page.navigate(eq(url), any(Page.NavigateOptions.class))).thenReturn(null);

        assertThatThrownBy(() -> service.capture(url)).isInstanceOf(PageCaptureException.class);
        verify(page, never()).content();
        verify(browser).close();
    }
}
