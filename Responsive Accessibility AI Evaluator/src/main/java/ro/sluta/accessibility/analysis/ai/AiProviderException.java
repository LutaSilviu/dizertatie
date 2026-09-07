package ro.sluta.accessibility.analysis.ai;

public class AiProviderException extends RuntimeException {
    private final AiErrorCode code;
    private final boolean transientFailure;

    public AiProviderException(AiErrorCode code, boolean transientFailure, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.transientFailure = transientFailure;
    }

    public AiErrorCode code() { return code; }
    public boolean transientFailure() { return transientFailure; }
}
