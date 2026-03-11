package com.solarpumps.flink.validation;

import com.solarpumps.flink.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link TelemetryValidator}.
 */
class TelemetryValidatorTest {

    private TelemetryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TelemetryValidator();
    }

    // ------------------------------------------------------------------ //
    //  Helper: build a fully-valid TelemetryMessage
    // ------------------------------------------------------------------ //

    private TelemetryMessage validMessage() {
        TelemetryMessage msg = new TelemetryMessage();
        msg.setPumpId("PUMP_001");
        msg.setTimestamp(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

        LocationTelemetry loc = new LocationTelemetry(-1.286389, 36.817223, "Nairobi");
        msg.setLocation(loc);

        SolarPanelTelemetry sp = new SolarPanelTelemetry(
                850.0, 46.5, 8.2, 381.3, 42.0);
        msg.setSolarPanel(sp);

        PumpTelemetry pump = new PumpTelemetry(
                44.0, 7.5, 2500, 120.0, 3.2, 0.3, 24.0);
        msg.setPump(pump);

        SystemTelemetry sys = new SystemTelemetry(1234.5, 2.1, 0, "OPERATIONAL");
        msg.setSystem(sys);

        EnvironmentTelemetry env = new EnvironmentTelemetry(32.0, 55.0);
        msg.setEnvironment(env);

        return msg;
    }

    // ================================================================== //
    //  Happy path
    // ================================================================== //

    @Test
    @DisplayName("Valid message passes all checks")
    void validMessagePasses() {
        ValidationResult result = validator.validate(validMessage());
        assertTrue(result.isValid());
        assertNull(result.getErrorReason());
        assertNull(result.getErrorType());
    }

    @Test
    @DisplayName("Valid message without environment section passes")
    void validMessageWithoutEnvironmentPasses() {
        TelemetryMessage msg = validMessage();
        msg.setEnvironment(null);
        assertTrue(validator.validate(msg).isValid());
    }

    // ================================================================== //
    //  Schema validation
    // ================================================================== //

    @Nested
    @DisplayName("Schema validation")
    class SchemaValidation {

        @Test
        @DisplayName("Null message fails")
        void nullMessage() {
            ValidationResult r = validator.validate(null);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
        }

        @Test
        @DisplayName("Missing pump_id fails")
        void missingPumpId() {
            TelemetryMessage msg = validMessage();
            msg.setPumpId(null);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
            assertTrue(r.getErrorReason().contains("pump_id"));
        }

        @Test
        @DisplayName("Blank pump_id fails")
        void blankPumpId() {
            TelemetryMessage msg = validMessage();
            msg.setPumpId("   ");
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
        }

        @Test
        @DisplayName("Missing timestamp fails")
        void missingTimestamp() {
            TelemetryMessage msg = validMessage();
            msg.setTimestamp(null);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
            assertTrue(r.getErrorReason().contains("timestamp"));
        }

        @Test
        @DisplayName("Missing solar_panel fails")
        void missingSolarPanel() {
            TelemetryMessage msg = validMessage();
            msg.setSolarPanel(null);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
            assertTrue(r.getErrorReason().contains("solar_panel"));
        }

        @Test
        @DisplayName("Missing pump fails")
        void missingPump() {
            TelemetryMessage msg = validMessage();
            msg.setPump(null);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
            assertTrue(r.getErrorReason().contains("pump"));
        }

        @Test
        @DisplayName("Missing system fails")
        void missingSystem() {
            TelemetryMessage msg = validMessage();
            msg.setSystem(null);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
            assertTrue(r.getErrorReason().contains("system"));
        }

        @Test
        @DisplayName("Blank system.status fails")
        void blankSystemStatus() {
            TelemetryMessage msg = validMessage();
            msg.getSystem().setStatus("  ");
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
            assertTrue(r.getErrorReason().contains("system.status"));
        }

        @Test
        @DisplayName("Null system.status fails")
        void nullSystemStatus() {
            TelemetryMessage msg = validMessage();
            msg.getSystem().setStatus(null);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("SCHEMA", r.getErrorType());
        }
    }

    // ================================================================== //
    //  Timestamp validation
    // ================================================================== //

    @Nested
    @DisplayName("Timestamp validation")
    class TimestampValidation {

        @Test
        @DisplayName("Invalid ISO8601 format fails")
        void invalidFormat() {
            TelemetryMessage msg = validMessage();
            msg.setTimestamp("2024-13-01 25:00:00");
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("TIMESTAMP", r.getErrorType());
            assertTrue(r.getErrorReason().contains("Invalid ISO8601"));
        }

        @Test
        @DisplayName("Timestamp in the far future fails")
        void futureTimestamp() {
            TelemetryMessage msg = validMessage();
            msg.setTimestamp(
                    Instant.now().plus(10, ChronoUnit.MINUTES).toString());
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("TIMESTAMP", r.getErrorType());
            assertTrue(r.getErrorReason().contains("future"));
        }

        @Test
        @DisplayName("Timestamp within tolerance passes")
        void withinTolerance() {
            TelemetryMessage msg = validMessage();
            // 30 seconds in the future — within the 60-second tolerance
            msg.setTimestamp(
                    Instant.now().plus(30, ChronoUnit.SECONDS).toString());
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("Past timestamp passes")
        void pastTimestamp() {
            TelemetryMessage msg = validMessage();
            msg.setTimestamp(
                    Instant.now().minus(1, ChronoUnit.HOURS).toString());
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("Garbage string fails")
        void garbageTimestamp() {
            TelemetryMessage msg = validMessage();
            msg.setTimestamp("not-a-date");
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("TIMESTAMP", r.getErrorType());
        }
    }

    // ================================================================== //
    //  Range validation — Solar Panel
    // ================================================================== //

    @Nested
    @DisplayName("Range validation — Solar Panel")
    class SolarPanelRanges {

        @Test
        @DisplayName("Negative irradiance fails")
        void negativeIrradiance() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setIrradianceWM2(-1.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
            assertTrue(r.getErrorReason().contains("irradiance"));
        }

        @Test
        @DisplayName("Irradiance above 1500 fails")
        void irradianceTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setIrradianceWM2(1501.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Zero irradiance passes (nighttime)")
        void zeroIrradiance() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setIrradianceWM2(0.0);
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("Boundary irradiance 1500 passes")
        void boundaryIrradiance() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setIrradianceWM2(1500.0);
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("Solar power above 3000 fails")
        void solarPowerTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setPowerW(3001.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Solar temperature below -40 fails")
        void solarTempTooLow() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setTemperatureC(-41.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("NaN solar voltage fails")
        void nanVoltage() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setVoltageV(Double.NaN);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
            assertTrue(r.getErrorReason().contains("NaN"));
        }

        @Test
        @DisplayName("Infinite solar current fails")
        void infiniteCurrent() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setCurrentA(Double.POSITIVE_INFINITY);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }
    }

    // ================================================================== //
    //  Range validation — Pump
    // ================================================================== //

    @Nested
    @DisplayName("Range validation — Pump")
    class PumpRanges {

        @Test
        @DisplayName("Negative RPM fails")
        void negativeRpm() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setRpm(-100);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("RPM above 5000 fails")
        void rpmTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setRpm(5001);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Flow rate above 500 fails")
        void flowRateTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setFlowRateLpm(501.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Negative discharge pressure fails")
        void negativePressure() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setDischargePressureBar(-0.1);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Water temperature above 80 fails")
        void waterTempTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setWaterTemperatureC(81.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Zero flow rate passes (standby)")
        void zeroFlowRate() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setFlowRateLpm(0.0);
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("Boundary values pass")
        void boundaryValues() {
            TelemetryMessage msg = validMessage();
            msg.getPump().setMotorVoltageV(100.0);
            msg.getPump().setMotorCurrentA(30.0);
            msg.getPump().setRpm(5000);
            msg.getPump().setFlowRateLpm(500.0);
            msg.getPump().setDischargePressureBar(20.0);
            msg.getPump().setSuctionPressureBar(20.0);
            msg.getPump().setWaterTemperatureC(80.0);
            assertTrue(validator.validate(msg).isValid());
        }
    }

    // ================================================================== //
    //  Range validation — System
    // ================================================================== //

    @Nested
    @DisplayName("Range validation — System")
    class SystemRanges {

        @Test
        @DisplayName("Vibration above 50 fails")
        void vibrationTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getSystem().setVibrationMmS(51.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Negative operating hours fails")
        void negativeOperatingHours() {
            TelemetryMessage msg = validMessage();
            msg.getSystem().setOperatingHours(-1.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Operating hours above 100000 fails")
        void operatingHoursTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getSystem().setOperatingHours(100_001.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }
    }

    // ================================================================== //
    //  Range validation — Environment
    // ================================================================== //

    @Nested
    @DisplayName("Range validation — Environment")
    class EnvironmentRanges {

        @Test
        @DisplayName("Ambient temperature below -40 fails")
        void ambientTempTooLow() {
            TelemetryMessage msg = validMessage();
            msg.getEnvironment().setAmbientTemperatureC(-41.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Ambient temperature above 60 fails")
        void ambientTempTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getEnvironment().setAmbientTemperatureC(61.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Humidity above 100 fails")
        void humidityTooHigh() {
            TelemetryMessage msg = validMessage();
            msg.getEnvironment().setHumidityPercent(101.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Negative humidity fails")
        void negativeHumidity() {
            TelemetryMessage msg = validMessage();
            msg.getEnvironment().setHumidityPercent(-1.0);
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            assertEquals("RANGE", r.getErrorType());
        }

        @Test
        @DisplayName("Boundary environment values pass")
        void boundaryValues() {
            TelemetryMessage msg = validMessage();
            msg.getEnvironment().setAmbientTemperatureC(-40.0);
            msg.getEnvironment().setHumidityPercent(100.0);
            assertTrue(validator.validate(msg).isValid());
        }
    }

    // ================================================================== //
    //  Edge cases
    // ================================================================== //

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("All zero sensor values pass (standby mode)")
        void allZeroValues() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setIrradianceWM2(0);
            msg.getSolarPanel().setVoltageV(0);
            msg.getSolarPanel().setCurrentA(0);
            msg.getSolarPanel().setPowerW(0);
            msg.getSolarPanel().setTemperatureC(0);
            msg.getPump().setMotorVoltageV(0);
            msg.getPump().setMotorCurrentA(0);
            msg.getPump().setRpm(0);
            msg.getPump().setFlowRateLpm(0);
            msg.getPump().setDischargePressureBar(0);
            msg.getPump().setSuctionPressureBar(0);
            msg.getPump().setWaterTemperatureC(0);
            msg.getSystem().setVibrationMmS(0);
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("Maximum valid sensor values pass")
        void maxValidValues() {
            TelemetryMessage msg = validMessage();
            msg.getSolarPanel().setIrradianceWM2(1500);
            msg.getSolarPanel().setVoltageV(100);
            msg.getSolarPanel().setCurrentA(30);
            msg.getSolarPanel().setPowerW(3000);
            msg.getSolarPanel().setTemperatureC(90);
            msg.getPump().setMotorVoltageV(100);
            msg.getPump().setMotorCurrentA(30);
            msg.getPump().setRpm(5000);
            msg.getPump().setFlowRateLpm(500);
            msg.getPump().setDischargePressureBar(20);
            msg.getPump().setSuctionPressureBar(20);
            msg.getPump().setWaterTemperatureC(80);
            msg.getSystem().setVibrationMmS(50);
            msg.getSystem().setOperatingHours(100_000);
            msg.getEnvironment().setAmbientTemperatureC(60);
            msg.getEnvironment().setHumidityPercent(100);
            assertTrue(validator.validate(msg).isValid());
        }

        @Test
        @DisplayName("First failed check short-circuits validation")
        void firstFailureStops() {
            TelemetryMessage msg = validMessage();
            msg.setPumpId(null);                               // fails schema
            msg.getSolarPanel().setIrradianceWM2(-999);        // also out of range
            ValidationResult r = validator.validate(msg);
            assertFalse(r.isValid());
            // Should report the schema error first, not the range error
            assertEquals("SCHEMA", r.getErrorType());
        }
    }
}
