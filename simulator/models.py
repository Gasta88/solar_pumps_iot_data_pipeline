"""Pydantic models for the telemetry JSON schema."""

from __future__ import annotations

from datetime import datetime, timezone

from pydantic import BaseModel, Field


class LocationTelemetry(BaseModel):
    """GPS coordinates embedded in a telemetry message."""

    latitude: float
    longitude: float
    region: str


class SolarPanelTelemetry(BaseModel):
    """Instantaneous solar panel readings."""

    irradiance_w_m2: float
    voltage_v: float
    current_a: float
    power_w: float
    temperature_c: float


class PumpTelemetry(BaseModel):
    """Instantaneous water-pump motor readings."""

    motor_voltage_v: float
    motor_current_a: float
    rpm: int
    flow_rate_lpm: float
    discharge_pressure_bar: float
    suction_pressure_bar: float
    water_temperature_c: float


class SystemTelemetry(BaseModel):
    """Operational metadata for the pump system."""

    operating_hours: float
    vibration_mm_s: float
    error_code: int = 0
    status: str = "OPERATIONAL"


class EnvironmentTelemetry(BaseModel):
    """Ambient environmental conditions."""

    ambient_temperature_c: float
    humidity_percent: float


class TelemetryMessage(BaseModel):
    """Top-level telemetry message published to RabbitMQ.

    Mirrors the JSON schema specified in the project requirements.
    """

    pump_id: str
    timestamp: str = Field(
        default_factory=lambda: datetime.now(timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )
    location: LocationTelemetry
    solar_panel: SolarPanelTelemetry
    pump: PumpTelemetry
    system: SystemTelemetry
    environment: EnvironmentTelemetry
