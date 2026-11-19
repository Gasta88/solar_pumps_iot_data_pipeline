# Solar Pumps IoT Data Pipeline

[![CI](https://github.com/Gasta88/solar_pumps_iot_data_pipeline/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Gasta88/solar_pumps_iot_data_pipeline/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Real-time data pipeline for ingesting solar-powered water pump telemetry, validating it with Apache Flink, persisting to TimescaleDB, and visualizing performance in Grafana. Everything runs via Docker Compose.

CI runs the Python and JUnit suites plus a Compose smoke job that boots RabbitMQ and TimescaleDB and asserts the TimescaleDB hypertables. A manual `E2E Pipeline` workflow exercises the full telemetry path.

![System overview](docs/diagrams/system_overview.png)

## Architecture Overview

```mermaid
flowchart LR
    Sim[Python Simulator] --> MQ[(RabbitMQ)]
    MQ --> Flink[Flink JobManager/TaskManager]
    Flink --> TS[(TimescaleDB)]
    Flink --> PM[(Prometheus)]
    TS --> GF[Grafana]
    PM --> GF
```

Printable diagrams live under `docs/diagrams/` and the full breakdown is in `docs/architecture.md`.

![Data flow](docs/diagrams/data_flow.png)

## Quick Start

```bash
cp .env.example .env
make build
make up
```

After `make up` finishes, Grafana (http://localhost:3000) and the rest of the stack come online.

Simulation can be started with the following commands:

```bash
make flink-build
make flink-submit
```

## Detailed Setup

1. **Install prerequisites** – Docker ≥ 24, Docker Compose v2, GNU Make, Java 11 + Maven (for Flink builds), and Python 3.11 if you plan to run helper scripts locally.
2. **Configure secrets** – Copy `.env.example` to `.env`, update the default passwords, and ensure the ports do not clash with existing services. Add any extra pumps to `config/simulator/config.yaml`.
3. **Bring up infrastructure** – `make up` starts RabbitMQ, TimescaleDB, Prometheus, Grafana, Flink JobManager/TaskManager, exporters, and the simulator. Use `make ps` and `make logs` to monitor container health.
4. **Deploy the Flink job** – Build once with `make flink-build`, then (re)submit via `make flink-submit`. The JobManager UI at http://localhost:8081 should show the “Solar Pumps Telemetry Pipeline” job in `RUNNING` state.
5. **Explore data** – `make shell-db` opens `psql` against TimescaleDB. Run `\\i scripts/query_examples.sql` to execute the curated analytics queries. Grafana dashboards are auto-provisioned from `config/grafana`.

## Dashboards & Service Access

| Service | URL |  What to check |
| --- | --- | --- |
| Grafana | http://localhost:3000 |  Pump KPIs, DLQ alerts, solar trends, exporter health. |
| RabbitMQ | http://localhost:15672 |  Queue depth (`telemetry.raw`), connection status, publishers. |
| Flink UI | http://localhost:8081 | Job metrics, checkpoint history, task throughput. |
| Prometheus | http://localhost:9090 | Raw metrics (Flink, RabbitMQ, TimescaleDB exporters). |
| TimescaleDB | change via `.env` | Run SQL, inspect hypertables (`raw_telemetry`, `aggregated_metrics`, `dlq_records`). |

## Screenshots

The architecture diagram above is committed at `docs/diagrams/system_overview.png`, and the data-flow diagram is at `docs/diagrams/data_flow.png`.

Grafana screenshots are captured from a live stack instead of being committed, so they cannot go stale. Run `make up`, `make flink-build`, `make flink-submit`, then open http://localhost:3000 and capture the Operations, Data Quality, or System Health dashboards.

## Simulation Behaviour

`config/simulator/config.yaml` exposes `simulator.always_daytime` (default `true`). When enabled, the solar clock is wrapped into the 06:00–18:00 window so demos always show daytime production. Set it to `false` for a real day/night cycle: irradiance and pump output fall to zero at night and the system reports `STANDBY`.

## Running the Tests

```bash
pip install -r simulator/requirements.txt
pytest simulator/tests

cd flink-jobs/telemetry-processor && mvn test
```

The same commands run in CI, alongside a Compose smoke job that asserts the TimescaleDB schema.

## Triggering Failures for Demos

- **Built-in probabilistic faults** – `config/simulator/config.yaml` controls `simulator.failure_injection.enabled` and `rate`. When enabled, ~5% of messages are mutated (out-of-range, missing fields, structural issues) so DLQ dashboards light up.
- **Deterministic faults via helper script** – `scripts/simulate_failure.py` publishes a single crafted payload to RabbitMQ.

Examples:

```bash
# Dry-run showing the payload
python scripts/simulate_failure.py --pump-id PUMP_001 --failure-type pump_offline --dry-run

# Publish a structural corruption demo
python scripts/simulate_failure.py --pump-id PUMP_002 --failure-type structural_corruption --verbose
```

Supported types: `out_of_range`, `missing_field`, `structural_corruption`, `pump_offline`, `high_vibration`, `stuck_sensor`.

## SQL & Analytics

`scripts/query_examples.sql` contains ready-to-run TimescaleDB queries for:

1. Top 10 pumps by estimated water delivered in the last 24 h.
2. Pumps that have been offline (no telemetry) for more than 5 minutes.
3. Hourly solar power trends (average + peak) over the last 48 h.
4. Error-code and status breakdowns for non-operational readings.

Execute them with:

```bash
psql "postgresql://iot_user:changeme_postgres@localhost:5432/iot_data" -f scripts/query_examples.sql
```

## Troubleshooting

- **Containers stuck in “unhealthy”** – Run `make logs <service>` (e.g., `make logs timescaledb`) and confirm the corresponding port is free. Removing old volumes via `make clean` often resolves stale state.
- **Flink job missing jar** – Ensure `make flink-build` succeeded; the shaded jar must exist under `flink-jobs/telemetry-processor/target` before calling `make flink-submit`.
- **Simulator cannot reach RabbitMQ** – Verify `.env` credentials match `config/simulator/config.yaml` or export `RABBITMQ_*` environment variables before `make up`.
- **Grafana dashboards empty** – Confirm TimescaleDB has rows (`SELECT COUNT(*) FROM raw_telemetry;`) and that Prometheus can scrape exporters (`Status → Targets` in the Prometheus UI).
- **Publishing helper script fails** – RabbitMQ must be listening on the host/port you pass. Override with `--rabbit-host`/`--rabbit-port` when running outside Docker.

## Reference Documentation

- `docs/architecture.md` – Component descriptions, Mermaid diagrams, and PNG exports (`docs/diagrams/*.png`).
- `docs/telemetry_spec.md` – Complete telemetry JSON schema, validation rules, and TimescaleDB table definitions.
- `config/timescaledb/001_init_schema.sql` – Authoritative database DDL.
- `Makefile` – All developer convenience targets (`make help`).

