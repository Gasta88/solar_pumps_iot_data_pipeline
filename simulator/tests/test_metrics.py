"""Tests for simulator.metrics — Prometheus metrics definitions."""

from __future__ import annotations

from simulator.metrics import (
    ACTIVE_PUMPS,
    FAULTS_INJECTED,
    FLOW_RATE_GAUGE,
    IRRADIANCE_GAUGE,
    MESSAGES_PUBLISHED,
    PUBLISH_ERRORS,
    PUBLISH_LATENCY,
)


class TestMetricsDefined:
    """Smoke-test that all expected Prometheus metrics exist."""

    def test_counters_exist(self) -> None:
        assert MESSAGES_PUBLISHED is not None
        assert FAULTS_INJECTED is not None
        assert PUBLISH_ERRORS is not None

    def test_gauges_exist(self) -> None:
        assert IRRADIANCE_GAUGE is not None
        assert FLOW_RATE_GAUGE is not None
        assert ACTIVE_PUMPS is not None

    def test_histogram_exists(self) -> None:
        assert PUBLISH_LATENCY is not None

    def test_counter_increment(self) -> None:
        before = MESSAGES_PUBLISHED.labels(pump_id="PUMP_TEST")._value.get()
        MESSAGES_PUBLISHED.labels(pump_id="PUMP_TEST").inc()
        after = MESSAGES_PUBLISHED.labels(pump_id="PUMP_TEST")._value.get()
        assert after == before + 1

    def test_gauge_set(self) -> None:
        IRRADIANCE_GAUGE.labels(pump_id="PUMP_TEST").set(999.9)
        val = IRRADIANCE_GAUGE.labels(pump_id="PUMP_TEST")._value.get()
        assert val == 999.9
