package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

/**
 * Wrapper for messages routed to the dead-letter queue (DLQ).
 *
 * <p>Contains the original raw payload, the validation error reason,
 * and metadata about when and where the error occurred.
 */
public class DLQMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("original_payload")
    private String originalPayload;

    @JsonProperty("error_reason")
    private String errorReason;

    @JsonProperty("error_type")
    private String errorType;

    @JsonProperty("pump_id")
    private String pumpId;

    @JsonProperty("failed_at")
    private String failedAt;

    public DLQMessage() {}

    public DLQMessage(String originalPayload, String errorReason,
                       String errorType, String pumpId) {
        this.originalPayload = originalPayload;
        this.errorReason = errorReason;
        this.errorType = errorType;
        this.pumpId = pumpId;
        this.failedAt = Instant.now().toString();
    }

    public String getOriginalPayload() { return originalPayload; }
    public void setOriginalPayload(String originalPayload) { this.originalPayload = originalPayload; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getPumpId() { return pumpId; }
    public void setPumpId(String pumpId) { this.pumpId = pumpId; }

    public String getFailedAt() { return failedAt; }
    public void setFailedAt(String failedAt) { this.failedAt = failedAt; }

    @Override
    public String toString() {
        return "DLQMessage{pumpId='" + pumpId + "'"
                + ", errorType='" + errorType + "'"
                + ", errorReason='" + errorReason + "'"
                + ", failedAt='" + failedAt + "'}";
    }
}
