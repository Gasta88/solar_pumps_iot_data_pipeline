"""Entry-point for the Solar Pumps IoT Simulator.

Usage::

    python -m simulator --config config/simulator/config.yaml

Graceful shutdown on SIGTERM / SIGINT (Ctrl-C).
"""

from __future__ import annotations

import argparse
import logging
import signal
import threading
from pathlib import Path
from typing import List

from simulator.config import load_config
from simulator.faults import FaultInjector
from simulator.logging_config import setup_logging
from simulator.metrics import start_metrics_server
from simulator.pump_worker import PumpWorker

logger = logging.getLogger(__name__)


def _parse_args(argv: List[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Solar Pumps IoT Simulator",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("config/simulator/config.yaml"),
        help="Path to the YAML configuration file",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Logging verbosity",
    )
    return parser.parse_args(argv)


def main(argv: List[str] | None = None) -> None:
    """Bootstrap and run the simulator until terminated."""
    args = _parse_args(argv)

    # --- Logging ---
    setup_logging(level=getattr(logging, args.log_level))

    # --- Configuration ---
    logger.info(
        "Loading configuration from %s",
        args.config,
        extra={"event_type": "config_load"},
    )
    config = load_config(args.config)

    # --- Prometheus ---
    start_metrics_server(config.prometheus.port)
    logger.info(
        "Prometheus metrics server started on :%d",
        config.prometheus.port,
        extra={"event_type": "metrics_started"},
    )

    # --- Fault injector ---
    fault_injector: FaultInjector | None = None
    if config.simulator.failure_injection.enabled:
        fault_injector = FaultInjector(
            rate=config.simulator.failure_injection.rate,
        )
        logger.info(
            "Failure injection enabled (rate=%.2f)",
            fault_injector.rate,
            extra={"event_type": "fault_injection_enabled"},
        )

    # --- Shutdown coordination ---
    stop_event = threading.Event()

    def _signal_handler(signum: int, _frame: object) -> None:
        sig_name = signal.Signals(signum).name
        logger.info(
            "Received %s – initiating graceful shutdown",
            sig_name,
            extra={"event_type": "shutdown_signal"},
        )
        stop_event.set()

    signal.signal(signal.SIGTERM, _signal_handler)
    signal.signal(signal.SIGINT, _signal_handler)

    # --- Spawn pump workers ---
    workers: List[PumpWorker] = []
    for pump_def in config.pumps:
        worker = PumpWorker(
            pump_def=pump_def,
            rabbitmq_config=config.rabbitmq,
            interval_sec=config.simulator.telemetry_interval_sec,
            fault_injector=fault_injector,
            stop_event=stop_event,
        )
        worker.start()
        workers.append(worker)
        logger.info(
            "Started worker for %s",
            pump_def.pump_id,
            extra={
                "pump_id": pump_def.pump_id,
                "event_type": "worker_spawned",
            },
        )

    logger.info(
        "Simulator running with %d pump(s).  Press Ctrl-C or send SIGTERM to stop.",
        len(workers),
        extra={"event_type": "simulator_running"},
    )

    # --- Wait for stop signal, then join all threads ---
    try:
        while not stop_event.is_set():
            stop_event.wait(timeout=1.0)
    except KeyboardInterrupt:
        stop_event.set()

    logger.info("Joining worker threads …", extra={"event_type": "shutdown_join"})
    for w in workers:
        w.join(timeout=10.0)
        if w.is_alive():
            logger.warning(
                "Worker %s did not stop in time",
                w.name,
                extra={"event_type": "shutdown_timeout"},
            )

    logger.info("Simulator shut down cleanly.", extra={"event_type": "shutdown_complete"})
