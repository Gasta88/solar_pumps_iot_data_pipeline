"""Tests for simulator.solar — solar irradiance generation."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from simulator.solar import solar_irradiance


class TestSolarIrradiance:
    """Verify the day/night cycle and noise behaviour."""

    # --- Night time returns 0 ---

    @pytest.mark.parametrize(
        "hour",
        [0, 1, 2, 3, 4, 5, 18, 19, 20, 21, 22, 23],
    )
    def test_night_returns_zero(self, hour: int) -> None:
        dt = datetime(2024, 6, 15, hour, 0, 0, tzinfo=timezone.utc)
        assert solar_irradiance(dt, noise_std=0) == 0.0

    # --- Daytime is positive ---

    @pytest.mark.parametrize("hour", [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17])
    def test_daytime_positive(self, hour: int) -> None:
        dt = datetime(2024, 6, 15, hour, 0, 0, tzinfo=timezone.utc)
        value = solar_irradiance(dt, noise_std=0)
        assert value > 0

    # --- Peak at noon ---

    def test_peak_at_noon(self) -> None:
        dt = datetime(2024, 6, 15, 12, 0, 0, tzinfo=timezone.utc)
        value = solar_irradiance(dt, peak_irradiance=1100.0, noise_std=0)
        assert value == pytest.approx(1100.0, abs=1.0)

    # --- Symmetric ramp ---

    def test_symmetric_morning_afternoon(self) -> None:
        morning = datetime(2024, 6, 15, 9, 0, 0, tzinfo=timezone.utc)
        afternoon = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        v_am = solar_irradiance(morning, noise_std=0)
        v_pm = solar_irradiance(afternoon, noise_std=0)
        assert v_am == pytest.approx(v_pm, abs=1.0)

    # --- Noise clamps to >= 0 ---

    def test_never_negative(self) -> None:
        dt = datetime(2024, 6, 15, 6, 1, 0, tzinfo=timezone.utc)
        for _ in range(200):
            assert solar_irradiance(dt, noise_std=100) >= 0.0

    # --- Boundary at 06:00 ---

    def test_boundary_six_am(self) -> None:
        dt = datetime(2024, 6, 15, 6, 0, 0, tzinfo=timezone.utc)
        value = solar_irradiance(dt, noise_std=0)
        assert value == pytest.approx(0.0, abs=1.0)

    # --- Uses current time when dt is None ---

    def test_default_dt_runs_without_error(self) -> None:
        # Just check it returns a float without crashing
        result = solar_irradiance()
        assert isinstance(result, float)
