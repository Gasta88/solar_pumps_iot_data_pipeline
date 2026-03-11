package com.solarpumps.flink.validation;

import com.solarpumps.flink.model.*;

import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Stateless validator for {@link TelemetryMessage} objects.
 *
 * <p>Performs two categories of checks:
 * <ol>
 *   <li><strong>Schema validation</strong> &mdash; required fields are present
 *       and have the correct types.</li>
 *   <li><strong>Range validation</strong> &mdash; numeric sensor readings fall
 *       within physically plausible bounds.</li>
 * </ol>
 *
 * <p>All range limits are based on the solar pump equipment specifications:
 * <ul>
 *   <li>Irradiance: 0 &ndash; 1500 W/m&sup2;</li>
 *   <li>Solar panel voltage: 0 &ndash; 100 V</li>
 *   <li>Solar panel current: 0 &ndash; 30 A</li>
 *   <li>Solar power: 0 &ndash; 3000 W</li>
 *   <li>Solar panel temperature: &minus;40 &ndash; 90 &deg;C</li>
 *   <li>Motor voltage: 0 &ndash; 100 V</li>
 *   <li>Motor current: 0 &ndash; 30 A</li>
 *   <li>RPM: 0 &ndash; 5000</li>
 *   <li>Flow rate: 0 &ndash; 500 L/min</li>
 *   <li>Pressures: 0 &ndash; 20 bar</li>
 *   <li>Water temperature: 0 &ndash; 80 &deg;C</li>
 *   <li>Vibration: 0 &ndash; 50 mm/s</li>
 *   <li>Operating hours: 0 &ndash; 100 000 h</li>
 *   <li>Ambient temperature: &minus;40 &ndash; 60 &deg;C</li>
 *   <li>Humidity: 0 &ndash; 100 %</li>
 * </ul>
 */
public class TelemetryValidator implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------ //
    //  Range constants
    // ------------------------------------------------------------------ //
    private static final double IRRADIANCE_MIN = 0;
    private static final double IRRADIANCE_MAX = 1500;

    private static final double SOLAR_VOLTAGE_MIN = 0;
    private static final double SOLAR_VOLTAGE_MAX = 100;

    private static final double SOLAR_CURRENT_MIN = 0;
    private static final double SOLAR_CURRENT_MAX = 30;

    private static final double SOLAR_POWER_MIN = 0;
    private static final double SOLAR_POWER_MAX = 3000;

    private static final double SOLAR_TEMP_MIN = -40;
    private static final double SOLAR_TEMP_MAX = 90;

    private static final double MOTOR_VOLTAGE_MIN = 0;
    private static final double MOTOR_VOLTAGE_MAX = 100;

    private static final double MOTOR_CURRENT_MIN = 0;
    private static final double MOTOR_CURRENT_MAX = 30;

    private static final int RPM_MIN = 0;
    private static final int RPM_MAX = 5000;

    private static final double FLOW_RATE_MIN = 0;
    private static final double FLOW_RATE_MAX = 500;

    private static final double PRESSURE_MIN = 0;
    private static final double PRESSURE_MAX = 20;

    private static final double WATER_TEMP_MIN = 0;
    private static final double WATER_TEMP_MAX = 80;

    private static final double VIBRATION_MIN = 0;
    private static final double VIBRATION_MAX = 50;

    private static final double OP_HOURS_MIN = 0;
    private static final double OP_HOURS_MAX = 100_000;

    private static final double AMBIENT_TEMP_MIN = -40;
    private static final double AMBIENT_TEMP_MAX = 60;

    private static final double HUMIDITY_MIN = 0;
    private static final double HUMIDITY_MAX = 100;

    /** Maximum clock skew tolerance for future timestamps. */
    private static final long FUTURE_TOLERANCE_SECONDS = 60;

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Validate a telemetry message.
     *
     * @param msg the message to validate (may be {@code null})
     * @return a {@link ValidationResult} indicating pass/fail
     */
    public ValidationResult validate(TelemetryMessage msg) {
        // 1. Null check
        if (msg == null) {
            return ValidationResult.fail("SCHEMA", "Message is null");
        }

        // 2. Schema validation: required top-level fields
        ValidationResult schemaResult = validateSchema(msg);
        if (!schemaResult.isValid()) {
            return schemaResult;
        }

        // 3. Timestamp validation
        ValidationResult tsResult = validateTimestamp(msg.getTimestamp());
        if (!tsResult.isValid()) {
            return tsResult;
        }

        // 4. Range validation
        return validateRanges(msg);
    }

    // ------------------------------------------------------------------ //
    //  Schema checks
    // ------------------------------------------------------------------ //

    private ValidationResult validateSchema(TelemetryMessage msg) {
        if (msg.getPumpId() == null || msg.getPumpId().isBlank()) {
            return ValidationResult.fail("SCHEMA", "pump_id is required");
        }
        if (msg.getTimestamp() == null || msg.getTimestamp().isBlank()) {
            return ValidationResult.fail("SCHEMA", "timestamp is required");
        }
        if (msg.getSolarPanel() == null) {
            return ValidationResult.fail("SCHEMA", "solar_panel is required");
        }
        if (msg.getPump() == null) {
            return ValidationResult.fail("SCHEMA", "pump is required");
        }
        if (msg.getSystem() == null) {
            return ValidationResult.fail("SCHEMA", "system is required");
        }
        if (msg.getSystem().getStatus() == null
                || msg.getSystem().getStatus().isBlank()) {
            return ValidationResult.fail("SCHEMA", "system.status is required");
        }
        return ValidationResult.ok();
    }

    // ------------------------------------------------------------------ //
    //  Timestamp checks
    // ------------------------------------------------------------------ //

    private ValidationResult validateTimestamp(String timestamp) {
        try {
            Instant ts = Instant.parse(timestamp);
            Instant now = Instant.now();
            if (ts.isAfter(now.plus(FUTURE_TOLERANCE_SECONDS, ChronoUnit.SECONDS))) {
                return ValidationResult.fail("TIMESTAMP",
                        "Timestamp is in the future: " + timestamp);
            }
        } catch (DateTimeParseException e) {
            return ValidationResult.fail("TIMESTAMP",
                    "Invalid ISO8601 timestamp: " + timestamp);
        }
        return ValidationResult.ok();
    }

    // ------------------------------------------------------------------ //
    //  Range checks
    // ------------------------------------------------------------------ //

    private ValidationResult validateRanges(TelemetryMessage msg) {
        // --- Solar Panel ---
        SolarPanelTelemetry sp = msg.getSolarPanel();
        ValidationResult r;

        r = checkRange("solar_panel.irradiance_w_m2", sp.getIrradianceWM2(),
                IRRADIANCE_MIN, IRRADIANCE_MAX);
        if (!r.isValid()) return r;

        r = checkRange("solar_panel.voltage_v", sp.getVoltageV(),
                SOLAR_VOLTAGE_MIN, SOLAR_VOLTAGE_MAX);
        if (!r.isValid()) return r;

        r = checkRange("solar_panel.current_a", sp.getCurrentA(),
                SOLAR_CURRENT_MIN, SOLAR_CURRENT_MAX);
        if (!r.isValid()) return r;

        r = checkRange("solar_panel.power_w", sp.getPowerW(),
                SOLAR_POWER_MIN, SOLAR_POWER_MAX);
        if (!r.isValid()) return r;

        r = checkRange("solar_panel.temperature_c", sp.getTemperatureC(),
                SOLAR_TEMP_MIN, SOLAR_TEMP_MAX);
        if (!r.isValid()) return r;

        // --- Pump ---
        PumpTelemetry p = msg.getPump();

        r = checkRange("pump.motor_voltage_v", p.getMotorVoltageV(),
                MOTOR_VOLTAGE_MIN, MOTOR_VOLTAGE_MAX);
        if (!r.isValid()) return r;

        r = checkRange("pump.motor_current_a", p.getMotorCurrentA(),
                MOTOR_CURRENT_MIN, MOTOR_CURRENT_MAX);
        if (!r.isValid()) return r;

        r = checkRange("pump.rpm", p.getRpm(), RPM_MIN, RPM_MAX);
        if (!r.isValid()) return r;

        r = checkRange("pump.flow_rate_lpm", p.getFlowRateLpm(),
                FLOW_RATE_MIN, FLOW_RATE_MAX);
        if (!r.isValid()) return r;

        r = checkRange("pump.discharge_pressure_bar", p.getDischargePressureBar(),
                PRESSURE_MIN, PRESSURE_MAX);
        if (!r.isValid()) return r;

        r = checkRange("pump.suction_pressure_bar", p.getSuctionPressureBar(),
                PRESSURE_MIN, PRESSURE_MAX);
        if (!r.isValid()) return r;

        r = checkRange("pump.water_temperature_c", p.getWaterTemperatureC(),
                WATER_TEMP_MIN, WATER_TEMP_MAX);
        if (!r.isValid()) return r;

        // --- System ---
        SystemTelemetry s = msg.getSystem();

        r = checkRange("system.vibration_mm_s", s.getVibrationMmS(),
                VIBRATION_MIN, VIBRATION_MAX);
        if (!r.isValid()) return r;

        r = checkRange("system.operating_hours", s.getOperatingHours(),
                OP_HOURS_MIN, OP_HOURS_MAX);
        if (!r.isValid()) return r;

        // --- Environment (optional section, only validate if present) ---
        if (msg.getEnvironment() != null) {
            EnvironmentTelemetry e = msg.getEnvironment();

            r = checkRange("environment.ambient_temperature_c",
                    e.getAmbientTemperatureC(), AMBIENT_TEMP_MIN, AMBIENT_TEMP_MAX);
            if (!r.isValid()) return r;

            r = checkRange("environment.humidity_percent",
                    e.getHumidityPercent(), HUMIDITY_MIN, HUMIDITY_MAX);
            if (!r.isValid()) return r;
        }

        return ValidationResult.ok();
    }

    // ------------------------------------------------------------------ //
    //  Helper
    // ------------------------------------------------------------------ //

    private ValidationResult checkRange(String field, double value,
                                         double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return ValidationResult.fail("RANGE",
                    field + " is NaN or Infinite");
        }
        if (value < min || value > max) {
            return ValidationResult.fail("RANGE",
                    field + "=" + value + " out of range [" + min + ", " + max + "]");
        }
        return ValidationResult.ok();
    }
}
