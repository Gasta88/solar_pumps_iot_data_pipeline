# ============================================================
# Solar Pumps IoT Data Pipeline - Makefile
# ============================================================

COMPOSE = docker compose
ENV_FILE = .env

# Default target
.DEFAULT_GOAL := help

# ---------- Setup ----------

.env: .env.example  ## Create .env from example if it doesn't exist
	@cp .env.example .env
	@echo "✔ Created .env from .env.example — edit it with your secrets."

# ---------- Docker Compose ----------

.PHONY: help build up down logs restart ps

help:  ## Show this help message
	@echo ""
	@echo "Solar Pumps IoT Data Pipeline"
	@echo "=============================="
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""

build: .env  ## Build all Docker images
	$(COMPOSE) --env-file $(ENV_FILE) build

up: .env  ## Start all services in detached mode
	$(COMPOSE) --env-file $(ENV_FILE) up -d --build
	@echo ""
	@echo "Services starting …"
	@echo "  RabbitMQ Management : http://localhost:$${RABBITMQ_MANAGEMENT_PORT:-15672}"
	@echo "  TimescaleDB         : localhost:$${TIMESCALEDB_PORT:-5432}"
	@echo "  Prometheus          : http://localhost:$${PROMETHEUS_PORT:-9090}"
	@echo "  Grafana             : http://localhost:$${GRAFANA_PORT:-3000}"
	@echo "  Flink Dashboard     : http://localhost:$${FLINK_JOBMANAGER_PORT:-8081}"
	@echo ""

down:  ## Stop and remove all services
	$(COMPOSE) --env-file $(ENV_FILE) down

logs:  ## Tail logs for all services (Ctrl-C to stop)
	$(COMPOSE) --env-file $(ENV_FILE) logs -f

restart: down up  ## Restart all services

ps:  ## Show running service status
	$(COMPOSE) --env-file $(ENV_FILE) ps

# ---------- Shell Shortcuts ----------

.PHONY: shell-simulator shell-db shell-rabbitmq shell-grafana shell-flink

shell-simulator:  ## Open a shell in the simulator container
	$(COMPOSE) --env-file $(ENV_FILE) exec simulator /bin/bash

shell-db:  ## Open psql session on TimescaleDB
	$(COMPOSE) --env-file $(ENV_FILE) exec timescaledb psql -U $${POSTGRES_USER:-iot_user} -d $${POSTGRES_DB:-iot_data}

shell-rabbitmq:  ## Open a shell in the RabbitMQ container
	$(COMPOSE) --env-file $(ENV_FILE) exec rabbitmq /bin/bash

shell-grafana:  ## Open a shell in the Grafana container
	$(COMPOSE) --env-file $(ENV_FILE) exec grafana /bin/bash

shell-flink:  ## Open a shell in the Flink JobManager container
	$(COMPOSE) --env-file $(ENV_FILE) exec flink-jobmanager /bin/bash

# ---------- Cleanup ----------

.PHONY: clean nuke

clean: down  ## Stop services and remove volumes
	$(COMPOSE) --env-file $(ENV_FILE) down -v

nuke: clean  ## Full cleanup: volumes, orphan containers, and images
	$(COMPOSE) --env-file $(ENV_FILE) down -v --rmi local --remove-orphans
	@echo "✔ All project containers, volumes, and local images removed."

# ---------- Flink Job ----------

.PHONY: flink-build flink-submit

flink-build:  ## Build Flink telemetry-processor JAR (requires Maven + Java 11)
	cd flink-jobs/telemetry-processor && mvn clean package -DskipTests
	@echo "✔ Flink JAR built at flink-jobs/telemetry-processor/target/"

flink-test:  ## Run Flink unit tests
	cd flink-jobs/telemetry-processor && mvn test
	@echo "✔ Flink tests completed."

flink-submit: flink-build  ## Build and submit Flink job to JobManager
	$(COMPOSE) --env-file $(ENV_FILE) exec flink-jobmanager \
		flink run /opt/flink/job-jars/telemetry-processor-1.0.0.jar
	@echo "✔ Flink job submitted."
