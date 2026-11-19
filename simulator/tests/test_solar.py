"""Tests for simulator.solar — solar irradiance generation."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from simulator.solar import solar_irradiance

NIGHT_HOURS = [0, 1, 2, 3, 4, 5, 18, 19, 20, 21, 22, 23]
WRAPPED_NIGHT_HOURS = [0, 1, 2, 3, 4, 5, 19, 20, 21, 22, 23]
DAYTIME_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17]


def _at(hour: int, minute: int = 0) -> datetime:
    return datetime(2024, 6, 15, hour, minute, 0, tzinfo=timezone.utc)


class TestRealDayNightCycle:
    """``always_daytime=False`` returns a physically real day/night curve."""

    @pytest.mark.parametrize("hour", NIGHT_HOURS)
    def test_night_returns_zero(self, hour: int) -> None:
        assert solar_irradiance(_at(hour), noise_std=0, always_daytime=False) == 0.0

    @pytest.mark.parametrize("hour", DAYTIME_HOURS)
    def test_daytime_positive(self, hour: int) -> None:
        value = solar_irradiance(_at(hour), noise_std=0, always_daytime=False)
        assert value > 0

    def test_peak_at_noon(self) -> None:
        value = solar_irradiance(
            _at(12), peak_irradiance=1100.0, noise_std=0, always_daytime=False
        )
        assert value == pytest.approx(1100.0, abs=1.0)

    def test_symmetric_morning_afternoon(self) -> None:
        v_am = solar_irradiance(_at(9), noise_std=0, always_daytime=False)
        v_pm = solar_irradiance(_at(15), noise_std=0, always_daytime=False)
        assert v_am == pytest.approx(v_pm, abs=1.0)

    @pytest.mark.parametrize("hour", [6, 18])
    def test_boundaries_are_zero(self, hour: int) -> None:
        value = solar_irradiance(_at(hour), noise_std=0, always_daytime=False)
        assert value == pytest.approx(0.0, abs=1.0)


class TestAlwaysDaytimeWrap:
    """The demo default (``always_daytime=True``) never goes dark."""

    @pytest.mark.parametrize("hour", WRAPPED_NIGHT_HOURS)
    def test_night_wraps_into_daytime(self, hour: int) -> None:
        value = solar_irradiance(_at(hour), noise_std=0)
        assert value > 0

    def test_default_is_always_daytime(self) -> None:
        # Midnight wraps onto the solar-noon peak.
        value = solar_irradiance(_at(0), peak_irradiance=1100.0, noise_std=0)
        assert value == pytest.approx(1100.0, abs=1.0)

    def test_explicit_true_matches_default(self) -> None:
        default = solar_irradiance(_at(22), noise_std=0)
        explicit = solar_irradiance(_at(22), noise_std=0, always_daytime=True)
        assert default == explicit

    @pytest.mark.parametrize("hour", [6, 18])
    def test_boundaries_stay_zero(self, hour: int) -> None:
        value = solar_irradiance(_at(hour), noise_std=0, always_daytime=True)
        assert value == pytest.approx(0.0, abs=1.0)


class TestNoiseBehaviour:
    def test_never_negative(self) -> None:
        for hour in range(24):
            for _ in range(50):
                assert solar_irradiance(_at(hour), noise_std=100) >= 0.0

    def test_default_dt_runs_without_error(self) -> None:
        assert isinstance(solar_irradiance(), float)
