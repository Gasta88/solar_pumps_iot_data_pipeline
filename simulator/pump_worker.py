"""Pump worker thread — one thread per pump."""

from __future__ import annotations

import logging
import threading
import time

from simulator.config import PumpDefinition, RabbitMQConfig
from simulator.faults import FaultInjector
from simulator.metrics import (
    ACTIVE_PUMPS,
    FAULTS_INJECTED,
    FLOW_RATE_GAUGE,
    IRRADIANCE_GAUGE,
    MESSAGES_PUBLISHED,
    PUBLISH_ERRORS,
    PUBLISH_LATENCY,
)
from simulator.publisher import RabbitMQPublisher
from simulator.telemetry import TelemetryGenerator

logger = logging.getLogger(__name__)


class PumpWorker(threading.Thread):
    """Daemon thread that periodically generates and publishes telemetry.

    Parameters
    ----------
    pump_def:
        Pump hardware definition (from config).
    rabbitmq_config:
        RabbitMQ connection parameters.
    interval_sec:
        Seconds between successive telemetry messages.
    fault_injector:
        Optional :class:`FaultInjector` instance.
    stop_event:
        A :class:`threading.Event` signalled on graceful shutdown.
    """

    def __init__(
        self,
        pump_def: PumpDefinition,
        rabbitmq_config: RabbitMQConfig,
        interval_sec: float,
        fault_injector: FaultInjector | None,
        stop_event: threading.Event,
    ) -> None:
        super().__init__(name=f"worker-{pump_def.pump_id}", daemon=True)
        self._pump_def = pump_def
        self._rabbitmq_config = rabbitmq_config
        self._interval = interval_sec
        self._fault_injector = fault_injector
        self._stop_event = stop_event

        self._generator = TelemetryGenerator(pump_def)
        self._publisher: RabbitMQPublisher | None = None

    # ------------------------------------------------------------------
    # Thread body
    # ------------------------------------------------------------------

    def run(self) -> None:  # noqa: C901
        pump_id = self._pump_def.pump_id
        logger.info(
            "Pump worker starting",
            extra={"pump_id": pump_id, "event_type": "worker_start"},
        )

        self._publisher = RabbitMQPublisher(self._rabbitmq_config)
        try:
            self._publisher.connect()
        except Exception:
            logger.exception(
                "Unable to connect to RabbitMQ – worker exiting",
                extra={"pump_id": pump_id, "event_type": "worker_error"},
            )
            return

        ACTIVE_PUMPS.inc()
        try:
            self._loop(pump_id)
        finally:
            ACTIVE_PUMPS.dec()
            if self._publisher:
                self._publisher.close()
            logger.info(
                "Pump worker stopped",
                extra={"pump_id": pump_id, "event_type": "worker_stop"},
            )

    def _loop(self, pump_id: str) -> None:
        while not self._stop_event.is_set():
            try:
                msg = self._generator.generate()
                payload = msg.model_dump()

                # Update Prometheus gauges
                IRRADIANCE_GAUGE.labels(pump_id=pump_id).set(
                    payload["solar_panel"]["irradiance_w_m2"]
                )
                FLOW_RATE_GAUGE.labels(pump_id=pump_id).set(
                    payload["pump"]["flow_rate_lpm"]
                )

                # Fault injection
                if self._fault_injector:
                    body, fault_type = self._fault_injector.maybe_inject(payload)
                    if fault_type != "none":
                        FAULTS_INJECTED.labels(
                            pump_id=pump_id, fault_type=fault_type
                        ).inc()
                        logger.info(
                            "Fault injected: %s",
                            fault_type,
                            extra={
                                "pump_id": pump_id,
                                "event_type": "fault_injected",
                                "fault_type": fault_type,
                            },
                        )
                else:
                    import json
                    body = json.dumps(payload)
                    fault_type = "none"

                # Publish
                t0 = time.monotonic()
                self._publisher.publish(body)
                elapsed = time.monotonic() - t0

                PUBLISH_LATENCY.labels(pump_id=pump_id).observe(elapsed)
                MESSAGES_PUBLISHED.labels(pump_id=pump_id).inc()

                logger.debug(
                    "Telemetry published (%.3fs)",
                    elapsed,
                    extra={"pump_id": pump_id, "event_type": "telemetry_published"},
                )

            except Exception:
                PUBLISH_ERRORS.labels(pump_id=pump_id).inc()
                logger.exception(
                    "Error in telemetry loop",
                    extra={"pump_id": pump_id, "event_type": "worker_error"},
                )

            self._stop_event.wait(self._interval)
