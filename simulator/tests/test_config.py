"""Tests for simulator.config — YAML loading and Pydantic validation."""

from __future__ import annotations

import os
import textwrap
from pathlib import Path

import pytest

from simulator.config import (
    AppConfig,
    PumpDefinition,
    RabbitMQConfig,
    load_config,
)


# ---------------------------------------------------------------------------
# Happy-path
# ---------------------------------------------------------------------------

class TestLoadConfig:
    """Test the YAML ➜ Pydantic config pipeline."""

    def test_loads_valid_yaml(self, config_yaml_path: Path) -> None:
        cfg = load_config(config_yaml_path)
        assert isinstance(cfg, AppConfig)
        assert cfg.rabbitmq.host == "localhost"
        assert cfg.prometheus.port == 8001
        assert len(cfg.pumps) == 1
        assert cfg.pumps[0].pump_id == "PUMP_001"

    def test_env_var_interpolation(self, tmp_path: Path) -> None:
        os.environ["TEST_RMQ_HOST"] = "rabbit.prod"
        try:
            yaml_text = textwrap.dedent("""\
                rabbitmq:
                  host: "${TEST_RMQ_HOST:-localhost}"
                pumps:
                  - pump_id: "PUMP_001"
                    location: {latitude: 0, longitude: 0, region: test}
            """)
            p = tmp_path / "env.yaml"
            p.write_text(yaml_text)
            cfg = load_config(p)
            assert cfg.rabbitmq.host == "rabbit.prod"
        finally:
            del os.environ["TEST_RMQ_HOST"]

    def test_env_var_default_fallback(self, tmp_path: Path) -> None:
        yaml_text = textwrap.dedent("""\
            rabbitmq:
              host: "${NONEXISTENT_VAR:-fallback_host}"
            pumps:
              - pump_id: "PUMP_001"
                location: {latitude: 0, longitude: 0, region: test}
        """)
        p = tmp_path / "fb.yaml"
        p.write_text(yaml_text)
        cfg = load_config(p)
        assert cfg.rabbitmq.host == "fallback_host"


# ---------------------------------------------------------------------------
# Validation errors
# ---------------------------------------------------------------------------

class TestConfigValidation:
    """Verify that Pydantic catches bad configs early."""

    def test_no_pumps_raises(self, tmp_path: Path) -> None:
        yaml_text = textwrap.dedent("""\
            pumps: []
        """)
        p = tmp_path / "empty.yaml"
        p.write_text(yaml_text)
        with pytest.raises(Exception):
            load_config(p)

    def test_bad_pump_id_pattern(self) -> None:
        with pytest.raises(Exception):
            PumpDefinition.model_validate(
                {
                    "pump_id": "pump-bad",
                    "location": {"latitude": 0, "longitude": 0, "region": "x"},
                }
            )

    def test_invalid_port_range(self) -> None:
        with pytest.raises(Exception):
            RabbitMQConfig(port=99999)

    def test_latitude_out_of_range(self) -> None:
        with pytest.raises(Exception):
            PumpDefinition.model_validate(
                {
                    "pump_id": "PUMP_001",
                    "location": {
                        "latitude": 200,
                        "longitude": 0,
                        "region": "x",
                    },
                }
            )

    def test_negative_interval(self, tmp_path: Path) -> None:
        yaml_text = textwrap.dedent("""\
            simulator:
              telemetry_interval_sec: -1
            pumps:
              - pump_id: "PUMP_001"
                location: {latitude: 0, longitude: 0, region: test}
        """)
        p = tmp_path / "neg.yaml"
        p.write_text(yaml_text)
        with pytest.raises(Exception):
            load_config(p)
