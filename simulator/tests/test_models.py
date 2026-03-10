"""Tests for simulator.models — Pydantic telemetry schemas."""

from __future__ import annotations

import json
from datetime import datetime, timezone

from simulator.models import (
    EnvironmentTelemetry,
    LocationTelemetry,
    PumpTelemetry,
    SolarPanelTelemetry,
    SystemTelemetry,
    TelemetryMessage,
)


def _make_message(**overrides: object) -> TelemetryMessage:
    """Build a valid TelemetryMessage, applying optional overrides."""
    defaults = {
        "pump_id": "PUMP_001",
        "location": {
            "latitude": -1.286389,
            "longitude": 36.817223,
            "region": "Nairobi",
        },
        "solar_panel": {
            "irradiance_w_m2": 850.5,
            "voltage_v": 48.2,
            "current_a": 8.3,
            "power_w": 400.1,
            "temperature_c": 42.1,
        },
        "pump": {
            "motor_voltage_v": 47.8,
            "motor_current_a": 6.2,
            "rpm": 2850,
            "flow_rate_lpm": 120.5,
            "discharge_pressure_bar": 2.8,
            "suction_pressure_bar": 0.3,
            "water_temperature_c": 22.1,
        },
        "system": {
            "operating_hours": 1234.5,
            "vibration_mm_s": 2.1,
            "error_code": 0,
            "status": "OPERATIONAL",
        },
        "environment": {
            "ambient_temperature_c": 28.5,
            "humidity_percent": 65.2,
        },
    }
    defaults.update(overrides)
    return TelemetryMessage.model_validate(defaults)


class TestTelemetryMessage:
    """Validate telemetry model construction and serialisation."""

    def test_valid_message_round_trip(self) -> None:
        msg = _make_message()
        data = json.loads(msg.model_dump_json())
        assert data["pump_id"] == "PUMP_001"
        assert data["solar_panel"]["irradiance_w_m2"] == 850.5
        assert data["pump"]["rpm"] == 2850

    def test_timestamp_auto_generated(self) -> None:
        msg = _make_message()
        assert msg.timestamp.endswith("Z")

    def test_json_schema_keys_present(self) -> None:
        msg = _make_message()
        data = msg.model_dump()
        for key in (
            "pump_id",
            "timestamp",
            "location",
            "solar_panel",
            "pump",
            "system",
            "environment",
        ):
            assert key in data

    def test_location_fields(self) -> None:
        msg = _make_message()
        loc = msg.location
        assert loc.latitude == -1.286389
        assert loc.longitude == 36.817223
        assert loc.region == "Nairobi"

    def test_system_defaults(self) -> None:
        msg = _make_message()
        assert msg.system.error_code == 0
        assert msg.system.status == "OPERATIONAL"
