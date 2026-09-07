package ro.sluta.accessibility.browser;

import ro.sluta.accessibility.domain.SnapshotErrorCode;

public class RedirectResolutionException extends RuntimeException {
    private final SnapshotErrorCode errorCode;

    public RedirectResolutionException(SnapshotErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RedirectResolutionException(SnapshotErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public SnapshotErrorCode errorCode() { return errorCode; }
}
