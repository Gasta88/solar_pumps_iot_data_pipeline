"""Generate realistic telemetry readings for a single pump."""

from __future__ import annotations

import random
from datetime import datetime, timezone

from simulator.config import PumpDefinition
from simulator.models import (
    EnvironmentTelemetry,
    LocationTelemetry,
    PumpTelemetry,
    SolarPanelTelemetry,
    SystemTelemetry,
    TelemetryMessage,
)
from simulator.solar import solar_irradiance


class TelemetryGenerator:
    """Stateful generator that produces realistic telemetry for one pump.

    It tracks cumulative operating hours so successive readings are coherent.
    """

    def __init__(self, pump_def: PumpDefinition) -> None:
        self._def = pump_def
        self._operating_hours: float = round(random.uniform(100, 5000), 1)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def generate(self, dt: datetime | None = None) -> TelemetryMessage:
        """Build a complete :class:`TelemetryMessage` for the current instant."""
        if dt is None:
            dt = datetime.now(timezone.utc)

        irradiance = solar_irradiance(
            dt, peak_irradiance=self._def.solar_panel.peak_irradiance_w_m2
        )
        is_daytime = irradiance > 0

        solar = self._solar_panel(irradiance, is_daytime)
        pump = self._pump_readings(solar.power_w, is_daytime)
        system = self._system_readings(is_daytime)
        env = self._environment()

        return TelemetryMessage(
            pump_id=self._def.pump_id,
            timestamp=dt.isoformat(timespec="milliseconds").replace(
                "+00:00", "Z"
            ),
            location=LocationTelemetry(
                latitude=self._def.location.latitude,
                longitude=self._def.location.longitude,
                region=self._def.location.region,
            ),
            solar_panel=solar,
            pump=pump,
            system=system,
            environment=env,
        )

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _solar_panel(
        self, irradiance: float, is_daytime: bool
    ) -> SolarPanelTelemetry:
        cfg = self._def.solar_panel
        if not is_daytime:
            return SolarPanelTelemetry(
                irradiance_w_m2=0.0,
                voltage_v=0.0,
                current_a=0.0,
                power_w=0.0,
                temperature_c=round(random.uniform(15, 25), 1),
            )

        ratio = irradiance / cfg.peak_irradiance_w_m2
        voltage = round(cfg.nominal_voltage_v * random.uniform(0.95, 1.02), 1)
        current = round(cfg.max_current_a * ratio * random.uniform(0.9, 1.0), 1)
        power = round(voltage * current, 1)
        temp = round(25 + 30 * ratio + random.uniform(-2, 2), 1)

        return SolarPanelTelemetry(
            irradiance_w_m2=irradiance,
            voltage_v=voltage,
            current_a=current,
            power_w=power,
            temperature_c=temp,
        )

    def _pump_readings(
        self, solar_power: float, is_daytime: bool
    ) -> PumpTelemetry:
        cfg = self._def.pump
        if not is_daytime:
            return PumpTelemetry(
                motor_voltage_v=0.0,
                motor_current_a=0.0,
                rpm=0,
                flow_rate_lpm=0.0,
                discharge_pressure_bar=0.0,
                suction_pressure_bar=round(random.uniform(0.1, 0.4), 2),
                water_temperature_c=round(random.uniform(18, 24), 1),
            )

        max_power = (
            self._def.solar_panel.nominal_voltage_v
            * self._def.solar_panel.max_current_a
        )
        power_ratio = min(solar_power / max_power, 1.0) if max_power > 0 else 0

        voltage = round(
            self._def.solar_panel.nominal_voltage_v
            * random.uniform(0.93, 0.99),
            1,
        )
        current = round(solar_power / voltage if voltage else 0, 1)
        rpm = int(cfg.max_rpm * power_ratio * random.uniform(0.92, 1.0))
        flow = round(
            cfg.max_flow_rate_lpm * power_ratio * random.uniform(0.85, 1.0), 1
        )
        discharge = round(
            cfg.max_discharge_pressure_bar * power_ratio * random.uniform(0.8, 1.0),
            2,
        )
        suction = round(random.uniform(0.1, 0.5), 2)
        water_temp = round(random.uniform(18, 28), 1)

        return PumpTelemetry(
            motor_voltage_v=voltage,
            motor_current_a=current,
            rpm=rpm,
            flow_rate_lpm=flow,
            discharge_pressure_bar=discharge,
            suction_pressure_bar=suction,
            water_temperature_c=water_temp,
        )

    def _system_readings(self, is_daytime: bool) -> SystemTelemetry:
        if is_daytime:
            self._operating_hours = round(self._operating_hours + 0.01, 2)

        vibration = round(random.uniform(0.5, 4.0) if is_daytime else 0.0, 1)
        status = "OPERATIONAL" if is_daytime else "STANDBY"

        return SystemTelemetry(
            operating_hours=self._operating_hours,
            vibration_mm_s=vibration,
            error_code=0,
            status=status,
        )

    def _environment(self) -> EnvironmentTelemetry:
        return EnvironmentTelemetry(
            ambient_temperature_c=round(random.uniform(20, 38), 1),
            humidity_percent=round(random.uniform(30, 85), 1),
        )
