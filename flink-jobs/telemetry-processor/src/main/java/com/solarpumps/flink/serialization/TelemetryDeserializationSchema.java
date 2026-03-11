package com.solarpumps.flink.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solarpumps.flink.model.TelemetryMessage;

import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Deserializes JSON bytes from RabbitMQ into {@link TelemetryMessage} POJOs.
 *
 * <p>Uses Jackson for JSON parsing. Unknown properties in the payload are
 * silently ignored so that schema evolution does not break the pipeline.
 */
public class TelemetryDeserializationSchema
        extends AbstractDeserializationSchema<TelemetryMessage> {

    private static final long serialVersionUID = 1L;

    private transient ObjectMapper mapper;

    public TelemetryDeserializationSchema() {
        super(TelemetryMessage.class);
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
    public TelemetryMessage deserialize(byte[] message) throws IOException {
        return getMapper().readValue(message, TelemetryMessage.class);
    }
}
