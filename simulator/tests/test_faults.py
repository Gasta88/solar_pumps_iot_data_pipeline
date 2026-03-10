"""Tests for simulator.faults — fault injection strategies."""

from __future__ import annotations

import json

import pytest

from simulator.faults import (
    FaultInjector,
    inject_malformed_json,
    inject_missing_field,
    inject_out_of_range,
)


def _sample_payload() -> dict:
    """Return a realistic telemetry dict for mutation tests."""
    return {
        "pump_id": "PUMP_001",
        "timestamp": "2024-06-15T12:00:00.000Z",
        "location": {"latitude": -1.28, "longitude": 36.81, "region": "Nairobi"},
        "solar_panel": {
            "irradiance_w_m2": 900.0,
            "voltage_v": 48.0,
            "current_a": 8.0,
            "power_w": 384.0,
            "temperature_c": 40.0,
        },
        "pump": {
            "motor_voltage_v": 47.0,
            "motor_current_a": 6.0,
            "rpm": 2800,
            "flow_rate_lpm": 120.0,
            "discharge_pressure_bar": 2.5,
            "suction_pressure_bar": 0.3,
            "water_temperature_c": 22.0,
        },
        "system": {
            "operating_hours": 1000.0,
            "vibration_mm_s": 2.0,
            "error_code": 0,
            "status": "OPERATIONAL",
        },
        "environment": {
            "ambient_temperature_c": 28.0,
            "humidity_percent": 60.0,
        },
    }


class TestInjectOutOfRange:
    def test_produces_negative_or_extreme_value(self) -> None:
        payload = _sample_payload()
        mutated = inject_out_of_range(payload)
        # At least one numeric value should be out of normal range
        flat_values: list[float] = []
        for section in ("pump", "solar_panel", "system"):
            if isinstance(mutated.get(section), dict):
                flat_values.extend(
                    v for v in mutated[section].values() if isinstance(v, (int, float))
                )
        assert any(v < 0 or v > 99 for v in flat_values)


class TestInjectMissingField:
    def test_removes_a_key(self) -> None:
        payload = _sample_payload()
        original_keys = set(payload.keys())
        mutated = inject_missing_field(payload)
        assert set(mutated.keys()) < original_keys

    def test_does_not_remove_pump_id(self) -> None:
        payload = _sample_payload()
        mutated = inject_missing_field(payload)
        assert "pump_id" in mutated


class TestInjectMalformedJson:
    def test_produces_invalid_json(self) -> None:
        clean = json.dumps(_sample_payload())
        corrupted = inject_malformed_json(clean)
        # The corrupted string should fail to parse as valid JSON
        # (in the vast majority of cases)
        assert corrupted != clean

    def test_original_content_partially_present(self) -> None:
        clean = json.dumps(_sample_payload())
        corrupted = inject_malformed_json(clean)
        # At least the pump_id should still be recognisable
        assert "PUMP_001" in corrupted


class TestFaultInjector:
    def test_rate_zero_never_injects(self) -> None:
        fi = FaultInjector(rate=0.0)
        for _ in range(100):
            body, fault = fi.maybe_inject(_sample_payload())
            assert fault == "none"
            json.loads(body)  # must be valid JSON

    def test_rate_one_always_injects(self) -> None:
        fi = FaultInjector(rate=1.0)
        faults_seen: set[str] = set()
        for _ in range(200):
            _, fault = fi.maybe_inject(_sample_payload())
            assert fault != "none"
            faults_seen.add(fault)
        # With 200 tries and 3 fault types, we should see all three
        assert faults_seen == {"out_of_range", "missing_field", "malformed_json"}

    def test_returns_json_string(self) -> None:
        fi = FaultInjector(rate=0.0)
        body, _ = fi.maybe_inject(_sample_payload())
        assert isinstance(body, str)
        parsed = json.loads(body)
        assert parsed["pump_id"] == "PUMP_001"
