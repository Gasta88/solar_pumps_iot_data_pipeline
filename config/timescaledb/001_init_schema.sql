-- ============================================================
-- TimescaleDB Schema for Solar Pumps IoT Data Pipeline
-- Run once during initial setup.
-- ============================================================

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- -------------------------------------------------------
-- raw_telemetry: stores every validated sensor reading
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS raw_telemetry (
    time                    TIMESTAMPTZ     NOT NULL,
    pump_id                 TEXT            NOT NULL,
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    region                  TEXT,

    -- Solar panel
    irradiance_w_m2         DOUBLE PRECISION,
    solar_voltage_v         DOUBLE PRECISION,
    solar_current_a         DOUBLE PRECISION,
    solar_power_w           DOUBLE PRECISION,
    solar_temperature_c     DOUBLE PRECISION,

    -- Pump / motor
    motor_voltage_v         DOUBLE PRECISION,
    motor_current_a         DOUBLE PRECISION,
    rpm                     INTEGER,
    flow_rate_lpm           DOUBLE PRECISION,
    discharge_pressure_bar  DOUBLE PRECISION,
    suction_pressure_bar    DOUBLE PRECISION,
    water_temperature_c     DOUBLE PRECISION,

    -- System
    operating_hours         DOUBLE PRECISION,
    vibration_mm_s          DOUBLE PRECISION,
    error_code              INTEGER         DEFAULT 0,
    status                  TEXT,

    -- Environment
    ambient_temperature_c   DOUBLE PRECISION,
    humidity_percent        DOUBLE PRECISION
);

-- Convert to hypertable (chunk interval = 1 day)
SELECT create_hypertable('raw_telemetry', 'time',
       if_not_exists => TRUE,
       chunk_time_interval => INTERVAL '1 day');

-- Index for fast pump_id lookups
CREATE INDEX IF NOT EXISTS idx_raw_telemetry_pump_id
    ON raw_telemetry (pump_id, time DESC);

-- -------------------------------------------------------
-- aggregated_metrics: windowed statistics per pump
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS aggregated_metrics (
    window_start        TIMESTAMPTZ     NOT NULL,
    window_end          TIMESTAMPTZ     NOT NULL,
    window_size         TEXT            NOT NULL,   -- '1m' or '5m'
    pump_id             TEXT            NOT NULL,

    avg_flow_rate       DOUBLE PRECISION,
    max_flow_rate       DOUBLE PRECISION,
    min_flow_rate       DOUBLE PRECISION,

    avg_solar_power     DOUBLE PRECISION,
    max_solar_power     DOUBLE PRECISION,
    min_solar_power     DOUBLE PRECISION,

    avg_vibration       DOUBLE PRECISION,
    max_vibration       DOUBLE PRECISION,
    min_vibration       DOUBLE PRECISION,

    record_count        BIGINT,

    PRIMARY KEY (window_start, pump_id, window_size)
);

-- Convert to hypertable
SELECT create_hypertable('aggregated_metrics', 'window_start',
       if_not_exists => TRUE,
       chunk_time_interval => INTERVAL '1 day');

CREATE INDEX IF NOT EXISTS idx_agg_metrics_pump_window
    ON aggregated_metrics (pump_id, window_size, window_start DESC);

-- -------------------------------------------------------
-- dlq_records: stores dead-letter-queue messages for
-- data-quality monitoring and debugging.
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS dlq_records (
    error_timestamp     TIMESTAMPTZ     NOT NULL,
    pump_id             TEXT,
    error_type          TEXT            NOT NULL,
    error_reason        TEXT            NOT NULL,
    original_payload    TEXT
);

-- Convert to hypertable (chunk interval = 1 day)
SELECT create_hypertable('dlq_records', 'error_timestamp',
       if_not_exists => TRUE,
       chunk_time_interval => INTERVAL '1 day');

-- Index for quick error-type breakdowns
CREATE INDEX IF NOT EXISTS idx_dlq_records_error_type
    ON dlq_records (error_type, error_timestamp DESC);

-- Index for pump-level DLQ lookups
CREATE INDEX IF NOT EXISTS idx_dlq_records_pump_id
    ON dlq_records (pump_id, error_timestamp DESC);
