-- Solar Pumps IoT Data Pipeline - Handy analysis queries
-- ------------------------------------------------------
-- Usage:
--   psql "postgresql://iot_user:changeme_postgres@localhost:5432/iot_data" \
--        -f scripts/query_examples.sql

\echo '1) Top 10 pumps by estimated water delivered in the last 24h'
WITH delivery AS (
    SELECT
        pump_id,
        -- Flow rate (L/min) * 5-second sampling interval = liters per record
        flow_rate_lpm * (5.0 / 60.0) AS liters_delivered
    FROM raw_telemetry
    WHERE time >= NOW() - INTERVAL '24 hours'
)
SELECT pump_id,
       ROUND(SUM(liters_delivered), 2) AS total_liters,
       ROUND(SUM(liters_delivered) / 1000.0, 2) AS cubic_meters
FROM delivery
GROUP BY pump_id
ORDER BY total_liters DESC
LIMIT 10;

\echo ''
\echo '2) Pumps that have been offline (no telemetry) for more than 5 minutes'
SELECT pump_id,
       MAX(time) AS last_seen,
       NOW() - MAX(time) AS inactivity
FROM raw_telemetry
GROUP BY pump_id
HAVING MAX(time) < NOW() - INTERVAL '5 minutes'
ORDER BY inactivity DESC;

\echo ''
\echo '3) Hourly solar power trend (average and peak) for the last 48h'
SELECT DATE_TRUNC('hour', time) AS hour_bucket,
       pump_id,
       ROUND(AVG(solar_power_w), 2) AS avg_power_w,
       ROUND(MAX(solar_power_w), 2) AS peak_power_w
FROM raw_telemetry
WHERE time >= NOW() - INTERVAL '48 hours'
GROUP BY hour_bucket, pump_id
ORDER BY hour_bucket, pump_id;

\echo ''
\echo '4) Error code analysis (non-OPERATIONAL samples)'
SELECT error_code,
       status,
       COUNT(*) AS occurrences,
       ARRAY_AGG(DISTINCT pump_id ORDER BY pump_id)[:5] AS sample_pumps,
       MAX(time) AS last_seen
FROM raw_telemetry
WHERE error_code <> 0 OR status <> 'OPERATIONAL'
GROUP BY error_code, status
ORDER BY occurrences DESC;
