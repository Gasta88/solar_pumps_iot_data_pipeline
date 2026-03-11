package com.solarpumps.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.solarpumps.flink.aggregation.TelemetryAggregationFunction;
import com.solarpumps.flink.metrics.PipelineMetrics;
import com.solarpumps.flink.model.*;
import com.solarpumps.flink.serialization.JsonSerializationSchema;
import com.solarpumps.flink.serialization.TelemetryDeserializationSchema;
import com.solarpumps.flink.sink.AggregatedMetricsSinkFactory;
import com.solarpumps.flink.sink.TimescaleDbSinkFactory;
import com.solarpumps.flink.validation.TelemetryValidator;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.rabbitmq.RMQSink;
import org.apache.flink.streaming.connectors.rabbitmq.RMQSource;
import org.apache.flink.streaming.connectors.rabbitmq.common.RMQConnectionConfig;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Main Apache Flink streaming job for the Solar Pumps IoT pipeline.
 *
 * <h2>Pipeline topology</h2>
 * <pre>
 * RabbitMQ (telemetry.raw)
 *     |
 *     v
 * [Deserialize JSON -> TelemetryMessage]
 *     |
 *     v
 * [Validate]---invalid---> RabbitMQ (dlq.invalid)  [DLQMessage]
 *     |
 *     v  valid
 *     +-----> TimescaleDB  (raw_telemetry)
 *     +-----> RabbitMQ     (valid.telemetry)
 *     +-----> Aggregations (1-min & 5-min) -> TimescaleDB (aggregated_metrics)
 * </pre>
 */
public class TelemetryPipelineJob {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryPipelineJob.class);

    /** Side output tag for invalid messages routed to DLQ. */
    private static final OutputTag<DLQMessage> DLQ_TAG =
            new OutputTag<DLQMessage>("dlq-invalid") {};

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //

    public static void main(String[] args) throws Exception {
        // ----- Environment configuration -----
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();

        // Checkpointing every 60 seconds
        env.enableCheckpointing(60_000);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30_000);
        env.getCheckpointConfig().setCheckpointTimeout(120_000);

        // ----- Configuration from environment variables -----
        String rmqHost     = envOrDefault("RABBITMQ_HOST", "rabbitmq");
        int    rmqPort     = Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672"));
        String rmqUser     = envOrDefault("RABBITMQ_USER", "iot_user");
        String rmqPass     = envOrDefault("RABBITMQ_PASS", "changeme_rabbitmq");
        String rmqVHost    = envOrDefault("RABBITMQ_VHOST", "/");

        String jdbcUrl     = envOrDefault("TIMESCALEDB_JDBC_URL",
                "jdbc:postgresql://timescaledb:5432/iot_data");
        String dbUser      = envOrDefault("POSTGRES_USER", "iot_user");
        String dbPass      = envOrDefault("POSTGRES_PASSWORD", "changeme_postgres");

        // ----- RabbitMQ connection -----
        RMQConnectionConfig rmqConfig = new RMQConnectionConfig.Builder()
                .setHost(rmqHost)
                .setPort(rmqPort)
                .setUserName(rmqUser)
                .setPassword(rmqPass)
                .setVirtualHost(rmqVHost)
                .build();

        // ----- Source: RabbitMQ telemetry.raw queue -----
        DataStream<TelemetryMessage> rawStream = env
                .addSource(new RMQSource<>(
                        rmqConfig,
                        "telemetry.raw",
                        true,   // use correlation IDs for exactly-once
                        new TelemetryDeserializationSchema()))
                .name("rabbitmq-source")
                .uid("rabbitmq-source");

        // Assign event-time watermarks from the message timestamp
        DataStream<TelemetryMessage> withWatermarks = rawStream
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<TelemetryMessage>forBoundedOutOfOrderness(
                                        Duration.ofSeconds(10))
                                .withTimestampAssigner((msg, ts) -> {
                                    try {
                                        return Instant.parse(msg.getTimestamp())
                                                .toEpochMilli();
                                    } catch (Exception e) {
                                        return System.currentTimeMillis();
                                    }
                                }))
                .name("watermark-assigner")
                .uid("watermark-assigner");

        // ----- Validation + routing (valid / DLQ side output) -----
        SingleOutputStreamOperator<TelemetryMessage> validStream =
                withWatermarks
                        .process(new ValidationProcessFunction(DLQ_TAG))
                        .name("validator")
                        .uid("validator");

        DataStream<DLQMessage> dlqStream = validStream.getSideOutput(DLQ_TAG);

        // ----- Sink 1: Valid -> TimescaleDB (raw_telemetry) -----
        validStream.addSink(
                        TimescaleDbSinkFactory.build(jdbcUrl, dbUser, dbPass))
                .name("timescaledb-raw-sink")
                .uid("timescaledb-raw-sink");

        // ----- Sink 2: Valid -> RabbitMQ valid.telemetry queue -----
        validStream.addSink(new RMQSink<>(
                        rmqConfig,
                        "valid.telemetry",
                        new JsonSerializationSchema<>(TelemetryMessage.class)))
                .name("rabbitmq-valid-sink")
                .uid("rabbitmq-valid-sink");

        // ----- Sink 3: Invalid -> RabbitMQ dlq.invalid queue -----
        dlqStream.addSink(new RMQSink<>(
                        rmqConfig,
                        "dlq.invalid",
                        new JsonSerializationSchema<>(DLQMessage.class)))
                .name("rabbitmq-dlq-sink")
                .uid("rabbitmq-dlq-sink");

        // ----- Aggregation: 1-minute tumbling window -----
        DataStream<AggregatedMetric> agg1min = validStream
                .keyBy(TelemetryMessage::getPumpId)
                .window(TumblingEventTimeWindows.of(Time.minutes(1)))
                .apply(new TelemetryAggregationFunction("1m"))
                .name("agg-1min")
                .uid("agg-1min");

        agg1min.addSink(
                        AggregatedMetricsSinkFactory.build(jdbcUrl, dbUser, dbPass))
                .name("timescaledb-agg-1min-sink")
                .uid("timescaledb-agg-1min-sink");

        // ----- Aggregation: 5-minute tumbling window -----
        DataStream<AggregatedMetric> agg5min = validStream
                .keyBy(TelemetryMessage::getPumpId)
                .window(TumblingEventTimeWindows.of(Time.minutes(5)))
                .apply(new TelemetryAggregationFunction("5m"))
                .name("agg-5min")
                .uid("agg-5min");

        agg5min.addSink(
                        AggregatedMetricsSinkFactory.build(jdbcUrl, dbUser, dbPass))
                .name("timescaledb-agg-5min-sink")
                .uid("timescaledb-agg-5min-sink");

        // ----- Launch -----
        LOG.info("Starting Solar Pumps Telemetry Pipeline");
        env.execute("Solar Pumps Telemetry Pipeline");
    }

    // ================================================================== //
    //  Inner classes
    // ================================================================== //

    /**
     * ProcessFunction that validates each telemetry message and routes it
     * to the main output (valid) or the DLQ side output (invalid).
     *
     * <p>Also exposes Prometheus metrics via the Flink metrics API.
     */
    private static class ValidationProcessFunction
            extends org.apache.flink.streaming.api.functions.ProcessFunction<
                    TelemetryMessage, TelemetryMessage> {

        private static final long serialVersionUID = 1L;

        private final OutputTag<DLQMessage> dlqTag;
        private transient TelemetryValidator validator;
        private transient PipelineMetrics metrics;
        private transient ObjectMapper mapper;

        ValidationProcessFunction(OutputTag<DLQMessage> dlqTag) {
            this.dlqTag = dlqTag;
        }

        @Override
        public void open(Configuration parameters) {
            this.validator = new TelemetryValidator();
            this.metrics = new PipelineMetrics();
            this.metrics.register(getRuntimeContext().getMetricGroup());
            this.mapper = new ObjectMapper();
            this.mapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void processElement(
                TelemetryMessage msg,
                Context ctx,
                Collector<TelemetryMessage> out) {
            try {
                metrics.incRecordsProcessed();

                ValidationResult result = validator.validate(msg);

                if (result.isValid()) {
                    metrics.incRecordsWrittenTimescale();
                    out.collect(msg);
                } else {
                    metrics.incValidationError(result.getErrorType());

                    String payload;
                    try {
                        payload = mapper.writeValueAsString(msg);
                    } catch (Exception e) {
                        payload = msg != null ? msg.toString() : "null";
                    }

                    DLQMessage dlq = new DLQMessage(
                            payload,
                            result.getErrorReason(),
                            result.getErrorType(),
                            msg != null ? msg.getPumpId() : "UNKNOWN");

                    ctx.output(dlqTag, dlq);

                    LOG.warn("Validation failed for pump_id={}: {} — {}",
                            msg != null ? msg.getPumpId() : "UNKNOWN",
                            result.getErrorType(),
                            result.getErrorReason());
                }
            } catch (Exception e) {
                // Catch-all: never let the job fail
                LOG.error("Unexpected error processing message: {}",
                        msg != null ? msg.getPumpId() : "null", e);

                String payload;
                try {
                    payload = mapper.writeValueAsString(msg);
                } catch (Exception ex) {
                    payload = msg != null ? msg.toString() : "null";
                }

                DLQMessage dlq = new DLQMessage(
                        payload,
                        "Unexpected processing error: " + e.getMessage(),
                        "PROCESSING_ERROR",
                        msg != null ? msg.getPumpId() : "UNKNOWN");

                ctx.output(dlqTag, dlq);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private static String envOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
