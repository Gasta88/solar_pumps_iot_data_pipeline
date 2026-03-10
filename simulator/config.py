"""Configuration loading and Pydantic validation for the IoT simulator."""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import List

import yaml
from pydantic import BaseModel, Field, field_validator


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------

class RabbitMQConfig(BaseModel):
    """RabbitMQ connection settings."""

    host: str = "localhost"
    port: int = Field(default=5672, ge=1, le=65535)
    username: str = "iot_user"
    password: str = "changeme_rabbitmq"
    exchange: str = "iot.data"
    routing_key: str = "telemetry.raw"
    heartbeat: int = Field(default=60, ge=0)
    connection_timeout: int = Field(default=10, ge=1)


class PrometheusConfig(BaseModel):
    """Prometheus metrics endpoint settings."""

    port: int = Field(default=8001, ge=1, le=65535)


class FailureInjectionConfig(BaseModel):
    """Failure / fault-injection settings."""

    enabled: bool = True
    rate: float = Field(default=0.05, ge=0.0, le=1.0)


class SimulatorConfig(BaseModel):
    """Top-level simulator tuning knobs."""

    telemetry_interval_sec: float = Field(default=5.0, gt=0)
    failure_injection: FailureInjectionConfig = FailureInjectionConfig()


class LocationConfig(BaseModel):
    """GPS coordinates and region name for a pump."""

    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    region: str


class SolarPanelConfig(BaseModel):
    """Nameplate specs for the solar panel driving a pump."""

    peak_irradiance_w_m2: float = Field(default=1100.0, gt=0)
    nominal_voltage_v: float = Field(default=48.0, gt=0)
    max_current_a: float = Field(default=10.0, gt=0)


class PumpHardwareConfig(BaseModel):
    """Nameplate specs for the water pump."""

    max_rpm: int = Field(default=3000, gt=0)
    max_flow_rate_lpm: float = Field(default=150.0, gt=0)
    max_discharge_pressure_bar: float = Field(default=4.0, gt=0)


class PumpDefinition(BaseModel):
    """Full definition of a single pump (identity + hardware)."""

    pump_id: str
    location: LocationConfig
    solar_panel: SolarPanelConfig = SolarPanelConfig()
    pump: PumpHardwareConfig = PumpHardwareConfig()

    @field_validator("pump_id")
    @classmethod
    def pump_id_format(cls, v: str) -> str:
        if not re.match(r"^PUMP_\d{3,}$", v):
            raise ValueError(
                f"pump_id must match PUMP_NNN pattern, got '{v}'"
            )
        return v


class AppConfig(BaseModel):
    """Root configuration object for the whole application."""

    rabbitmq: RabbitMQConfig = RabbitMQConfig()
    prometheus: PrometheusConfig = PrometheusConfig()
    simulator: SimulatorConfig = SimulatorConfig()
    pumps: List[PumpDefinition]

    @field_validator("pumps")
    @classmethod
    def at_least_one_pump(cls, v: List[PumpDefinition]) -> List[PumpDefinition]:
        if not v:
            raise ValueError("At least one pump must be defined")
        return v


# ---------------------------------------------------------------------------
# YAML loader with env-var interpolation
# ---------------------------------------------------------------------------

_ENV_VAR_RE = re.compile(r"\$\{(?P<var>[^}:]+)(?::-(?P<default>[^}]*))?\}")


def _resolve_env_vars(value: str) -> str:
    """Replace ``${VAR:-default}`` placeholders with environment values."""

    def _replacer(match: re.Match) -> str:
        var_name = match.group("var")
        default = match.group("default") or ""
        return os.environ.get(var_name, default)

    return _ENV_VAR_RE.sub(_replacer, value)


def _walk_and_resolve(obj: object) -> object:  # noqa: ANN401
    """Recursively walk a parsed YAML tree and resolve env-var strings."""
    if isinstance(obj, dict):
        return {k: _walk_and_resolve(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_walk_and_resolve(item) for item in obj]
    if isinstance(obj, str):
        return _resolve_env_vars(obj)
    return obj


def load_config(path: str | Path) -> AppConfig:
    """Load, interpolate, and validate a YAML configuration file.

    Parameters
    ----------
    path:
        Filesystem path to the YAML config file.

    Returns
    -------
    AppConfig
        Fully validated application configuration.
    """
    path = Path(path)
    with path.open() as fh:
        raw = yaml.safe_load(fh)

    resolved = _walk_and_resolve(raw)
    return AppConfig.model_validate(resolved)
