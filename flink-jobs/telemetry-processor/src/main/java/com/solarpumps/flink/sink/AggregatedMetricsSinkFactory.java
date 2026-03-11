package com.solarpumps.flink.sink;

import com.solarpumps.flink.model.AggregatedMetric;

import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * Factory for the TimescaleDB JDBC sink that inserts windowed aggregation
 * results into the {@code aggregated_metrics} table.
 */
public final class AggregatedMetricsSinkFactory {

    private AggregatedMetricsSinkFactory() {}

    private static final String INSERT_SQL =
            "INSERT INTO aggregated_metrics ("
            + "  window_start, window_end, window_size, pump_id,"
            + "  avg_flow_rate, max_flow_rate, min_flow_rate,"
            + "  avg_solar_power, max_solar_power, min_solar_power,"
            + "  avg_vibration, max_vibration, min_vibration,"
            + "  record_count"
            + ") VALUES ("
            + "  ?, ?, ?, ?,"
            + "  ?, ?, ?,"
            + "  ?, ?, ?,"
            + "  ?, ?, ?,"
            + "  ?"
            + ")";

    /**
     * Build the JDBC sink.
     */
    public static SinkFunction<AggregatedMetric> build(
            String jdbcUrl, String username, String password) {

        return JdbcSink.sink(
                INSERT_SQL,
                (ps, m) -> {
                    int i = 1;
                    ps.setTimestamp(i++, m.getWindowStart());
                    ps.setTimestamp(i++, m.getWindowEnd());
                    ps.setString(i++, m.getWindowSize());
                    ps.setString(i++, m.getPumpId());

                    ps.setDouble(i++, m.getAvgFlowRate());
                    ps.setDouble(i++, m.getMaxFlowRate());
                    ps.setDouble(i++, m.getMinFlowRate());

                    ps.setDouble(i++, m.getAvgSolarPower());
                    ps.setDouble(i++, m.getMaxSolarPower());
                    ps.setDouble(i++, m.getMinSolarPower());

                    ps.setDouble(i++, m.getAvgVibration());
                    ps.setDouble(i++, m.getMaxVibration());
                    ps.setDouble(i++, m.getMinVibration());

                    ps.setLong(i++, m.getRecordCount());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(50)
                        .withBatchIntervalMs(15_000)
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
