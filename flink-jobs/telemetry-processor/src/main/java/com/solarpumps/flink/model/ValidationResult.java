package com.solarpumps.flink.model;

import java.io.Serializable;

/**
 * Result of validating a single {@link TelemetryMessage}.
 */
public class ValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean valid;
    private final String errorReason;
    private final String errorType;

    private ValidationResult(boolean valid, String errorReason, String errorType) {
        this.valid = valid;
        this.errorReason = errorReason;
        this.errorType = errorType;
    }

    /** Create a passing result. */
    public static ValidationResult ok() {
        return new ValidationResult(true, null, null);
    }

    /** Create a failing result with an error description. */
    public static ValidationResult fail(String errorType, String errorReason) {
        return new ValidationResult(false, errorReason, errorType);
    }

    public boolean isValid() { return valid; }
    public String getErrorReason() { return errorReason; }
    public String getErrorType() { return errorType; }

    @Override
    public String toString() {
        return valid ? "ValidationResult{OK}"
                : "ValidationResult{FAIL, type='" + errorType
                  + "', reason='" + errorReason + "'}";
    }
}
