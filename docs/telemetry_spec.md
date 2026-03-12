# Telemetry & Data Model Specification

This document is the single source of truth for sensor payloads, validation constraints, and the TimescaleDB schema used by the Solar Pumps IoT pipeline.

## Message Envelope

Each reading published to RabbitMQ is a JSON document containing the sections below. All fields are required unless marked optional.

| Field | Type | Description |
| --- | --- | --- |
| `pump_id` | `string` | Identifier that follows the `PUMP_###` convention. |
| `timestamp` | `string (ISO-8601, UTC)` | Capture time with millisecond precision; Flink rejects payloads >60 s in the future. |
| `location` | `object` | Static GPS coordinates of the pump (see table below). |
| `solar_panel` | `object` | Instantaneous solar panel readings (see table). |
| `pump` | `object` | Motor + hydraulic metrics (see table). |
| `system` | `object` | Operational metadata (status, hours, vibration). |
| `environment` | `object` (optional) | Ambient temperature + humidity. |

### Location

| Field | Type | Unit | Notes |
| --- | --- | --- | --- |
| `latitude` | float | degrees | Range: −90 to 90. |
| `longitude` | float | degrees | Range: −180 to 180. |
| `region` | string | — | Human-friendly label (e.g., `Nairobi`). |

### Solar Panel

| Field | Type | Unit | Allowed range |
| --- | --- | --- | --- |
| `irradiance_w_m2` | float | W/m² | 0 – 1,500 |
| `voltage_v` | float | volts | 0 – 100 |
| `current_a` | float | amps | 0 – 30 |
| `power_w` | float | watts | 0 – 3,000 |
| `temperature_c` | float | °C | −40 – 90 |

### Pump

| Field | Type | Unit | Allowed range |
| --- | --- | --- | --- |
| `motor_voltage_v` | float | volts | 0 – 100 |
| `motor_current_a` | float | amps | 0 – 30 |
| `rpm` | int | RPM | 0 – 5,000 |
| `flow_rate_lpm` | float | L/min | 0 – 500 |
| `discharge_pressure_bar` | float | bar | 0 – 20 |
| `suction_pressure_bar` | float | bar | 0 – 20 |
| `water_temperature_c` | float | °C | 0 – 80 |

### System

| Field | Type | Unit | Allowed range |
| --- | --- | --- | --- |
| `operating_hours` | float | hours | 0 – 100,000 |
| `vibration_mm_s` | float | mm/s | 0 – 50 |
| `error_code` | int | — | 0 = healthy. 9000+ reserved for injected demo faults. |
| `status` | string | — | `OPERATIONAL`, `STANDBY`, `DEGRADED`, `ALERT`, `FAULTED`. |

### Environment (optional)

| Field | Type | Unit | Allowed range |
| --- | --- | --- | --- |
| `ambient_temperature_c` | float | °C | −40 – 60 |
| `humidity_percent` | float | % | 0 – 100 |

## Validation Rules

Flink’s `TelemetryValidator` enforces:

1. **Schema** – `pump_id`, `timestamp`, and nested sections (`solar_panel`, `pump`, `system`) must exist and contain non-empty values.
2. **Timestamp sanity** – ISO-8601 strings are parsed in UTC; payloads more than 60 seconds in the future are rejected.
3. **Range checks** – Numeric fields must fall within the bounds listed in the tables above and cannot be `NaN`/`Infinity`.
4. **Dead-letter queue (DLQ)** – Messages failing schema/range checks are serialized into `dlq_records` with error context so dashboards can highlight bad pumps.

## TimescaleDB Schema

### `raw_telemetry`

Stores every validated reading. Key columns: `time` (TIMESTAMPTZ, hypertable partition), `pump_id`, geolocation, all solar/pump/system/environment metrics, plus indexes on `(pump_id, time DESC)` for fast lookups.

### `aggregated_metrics`

Windowed statistics emitted by Flink (1-minute and 5-minute windows). Columns: `window_start`, `window_end`, `window_size`, `pump_id`, averages/min/max for flow, solar power, vibration, and `record_count`. Primary key: `(window_start, pump_id, window_size)`.

### `dlq_records`

Captures invalid payloads. Columns: `error_timestamp`, `pump_id`, `error_type` (`SCHEMA`, `RANGE`, `TIMESTAMP`, etc.), `error_reason`, `original_payload`. Indexed by `error_type` and `pump_id` for rapid Grafana panels.

## Example Payloads

### Valid reading

```json
{
  "pump_id": "PUMP_001",
  "timestamp": "2026-03-12T09:15:27.532Z",
  "location": {"latitude": -1.286389, "longitude": 36.817223, "region": "Nairobi"},
  "solar_panel": {
    "irradiance_w_m2": 845.2,
    "voltage_v": 47.9,
    "current_a": 8.9,
    "power_w": 426.3,
    "temperature_c": 51.4
  },
  "pump": {
    "motor_voltage_v": 46.1,
    "motor_current_a": 8.6,
    "rpm": 2420,
    "flow_rate_lpm": 118.4,
    "discharge_pressure_bar": 3.1,
    "suction_pressure_bar": 0.32,
    "water_temperature_c": 23.4
  },
  "system": {
    "operating_hours": 1320.43,
    "vibration_mm_s": 2.1,
    "error_code": 0,
    "status": "OPERATIONAL"
  },
  "environment": {
    "ambient_temperature_c": 29.8,
    "humidity_percent": 48.1
  }
}
```

### Injected failure (missing pump block)

```json
{
  "pump_id": "PUMP_002",
  "timestamp": "2026-03-12T09:15:29.002Z",
  "location": {"latitude": -0.091702, "longitude": 34.768017, "region": "Kisumu"},
  "solar_panel": {"irradiance_w_m2": 0.0, "voltage_v": 0.0, "current_a": 0.0, "power_w": 0.0, "temperature_c": 19.7},
  "system": {"operating_hours": 1488.02, "vibration_mm_s": 0.0, "error_code": 9010, "status": "FAULTED"},
  "environment": {"ambient_temperature_c": 24.4, "humidity_percent": 61.0}
}
```

The second payload is rejected (missing `pump`) and lands in `dlq_records` with `error_type = 'SCHEMA'`.

### DLQ row example

```sql
SELECT error_timestamp, pump_id, error_type, error_reason
FROM dlq_records
ORDER BY error_timestamp DESC
LIMIT 1;
```

```text
 error_timestamp      | pump_id  | error_type |                   error_reason
----------------------+----------+------------+------------------------------------------------
 2026-03-12 09:15:29Z | PUMP_002 | SCHEMA     | pump is required
```

## Related Assets

- `scripts/simulate_failure.py` – Publish deterministic failure payloads for demos.
- `scripts/query_examples.sql` – Pre-built TimescaleDB analytics queries (water delivery, downtime, solar trends, error codes).
- `docs/architecture.md` – High-level system diagram and technology rationale.
