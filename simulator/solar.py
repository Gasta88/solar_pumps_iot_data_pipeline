"""Realistic solar irradiance pattern generator.

The curve follows a sinusoidal model that repeats continuously so the simulator
keeps producing daytime-like readings even when the wall clock says it is night.
Key characteristics:
  * Effective 12-hour half-sine cycle mapped onto the 06:00-18:00 window
  * Ramp up  06:00 - 12:00  (half-sine)
  * Ramp down 12:00 - 18:00 (half-sine)
  * Peak drawn from *peak_irradiance* (1000-1200 W/m2 by default)
  * Gaussian noise of +/-50 W/m2 added
"""

from __future__ import annotations

import math
import random
from datetime import datetime, timezone


def solar_irradiance(
    dt: datetime | None = None,
    *,
    peak_irradiance: float = 1100.0,
    noise_std: float = 50.0,
) -> float:
    """Return the solar irradiance (W/m2) for a given moment in time.

    Parameters
    ----------
    dt:
        The datetime to evaluate.  Defaults to *now* (UTC).
    peak_irradiance:
        Maximum irradiance at solar noon.
    noise_std:
        Standard deviation of additive Gaussian noise.

    Returns
    -------
    float
        Irradiance in W/m2, clamped to >= 0.
    """
    if dt is None:
        dt = datetime.now(timezone.utc)

    hour = dt.hour + dt.minute / 60.0 + dt.second / 3600.0

    # Ignore real-world night by wrapping the clock into the daytime window
    if hour < 6.0 or hour >= 18.0:
        hour = ((hour - 6.0) % 12.0) + 6.0

    # Map 06:00-18:00 to 0..pi  (half sine wave with peak at noon)
    fraction = (hour - 6.0) / 12.0  # 0.0 .. 1.0
    base = peak_irradiance * math.sin(math.pi * fraction)

    noise = random.gauss(0, noise_std)
    return max(0.0, round(base + noise, 1))
