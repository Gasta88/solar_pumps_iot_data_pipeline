# Solar Pumps IoT Data Pipeline

Real-time reference implementation for ingesting solar-powered water pump telemetry, validating it with Apache Flink, persisting to TimescaleDB, and visualizing performance in Grafana. Everything runs via Docker Compose for a frictionless developer experience.

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

## Quick Start (3 commands)

```bash
cp .env.example .env
docker compose pull
make up
```

After `make up` finishes, Grafana (http://localhost:3000) and the rest of the stack come online with sample simulator data.

## Detailed Setup

1. **Install prerequisites** – Docker ≥ 24, Docker Compose v2, GNU Make, Java 11 + Maven (for Flink builds), and Python 3.11 if you plan to run helper scripts locally.
2. **Configure secrets** – Copy `.env.example` to `.env`, update the default passwords, and ensure the ports do not clash with existing services. Add any extra pumps to `config/simulator/config.yaml`.
3. **Bring up infrastructure** – `make up` starts RabbitMQ, TimescaleDB, Prometheus, Grafana, Flink JobManager/TaskManager, exporters, and the simulator. Use `make ps` and `make logs` to monitor container health.
4. **Deploy the Flink job** – Build once with `make flink-build`, then (re)submit via `make flink-submit`. The JobManager UI at http://localhost:8081 should show the “Solar Pumps Telemetry Pipeline” job in `RUNNING` state.
5. **Explore data** – `make shell-db` opens `psql` against TimescaleDB. Run `\\i scripts/query_examples.sql` to execute the curated analytics queries. Grafana dashboards are auto-provisioned from `config/grafana`.

## Dashboards & Service Access

| Service | URL | Default credentials | What to check |
| --- | --- | --- | --- |
| Grafana | http://localhost:3000 | `admin` / `changeme_grafana` | Pump KPIs, DLQ alerts, solar trends, exporter health. |
| RabbitMQ | http://localhost:15672 | `iot_user` / `changeme_rabbitmq` | Queue depth (`telemetry.raw`), connection status, publishers. |
| Flink UI | http://localhost:8081 | n/a | Job metrics, checkpoint history, task throughput. |
| Prometheus | http://localhost:9090 | n/a | Raw metrics (Flink, RabbitMQ, TimescaleDB exporters). |
| TimescaleDB | `psql postgresql://iot_user:changeme_postgres@localhost:5432/iot_data` | change via `.env` | Run SQL, inspect hypertables (`raw_telemetry`, `aggregated_metrics`, `dlq_records`). |

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

## License

MIT
