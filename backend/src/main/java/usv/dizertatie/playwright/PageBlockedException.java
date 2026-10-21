package usv.dizertatie.playwright;

/**
 * Unchecked exception thrown when the target site does not serve the requested page to the
 * headless browser — e.g. a bot challenge or an HTTP error — so there is nothing valid to audit.
 */
public class PageBlockedException extends RuntimeException {

    public PageBlockedException(String message) {
        super(message);
    }
}
