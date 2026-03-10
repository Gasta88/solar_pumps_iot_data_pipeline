"""Tests for simulator.publisher — RabbitMQ publishing (unit-level, mocked)."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from simulator.config import RabbitMQConfig
from simulator.publisher import RabbitMQPublisher


@pytest.fixture()
def publisher(sample_rabbitmq_config: RabbitMQConfig) -> RabbitMQPublisher:
    return RabbitMQPublisher(sample_rabbitmq_config)


class TestRabbitMQPublisher:
    """All RabbitMQ interactions are mocked — no broker required."""

    @patch("simulator.publisher.pika.BlockingConnection")
    def test_connect_declares_topology(
        self, mock_conn_cls: MagicMock, publisher: RabbitMQPublisher
    ) -> None:
        mock_conn = MagicMock()
        mock_channel = MagicMock()
        mock_conn.channel.return_value = mock_channel
        mock_conn_cls.return_value = mock_conn

        publisher.connect()

        mock_channel.exchange_declare.assert_called_once_with(
            exchange="iot.data",
            exchange_type="topic",
            durable=True,
        )
        mock_channel.queue_declare.assert_called_once_with(
            queue="telemetry.raw",
            durable=True,
        )
        mock_channel.queue_bind.assert_called_once_with(
            queue="telemetry.raw",
            exchange="iot.data",
            routing_key="telemetry.raw",
        )

    @patch("simulator.publisher.pika.BlockingConnection")
    def test_publish_calls_basic_publish(
        self, mock_conn_cls: MagicMock, publisher: RabbitMQPublisher
    ) -> None:
        mock_conn = MagicMock()
        mock_channel = MagicMock()
        mock_channel.is_closed = False
        mock_conn.channel.return_value = mock_channel
        mock_conn_cls.return_value = mock_conn

        publisher.connect()
        publisher.publish('{"test": true}')

        mock_channel.basic_publish.assert_called_once()
        call_kwargs = mock_channel.basic_publish.call_args
        assert call_kwargs[1]["exchange"] == "iot.data"
        assert call_kwargs[1]["routing_key"] == "telemetry.raw"

    @patch("simulator.publisher.pika.BlockingConnection")
    def test_close_closes_connection(
        self, mock_conn_cls: MagicMock, publisher: RabbitMQPublisher
    ) -> None:
        mock_conn = MagicMock()
        mock_conn.is_open = True
        mock_conn.channel.return_value = MagicMock()
        mock_conn_cls.return_value = mock_conn

        publisher.connect()
        publisher.close()

        mock_conn.close.assert_called_once()

    @patch("simulator.publisher.pika.BlockingConnection")
    def test_publish_reconnects_on_failure(
        self, mock_conn_cls: MagicMock, publisher: RabbitMQPublisher
    ) -> None:
        mock_conn = MagicMock()
        mock_channel = MagicMock()
        mock_channel.is_closed = False
        mock_conn.channel.return_value = mock_channel
        mock_conn.is_open = True
        mock_conn_cls.return_value = mock_conn

        publisher.connect()

        # First publish fails, second succeeds
        from pika.exceptions import AMQPConnectionError

        mock_channel.basic_publish.side_effect = [
            AMQPConnectionError("oops"),
            None,
        ]

        publisher.publish('{"retry": true}')

        assert mock_channel.basic_publish.call_count == 2
