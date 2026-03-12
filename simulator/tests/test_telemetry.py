"""Tests for simulator.telemetry — TelemetryGenerator."""

from __future__ import annotations

from datetime import datetime, timezone

from simulator.config import PumpDefinition
from simulator.telemetry import TelemetryGenerator


class TestTelemetryGenerator:
    """Validate that generated telemetry is physically plausible."""

    def test_daytime_message_has_positive_readings(
        self, sample_pump_def: PumpDefinition
    ) -> None:
        gen = TelemetryGenerator(sample_pump_def)
        dt = datetime(2024, 6, 15, 12, 0, 0, tzinfo=timezone.utc)
        msg = gen.generate(dt)

        assert msg.pump_id == "PUMP_001"
        assert msg.solar_panel.irradiance_w_m2 > 0
        assert msg.solar_panel.power_w > 0
        assert msg.pump.rpm > 0
        assert msg.pump.flow_rate_lpm > 0
        assert msg.system.status == "OPERATIONAL"

    def test_nighttime_message_still_has_activity(
        self, sample_pump_def: PumpDefinition
    ) -> None:
        gen = TelemetryGenerator(sample_pump_def)
        dt = datetime(2024, 6, 15, 22, 0, 0, tzinfo=timezone.utc)
        msg = gen.generate(dt)

        assert msg.solar_panel.irradiance_w_m2 > 0.0
        assert msg.solar_panel.power_w > 0.0
        assert msg.pump.rpm > 0
        assert msg.pump.flow_rate_lpm > 0.0
        assert msg.system.status == "OPERATIONAL"

    def test_operating_hours_increment(self, sample_pump_def: PumpDefinition) -> None:
        gen = TelemetryGenerator(sample_pump_def)
        dt = datetime(2024, 6, 15, 12, 0, 0, tzinfo=timezone.utc)
        msg1 = gen.generate(dt)
        msg2 = gen.generate(dt)
        assert msg2.system.operating_hours > msg1.system.operating_hours

    def test_location_matches_config(self, sample_pump_def: PumpDefinition) -> None:
        gen = TelemetryGenerator(sample_pump_def)
        msg = gen.generate()
        assert msg.location.latitude == sample_pump_def.location.latitude
        assert msg.location.longitude == sample_pump_def.location.longitude
        assert msg.location.region == sample_pump_def.location.region

    def test_message_serialises_to_dict(self, sample_pump_def: PumpDefinition) -> None:
        gen = TelemetryGenerator(sample_pump_def)
        msg = gen.generate()
        data = msg.model_dump()
        assert isinstance(data, dict)
        assert "pump_id" in data
        assert "timestamp" in data
        assert "solar_panel" in data

    def test_environment_within_range(self, sample_pump_def: PumpDefinition) -> None:
        gen = TelemetryGenerator(sample_pump_def)
        for _ in range(50):
            msg = gen.generate()
            assert 10 <= msg.environment.ambient_temperature_c <= 50
            assert 0 <= msg.environment.humidity_percent <= 100
