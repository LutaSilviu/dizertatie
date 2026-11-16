package usv.dizertatie.ai;

/**
 * The AI model answered, but not with the structure the prompt asks for (empty reply, prose
 * instead of JSON, or JSON without a {@code findings} array). The scan has no evidence and must
 * be recorded as failed rather than as a completed scan with zero findings.
 */
public class AiResponseFormatException extends AiAnalysisException {

    public AiResponseFormatException(String message) {
        super(message);
    }

    public AiResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
