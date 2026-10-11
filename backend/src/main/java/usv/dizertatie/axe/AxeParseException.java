package usv.dizertatie.axe;

/**
 * Thrown when the Axe violations JSON cannot be parsed.
 */
public class AxeParseException extends RuntimeException {

    public AxeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
