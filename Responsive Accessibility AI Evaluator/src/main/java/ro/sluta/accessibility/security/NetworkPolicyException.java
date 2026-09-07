package ro.sluta.accessibility.security;

import ro.sluta.accessibility.domain.SnapshotErrorCode;

public class NetworkPolicyException extends RuntimeException {
    private final SnapshotErrorCode errorCode;

    public NetworkPolicyException(SnapshotErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SnapshotErrorCode errorCode() { return errorCode; }
}
