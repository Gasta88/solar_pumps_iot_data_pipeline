"""Shared fixtures for the simulator test suite."""

from __future__ import annotations

import textwrap
from pathlib import Path

import pytest

from simulator.config import (
    AppConfig,
    PumpDefinition,
    RabbitMQConfig,
    load_config,
)


@pytest.fixture()
def sample_pump_def() -> PumpDefinition:
    """Return a minimal ``PumpDefinition`` for unit tests."""
    return PumpDefinition.model_validate(
        {
            "pump_id": "PUMP_001",
            "location": {
                "latitude": -1.286389,
                "longitude": 36.817223,
                "region": "Nairobi",
            },
            "solar_panel": {
                "peak_irradiance_w_m2": 1100.0,
                "nominal_voltage_v": 48.0,
                "max_current_a": 10.0,
            },
            "pump": {
                "max_rpm": 3000,
                "max_flow_rate_lpm": 150.0,
                "max_discharge_pressure_bar": 4.0,
            },
        }
    )


@pytest.fixture()
def sample_rabbitmq_config() -> RabbitMQConfig:
    return RabbitMQConfig()


@pytest.fixture()
def config_yaml_path(tmp_path: Path) -> Path:
    """Write a minimal valid YAML config to a temp file and return the path."""
    yaml_content = textwrap.dedent("""\
        rabbitmq:
          host: localhost
          port: 5672
          username: guest
          password: guest
          exchange: iot.data
          routing_key: telemetry.raw
          heartbeat: 60
          connection_timeout: 10

        prometheus:
          port: 8001

        simulator:
          telemetry_interval_sec: 2.0
          failure_injection:
            enabled: true
            rate: 0.1

        pumps:
          - pump_id: "PUMP_001"
            location:
              latitude: -1.286389
              longitude: 36.817223
              region: "Nairobi"
            solar_panel:
              peak_irradiance_w_m2: 1100.0
              nominal_voltage_v: 48.0
              max_current_a: 10.0
            pump:
              max_rpm: 3000
              max_flow_rate_lpm: 150.0
              max_discharge_pressure_bar: 4.0
    """)
    p = tmp_path / "config.yaml"
    p.write_text(yaml_content)
    return p


@pytest.fixture()
def app_config(config_yaml_path: Path) -> AppConfig:
    return load_config(config_yaml_path)
