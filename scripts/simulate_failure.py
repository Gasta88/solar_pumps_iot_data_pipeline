#!/usr/bin/env python3
"""Publish a crafted failure payload to RabbitMQ for demo purposes."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Callable, Dict, Any

import pika

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from simulator.config import load_config, AppConfig, PumpDefinition  # noqa: E402
from simulator.telemetry import TelemetryGenerator  # noqa: E402
from simulator.faults import (  # noqa: E402
    inject_missing_field,
    inject_out_of_range,
    inject_structural_corruption,
)

Payload = Dict[str, Any]


def _deep_copy(payload: Payload) -> Payload:
    return json.loads(json.dumps(payload))


def _pump_offline(payload: Payload) -> Payload:
    data = _deep_copy(payload)
    pump = data.setdefault("pump", {})
    system = data.setdefault("system", {})
    solar = data.setdefault("solar_panel", {})
    for field in ("motor_voltage_v", "motor_current_a", "flow_rate_lpm"):
        pump[field] = 0.0
    pump["rpm"] = 0
    pump["discharge_pressure_bar"] = 0.0
    pump["suction_pressure_bar"] = 0.0
    system["status"] = "FAULTED"
    system["error_code"] = 9010
    system["vibration_mm_s"] = 0.0
    solar["power_w"] = 0.0
    solar["current_a"] = 0.0
    return data


def _high_vibration(payload: Payload) -> Payload:
    data = _deep_copy(payload)
    system = data.setdefault("system", {})
    system["vibration_mm_s"] = 120.0
    system["error_code"] = 9020
    system["status"] = "ALERT"
    return data


def _stuck_sensor(payload: Payload) -> Payload:
    data = _deep_copy(payload)
    solar = data.setdefault("solar_panel", {})
    stuck_value = round(solar.get("irradiance_w_m2", 0.0), 1)
    solar["irradiance_w_m2"] = stuck_value
    solar["power_w"] = stuck_value
    data.setdefault("system", {})["error_code"] = 9030
    data.setdefault("system", {})["status"] = "DEGRADED"
    return data


FAILURE_HANDLERS: Dict[str, Callable[[Payload], Payload]] = {
    "out_of_range": lambda p: inject_out_of_range(_deep_copy(p)),
    "missing_field": lambda p: inject_missing_field(_deep_copy(p)),
    "structural_corruption": lambda p: inject_structural_corruption(_deep_copy(p)),
    "pump_offline": _pump_offline,
    "high_vibration": _high_vibration,
    "stuck_sensor": _stuck_sensor,
}


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Publish a single failure telemetry payload to RabbitMQ",
    )
    parser.add_argument(
        "--config",
        default="config/simulator/config.yaml",
        help="Path to simulator config (for pump definitions and defaults)",
    )
    parser.add_argument(
        "--pump-id",
        required=True,
        help="Pump identifier to spoof (e.g. PUMP_001)",
    )
    parser.add_argument(
        "--failure-type",
        choices=sorted(FAILURE_HANDLERS.keys()),
        default="out_of_range",
        help="Type of failure to inject",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print payload without publishing",
    )
    parser.add_argument(
        "--rabbit-host",
        help="Override RabbitMQ host (defaults to config file)",
    )
    parser.add_argument(
        "--rabbit-port",
        type=int,
        help="Override RabbitMQ port",
    )
    parser.add_argument(
        "--username",
        help="Override RabbitMQ username",
    )
    parser.add_argument(
        "--password",
        help="Override RabbitMQ password",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print connection + publish progress",
    )
    return parser.parse_args()


def _select_pump(cfg: AppConfig, pump_id: str) -> PumpDefinition:
    for pump in cfg.pumps:
        if pump.pump_id == pump_id:
            return pump
    raise SystemExit(
        f"Pump '{pump_id}' not found in {len(cfg.pumps)} configured pump(s)"
    )


def _build_payload(pump_def: PumpDefinition) -> Payload:
    generator = TelemetryGenerator(pump_def)
    message = generator.generate()
    return message.model_dump(mode="python")


def _publish(cfg: AppConfig, body: str, overrides: argparse.Namespace) -> None:
    rabbit = cfg.rabbitmq
    host = overrides.rabbit_host or rabbit.host
    port = overrides.rabbit_port or rabbit.port
    username = overrides.username or rabbit.username
    password = overrides.password or rabbit.password

    credentials = pika.PlainCredentials(username, password)
    params = pika.ConnectionParameters(
        host=host,
        port=port,
        credentials=credentials,
        heartbeat=rabbit.heartbeat,
        blocked_connection_timeout=rabbit.connection_timeout,
    )

    connection = pika.BlockingConnection(params)
    channel = connection.channel()
    channel.basic_publish(
        exchange=rabbit.exchange,
        routing_key=rabbit.routing_key,
        body=body.encode("utf-8"),
        properties=pika.BasicProperties(content_type="application/json"),
    )
    channel.close()
    connection.close()


def main() -> None:
    args = _parse_args()
    cfg = load_config(args.config)
    pump_def = _select_pump(cfg, args.pump_id)

    base_payload = _build_payload(pump_def)
    handler = FAILURE_HANDLERS[args.failure_type]
    mutated_payload = handler(base_payload)
    body = json.dumps(mutated_payload)

    if args.verbose:
        print("Payload ready:")
        print(json.dumps(mutated_payload, indent=2))

    if args.dry_run:
        print("Dry-run mode enabled; nothing was published.")
        return

    try:
        _publish(cfg, body, args)
        print(
            f"Published {args.failure_type} payload for {args.pump_id} to"
            f" {cfg.rabbitmq.exchange}:{cfg.rabbitmq.routing_key}"
        )
    except Exception as exc:  # noqa: BLE001
        raise SystemExit(f"Failed to publish payload: {exc}")


if __name__ == "__main__":
    main()
