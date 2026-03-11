package com.solarpumps.flink.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarpumps.flink.model.DeserializationResult;
import com.solarpumps.flink.model.TelemetryMessage;

import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes JSON bytes from RabbitMQ into {@link TelemetryMessage} POJOs.
 *
 * <p>Uses Jackson for JSON parsing. Unknown properties in the payload are
 * silently ignored so that schema evolution does not break the pipeline.
 */
public class TelemetryDeserializationSchema
        extends AbstractDeserializationSchema<DeserializationResult> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(TelemetryDeserializationSchema.class);

    private transient ObjectMapper mapper;

    public TelemetryDeserializationSchema() {
        super(DeserializationResult.class);
    }

    private ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
        return mapper;
    }

    @Override
    public DeserializationResult deserialize(byte[] message) throws IOException {
        String payload = new String(message, StandardCharsets.UTF_8);
        try {
            TelemetryMessage msg =
                    getMapper().readValue(payload, TelemetryMessage.class);
            return DeserializationResult.success(msg, payload);
        } catch (Exception e) {
            LOG.warn("Failed to parse telemetry JSON: {}", e.getMessage());
            return DeserializationResult.failure(payload, e.getMessage());
        }
    }
}
