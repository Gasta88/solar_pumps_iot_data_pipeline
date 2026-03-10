"""Prometheus metrics exposed on a configurable HTTP port."""

from __future__ import annotations

from prometheus_client import Counter, Gauge, Histogram, start_http_server

# ---------------------------------------------------------------------------
# Counters
# ---------------------------------------------------------------------------

MESSAGES_PUBLISHED = Counter(
    "simulator_messages_published_total",
    "Total telemetry messages published to RabbitMQ",
    ["pump_id"],
)

FAULTS_INJECTED = Counter(
    "simulator_faults_injected_total",
    "Total fault-injected messages by fault type",
    ["pump_id", "fault_type"],
)

PUBLISH_ERRORS = Counter(
    "simulator_publish_errors_total",
    "Total RabbitMQ publish failures",
    ["pump_id"],
)

# ---------------------------------------------------------------------------
# Gauges
# ---------------------------------------------------------------------------

IRRADIANCE_GAUGE = Gauge(
    "simulator_irradiance_w_m2",
    "Latest solar irradiance reading (W/m2)",
    ["pump_id"],
)

FLOW_RATE_GAUGE = Gauge(
    "simulator_flow_rate_lpm",
    "Latest pump flow rate (litres per minute)",
    ["pump_id"],
)

ACTIVE_PUMPS = Gauge(
    "simulator_active_pump_threads",
    "Number of currently running pump threads",
)

# ---------------------------------------------------------------------------
# Histograms
# ---------------------------------------------------------------------------

PUBLISH_LATENCY = Histogram(
    "simulator_publish_latency_seconds",
    "Time taken to publish a single message",
    ["pump_id"],
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5),
)


# ---------------------------------------------------------------------------
# Server bootstrap
# ---------------------------------------------------------------------------

def start_metrics_server(port: int = 8001) -> None:
    """Start the Prometheus metrics HTTP server in a daemon thread."""
    start_http_server(port)
