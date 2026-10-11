package usv.dizertatie.playwright;

/**
 * Unchecked exception thrown when a page cannot be captured via Playwright.
 */
public class PageCaptureException extends RuntimeException {

    public PageCaptureException(String message) {
        super(message);
    }

    public PageCaptureException(String message, Throwable cause) {
        super(message, cause);
    }
}
