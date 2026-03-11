package com.solarpumps.flink.sink;

import com.solarpumps.flink.model.TelemetryMessage;

import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Factory for the TimescaleDB JDBC sink that inserts validated telemetry
 * into the {@code raw_telemetry} hypertable.
 *
 * <p>Uses batch inserts: flushes every <b>100 records</b> or <b>10 seconds</b>,
 * whichever comes first.
 */
public final class TimescaleDbSinkFactory {

    private TimescaleDbSinkFactory() {}

    /** SQL INSERT for the raw_telemetry table. */
    private static final String INSERT_SQL =
            "INSERT INTO raw_telemetry ("
            + "  time, pump_id, latitude, longitude, region,"
            + "  irradiance_w_m2, solar_voltage_v, solar_current_a, solar_power_w, solar_temperature_c,"
            + "  motor_voltage_v, motor_current_a, rpm, flow_rate_lpm,"
            + "  discharge_pressure_bar, suction_pressure_bar, water_temperature_c,"
            + "  operating_hours, vibration_mm_s, error_code, status,"
            + "  ambient_temperature_c, humidity_percent"
            + ") VALUES ("
            + "  ?, ?, ?, ?, ?,"
            + "  ?, ?, ?, ?, ?,"
            + "  ?, ?, ?, ?,"
            + "  ?, ?, ?,"
            + "  ?, ?, ?, ?,"
            + "  ?, ?"
            + ")";

    /**
     * Build the JDBC sink with batch execution options.
     *
     * @param jdbcUrl  JDBC connection URL (e.g. {@code jdbc:postgresql://timescaledb:5432/iot_data})
     * @param username database username
     * @param password database password
     * @return a ready-to-use Flink {@link SinkFunction}
     */
    public static SinkFunction<TelemetryMessage> build(
            String jdbcUrl, String username, String password) {

        return JdbcSink.sink(
                INSERT_SQL,
                (ps, msg) -> {
                    Timestamp ts = Timestamp.from(Instant.parse(msg.getTimestamp()));
                    int i = 1;

                    ps.setTimestamp(i++, ts);
                    ps.setString(i++, msg.getPumpId());

                    // Location (nullable)
                    if (msg.getLocation() != null) {
                        ps.setDouble(i++, msg.getLocation().getLatitude());
                        ps.setDouble(i++, msg.getLocation().getLongitude());
                        ps.setString(i++, msg.getLocation().getRegion());
                    } else {
                        ps.setDouble(i++, 0);
                        ps.setDouble(i++, 0);
                        ps.setString(i++, "UNKNOWN");
                    }

                    // Solar panel
                    ps.setDouble(i++, msg.getSolarPanel().getIrradianceWM2());
                    ps.setDouble(i++, msg.getSolarPanel().getVoltageV());
                    ps.setDouble(i++, msg.getSolarPanel().getCurrentA());
                    ps.setDouble(i++, msg.getSolarPanel().getPowerW());
                    ps.setDouble(i++, msg.getSolarPanel().getTemperatureC());

                    // Pump
                    ps.setDouble(i++, msg.getPump().getMotorVoltageV());
                    ps.setDouble(i++, msg.getPump().getMotorCurrentA());
                    ps.setInt(i++, msg.getPump().getRpm());
                    ps.setDouble(i++, msg.getPump().getFlowRateLpm());
                    ps.setDouble(i++, msg.getPump().getDischargePressureBar());
                    ps.setDouble(i++, msg.getPump().getSuctionPressureBar());
                    ps.setDouble(i++, msg.getPump().getWaterTemperatureC());

                    // System
                    ps.setDouble(i++, msg.getSystem().getOperatingHours());
                    ps.setDouble(i++, msg.getSystem().getVibrationMmS());
                    ps.setInt(i++, msg.getSystem().getErrorCode());
                    ps.setString(i++, msg.getSystem().getStatus());

                    // Environment (nullable)
                    if (msg.getEnvironment() != null) {
                        ps.setDouble(i++, msg.getEnvironment().getAmbientTemperatureC());
                        ps.setDouble(i++, msg.getEnvironment().getHumidityPercent());
                    } else {
                        ps.setDouble(i++, 0);
                        ps.setDouble(i++, 0);
                    }
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(100)
                        .withBatchIntervalMs(10_000)
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
