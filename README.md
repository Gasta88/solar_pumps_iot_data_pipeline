# Solar Pumps IoT Data Pipeline

Real-time data pipeline for monitoring and analysing solar-powered water pump systems. The project simulates IoT sensor telemetry, streams it through a message broker, processes it with Apache Flink, stores results in TimescaleDB, and visualises everything in Grafana.

---

## Architecture

```
┌────────────┐     ┌──────────┐     ┌───────────────┐     ┌─────────────┐
│  Simulator  │────▶│ RabbitMQ │────▶│  Apache Flink │────▶│ TimescaleDB │
│  (Python)   │     │  Broker  │     │  (Stream Proc)│     │  (Storage)  │
└────────────┘     └──────────┘     └───────────────┘     └──────┬──────┘
                                                                  │
                                            ┌─────────────┐      │
                                            │  Prometheus  │◀─────┘
                                            └──────┬──────┘
                                                   │
                                            ┌──────▼──────┐
                                            │   Grafana    │
                                            │ (Dashboards) │
                                            └─────────────┘
```

## Services

| Service            | Image                              | Port(s)       | Purpose                        |
| ------------------ | ---------------------------------- | ------------- | ------------------------------ |
| RabbitMQ           | `rabbitmq:3.12-management`         | 5672 / 15672  | Message broker                 |
| TimescaleDB        | `timescale/timescaledb:2.13.1-pg15`| 5432          | Time-series database           |
| Prometheus         | `prom/prometheus:v2.48.1`          | 9090          | Metrics collection             |
| Grafana            | `grafana/grafana:10.2.3`           | 3000          | Dashboards & visualisation     |
| Flink JobManager   | `flink:1.18-java11`               | 8081          | Stream processing coordinator  |
| Flink TaskManager  | `flink:1.18-java11`               | —             | Stream processing worker       |
| Simulator          | Custom Python 3.11                 | —             | IoT telemetry generator        |

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) ≥ 24.0
- [Docker Compose](https://docs.docker.com/compose/) ≥ 2.20 (V2 plugin)
- GNU Make

## Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/Gasta88/solar_pumps_iot_data_pipeline.git
cd solar_pumps_iot_data_pipeline

# 2. Create your environment file
cp .env.example .env    # then edit .env with your own secrets

# 3. Start all services
make up

# 4. Check service status
make ps

# 5. Open the dashboards
#    Grafana   → http://localhost:3000
#    RabbitMQ  → http://localhost:15672
#    Flink     → http://localhost:8081
#    Prometheus→ http://localhost:9090
```

## Makefile Targets

```
make help              Show available targets
make build             Build all Docker images
make up                Start all services (detached)
make down              Stop and remove services
make logs              Tail service logs
make restart           Restart all services
make ps                Show running containers
make shell-simulator   Shell into the simulator container
make shell-db          Open psql on TimescaleDB
make shell-rabbitmq    Shell into RabbitMQ container
make shell-grafana     Shell into Grafana container
make shell-flink       Shell into Flink JobManager
make clean             Stop services and remove volumes
make nuke              Full cleanup (volumes + images)
```

## Project Structure

```
.
├── config/
│   ├── flink/                   # Flink configuration
│   ├── grafana/
│   │   ├── dashboards/          # Grafana dashboard JSON files
│   │   └── provisioning/        # Auto-provisioning configs
│   ├── prometheus/
│   │   └── prometheus.yml       # Prometheus scrape config
│   └── timescaledb/
│       └── 001_init_schema.sql  # TimescaleDB schema (raw_telemetry, aggregated_metrics)
├── data/                        # Persistent volume mount points (git-ignored)
├── flink-jobs/
│   └── telemetry-processor/     # Apache Flink streaming job (Java 11, Maven)
│       ├── pom.xml
│       └── src/
│           ├── main/java/com/solarpumps/flink/
│           │   ├── TelemetryPipelineJob.java       # Main job entry point
│           │   ├── aggregation/                     # Windowed aggregation functions
│           │   ├── metrics/                         # Prometheus metrics helpers
│           │   ├── model/                           # POJOs (TelemetryMessage, DLQMessage, etc.)
│           │   ├── serialization/                   # JSON (de)serializers for RabbitMQ
│           │   ├── sink/                            # TimescaleDB JDBC sink factories
│           │   └── validation/                      # TelemetryValidator (schema + range)
│           └── test/java/com/solarpumps/flink/
│               └── validation/
│                   └── TelemetryValidatorTest.java  # 42 JUnit 5 tests
├── simulator/
│   ├── Dockerfile               # Python 3.11 simulator image
│   ├── __init__.py
│   └── requirements.txt
├── .env.example                 # Environment variable template
├── docker-compose.yml           # All service definitions
├── Makefile                     # Developer automation
└── README.md                    # ← You are here
```

## Environment Variables

See [`.env.example`](.env.example) for the full list. Key variables:

| Variable                  | Default              | Description                    |
| ------------------------- | -------------------- | ------------------------------ |
| `POSTGRES_USER`           | `iot_user`           | TimescaleDB username           |
| `POSTGRES_PASSWORD`       | `changeme_postgres`  | TimescaleDB password           |
| `POSTGRES_DB`             | `iot_data`           | TimescaleDB database name      |
| `RABBITMQ_DEFAULT_USER`   | `iot_user`           | RabbitMQ username              |
| `RABBITMQ_DEFAULT_PASS`   | `changeme_rabbitmq`  | RabbitMQ password              |
| `GF_SECURITY_ADMIN_USER`  | `admin`              | Grafana admin username         |
| `GF_SECURITY_ADMIN_PASSWORD`| `changeme_grafana` | Grafana admin password         |

## Roadmap

- [x] **Session 0** — Project bootstrap & Docker Compose skeleton
- [x] **Session 1** — IoT simulator with realistic telemetry generation
- [x] **Session 2** — Flink stream processing: validation, routing, TimescaleDB sinks, aggregations
- [ ] **Session 3** — RabbitMQ integration & message schema
- [ ] **Session 4** — TimescaleDB schema & continuous aggregates
- [ ] **Session 5** — Grafana dashboards & alerting
- [ ] **Session 6** — End-to-end testing & documentation

## License

MIT
