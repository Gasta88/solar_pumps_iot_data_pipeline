package com.solarpumps.flink.sink;

import com.solarpumps.flink.model.DLQMessage;

import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Factory for the TimescaleDB JDBC sink that persists dead-letter-queue
 * messages into the {@code dlq_records} hypertable so they can be queried
 * from Grafana's Data Quality dashboard.
 *
 * <p>Uses small batches (20 records / 5 s) because DLQ volume is expected
 * to be low compared to the main telemetry stream.
 */
public final class DlqSinkFactory {

    private DlqSinkFactory() {}

    /** SQL INSERT for the dlq_records table. */
    private static final String INSERT_SQL =
            "INSERT INTO dlq_records ("
            + "  error_timestamp, pump_id, error_type, error_reason, original_payload"
            + ") VALUES (?, ?, ?, ?, ?)";

    /**
     * Build the JDBC sink.
     *
     * @param jdbcUrl  JDBC connection URL
     * @param username database username
     * @param password database password
     * @return a ready-to-use Flink {@link SinkFunction}
     */
    public static SinkFunction<DLQMessage> build(
            String jdbcUrl, String username, String password) {

        return JdbcSink.sink(
                INSERT_SQL,
                (ps, dlq) -> {
                    Timestamp ts;
                    try {
                        ts = Timestamp.from(Instant.parse(dlq.getFailedAt()));
                    } catch (Exception e) {
                        ts = Timestamp.from(Instant.now());
                    }

                    ps.setTimestamp(1, ts);
                    ps.setString(2, dlq.getPumpId());
                    ps.setString(3, dlq.getErrorType());
                    ps.setString(4, dlq.getErrorReason());
                    ps.setString(5, dlq.getOriginalPayload());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(20)
                        .withBatchIntervalMs(5_000)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(jdbcUrl)
                        .withDriverName("org.postgresql.Driver")
                        .withUsername(username)
                        .withPassword(password)
                        .build()
        );
    }
}
