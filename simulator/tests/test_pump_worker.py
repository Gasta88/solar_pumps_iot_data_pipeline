"""Tests for simulator.pump_worker — PumpWorker thread (mocked RabbitMQ)."""

from __future__ import annotations

import threading
import time
from unittest.mock import MagicMock, patch

import pytest

from simulator.config import PumpDefinition, RabbitMQConfig
from simulator.faults import FaultInjector
from simulator.pump_worker import PumpWorker


@pytest.fixture()
def stop_event() -> threading.Event:
    return threading.Event()


class TestPumpWorker:
    """Integration-level tests with mocked RabbitMQ publisher."""

    @patch("simulator.pump_worker.RabbitMQPublisher")
    def test_worker_publishes_messages(
        self,
        mock_pub_cls: MagicMock,
        sample_pump_def: PumpDefinition,
        sample_rabbitmq_config: RabbitMQConfig,
        stop_event: threading.Event,
    ) -> None:
        mock_pub = MagicMock()
        mock_pub_cls.return_value = mock_pub

        worker = PumpWorker(
            pump_def=sample_pump_def,
            rabbitmq_config=sample_rabbitmq_config,
            interval_sec=0.1,
            fault_injector=None,
            stop_event=stop_event,
        )
        worker.start()
        time.sleep(0.5)
        stop_event.set()
        worker.join(timeout=5)

        assert mock_pub.connect.called
        assert mock_pub.publish.call_count >= 1

    @patch("simulator.pump_worker.RabbitMQPublisher")
    def test_worker_stops_on_event(
        self,
        mock_pub_cls: MagicMock,
        sample_pump_def: PumpDefinition,
        sample_rabbitmq_config: RabbitMQConfig,
        stop_event: threading.Event,
    ) -> None:
        mock_pub_cls.return_value = MagicMock()

        worker = PumpWorker(
            pump_def=sample_pump_def,
            rabbitmq_config=sample_rabbitmq_config,
            interval_sec=0.1,
            fault_injector=None,
            stop_event=stop_event,
        )
        worker.start()
        time.sleep(0.3)
        stop_event.set()
        worker.join(timeout=5)

        assert not worker.is_alive()

    @patch("simulator.pump_worker.RabbitMQPublisher")
    def test_worker_with_fault_injector(
        self,
        mock_pub_cls: MagicMock,
        sample_pump_def: PumpDefinition,
        sample_rabbitmq_config: RabbitMQConfig,
        stop_event: threading.Event,
    ) -> None:
        mock_pub = MagicMock()
        mock_pub_cls.return_value = mock_pub

        fi = FaultInjector(rate=1.0)

        worker = PumpWorker(
            pump_def=sample_pump_def,
            rabbitmq_config=sample_rabbitmq_config,
            interval_sec=0.1,
            fault_injector=fi,
            stop_event=stop_event,
        )
        worker.start()
        time.sleep(0.5)
        stop_event.set()
        worker.join(timeout=5)

        assert mock_pub.publish.call_count >= 1

    @patch("simulator.pump_worker.RabbitMQPublisher")
    def test_worker_handles_connection_failure(
        self,
        mock_pub_cls: MagicMock,
        sample_pump_def: PumpDefinition,
        sample_rabbitmq_config: RabbitMQConfig,
        stop_event: threading.Event,
    ) -> None:
        mock_pub = MagicMock()
        mock_pub.connect.side_effect = Exception("connection refused")
        mock_pub_cls.return_value = mock_pub

        worker = PumpWorker(
            pump_def=sample_pump_def,
            rabbitmq_config=sample_rabbitmq_config,
            interval_sec=0.1,
            fault_injector=None,
            stop_event=stop_event,
        )
        worker.start()
        worker.join(timeout=5)

        # Worker should exit gracefully without crashing
        assert not worker.is_alive()
