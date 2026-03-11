package com.solarpumps.flink.model;

import java.io.Serializable;

/**
 * Wrapper that captures the outcome of JSON deserialization.
 */
public class DeserializationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TelemetryMessage message;
    private final String rawPayload;
    private final String error;

    private DeserializationResult(TelemetryMessage message,
                                  String rawPayload,
                                  String error) {
        this.message = message;
        this.rawPayload = rawPayload;
        this.error = error;
    }

    public static DeserializationResult success(TelemetryMessage message,
                                                String rawPayload) {
        return new DeserializationResult(message, rawPayload, null);
    }

    public static DeserializationResult failure(String rawPayload, String error) {
        return new DeserializationResult(null, rawPayload, error);
    }

    public boolean isSuccess() {
        return message != null;
    }

    public TelemetryMessage getMessage() {
        return message;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getError() {
        return error;
    }
}
