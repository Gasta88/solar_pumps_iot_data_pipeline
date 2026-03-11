package com.solarpumps.flink.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.flink.streaming.connectors.rabbitmq.SerializableReturnListener;
import org.apache.flink.api.common.serialization.SerializationSchema;

import java.nio.charset.StandardCharsets;

/**
 * Generic Jackson-based serialization schema for writing POJOs as JSON
 * bytes back to RabbitMQ.
 *
 * @param <T> the type to serialise
 */
public class JsonSerializationSchema<T> implements SerializationSchema<T> {

    private static final long serialVersionUID = 1L;

    private final Class<T> clazz;
    private transient ObjectMapper mapper;

    public JsonSerializationSchema(Class<T> clazz) {
        this.clazz = clazz;
    }

    private ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
        }
        return mapper;
    }

    @Override
    public byte[] serialize(T element) {
        try {
            return getMapper().writeValueAsBytes(element);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize " + clazz.getSimpleName(), e);
        }
    }
}
