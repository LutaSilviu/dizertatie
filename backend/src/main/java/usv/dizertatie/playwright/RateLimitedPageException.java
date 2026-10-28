package usv.dizertatie.playwright;

/** Raised when the target responds with HTTP 429 and cannot be scanned reliably. */
public class RateLimitedPageException extends RuntimeException {

    public RateLimitedPageException(String message) {
        super(message);
    }
}
