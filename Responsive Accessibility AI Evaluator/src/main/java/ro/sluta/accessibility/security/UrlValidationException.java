package ro.sluta.accessibility.security;

import ro.sluta.accessibility.domain.SnapshotErrorCode;

public class UrlValidationException extends RuntimeException {
    private final SnapshotErrorCode errorCode;

    public UrlValidationException(String message) {
        this(SnapshotErrorCode.INVALID_URL, message);
    }

    public UrlValidationException(SnapshotErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SnapshotErrorCode errorCode() { return errorCode; }
}
