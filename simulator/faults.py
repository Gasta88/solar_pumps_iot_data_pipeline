"""Failure injection module.

Injects three categories of faults into telemetry payloads:

1. **Out-of-range values** - e.g. negative flow rate, absurd RPM.
2. **Missing required fields** - randomly drops a top-level key.
3. **Structural corruption** - replaces structured sections (e.g. ``location``)
   with nonsense but still-valid JSON values.
"""

from __future__ import annotations

import json
import random
from typing import Any, Dict


# ---------------------------------------------------------------------------
# Fault strategies
# ---------------------------------------------------------------------------


def inject_out_of_range(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Mutate one or more numeric fields to physically impossible values."""
    mutations: list[tuple[str, str, float]] = [
        ("pump", "flow_rate_lpm", round(random.uniform(-50.0, -1.0), 1)),
        ("pump", "rpm", random.randint(-500, -10)),
        ("solar_panel", "irradiance_w_m2", round(random.uniform(-200, -10), 1)),
        ("solar_panel", "voltage_v", round(random.uniform(-100, -5), 1)),
        ("pump", "discharge_pressure_bar", round(random.uniform(-10, -0.5), 2)),
        ("system", "vibration_mm_s", round(random.uniform(100, 500), 1)),
    ]
    section, field, value = random.choice(mutations)
    if section in payload and isinstance(payload[section], dict):
        payload[section][field] = value
    return payload


def inject_missing_field(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Remove a randomly chosen required top-level field."""
    removable = [
        k for k in ("solar_panel", "pump", "system", "environment") if k in payload
    ]
    if removable:
        key = random.choice(removable)
        del payload[key]
    return payload


def inject_structural_corruption(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Replace structured sections with incompatible JSON-friendly values."""

    corruptors: list[tuple[str, Any]] = []
    if "location" in payload:
        corruptors.append(
            (
                "location",
                random.choice(
                    [
                        "CORRUPTED_LOCATION",
                        [42, "??"],
                        {"lat": "NaN", "lon": None},
                    ]
                ),
            )
        )
    if "solar_panel" in payload:
        corruptors.append(
            (
                "solar_panel",
                random.choice(
                    [
                        "NOT_AN_OBJECT",
                        12345,
                        ["voltage", "??"],
                    ]
                ),
            )
        )
    if "pump" in payload:
        corruptors.append(
            (
                "pump",
                random.choice(
                    [
                        None,
                        "PUMP_FAIL",
                        [1, 2, 3],
                    ]
                ),
            )
        )
    if "system" in payload:
        corruptors.append(("system", "SYSTEM_CORRUPTED"))

    if corruptors:
        field, value = random.choice(corruptors)
        payload[field] = value
    else:
        payload["garbled"] = "structural_fault"

    return payload


# ---------------------------------------------------------------------------
# Public entry-point
# ---------------------------------------------------------------------------


class FaultInjector:
    """Probabilistically injects faults into telemetry payloads.

    Parameters
    ----------
    rate:
        Probability (0..1) that *any given message* will be faulted.
    """

    FAULT_TYPES = ("out_of_range", "missing_field", "structural_corruption")

    def __init__(self, rate: float = 0.05) -> None:
        self.rate = rate

    def maybe_inject(self, payload: Dict[str, Any]) -> tuple[str, str]:
        """Optionally inject a fault and return ``(json_string, fault_type)``.

        Returns
        -------
        tuple[str, str]
            A 2-tuple of ``(serialised_json, fault_type)`` where
            *fault_type* is one of ``"out_of_range"``, ``"missing_field"``,
            ``"structural_corruption"``, or ``"none"`` when no fault was injected.
        """
        if random.random() >= self.rate:
            return json.dumps(payload), "none"

        fault = random.choice(self.FAULT_TYPES)

        if fault == "out_of_range":
            payload = inject_out_of_range(payload)
            return json.dumps(payload), fault

        if fault == "missing_field":
            payload = inject_missing_field(payload)
            return json.dumps(payload), fault

        payload = inject_structural_corruption(payload)
        return json.dumps(payload), fault
