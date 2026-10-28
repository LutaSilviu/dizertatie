package usv.dizertatie.domain;

/** Describes whether a scan attempt completed or why it could not be completed. */
public enum ScanStatus {
    COMPLETED,
    TIMED_OUT,
    RATE_LIMITED,
    BLOCKED,
    FAILED
}
