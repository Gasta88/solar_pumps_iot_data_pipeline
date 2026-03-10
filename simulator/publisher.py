"""RabbitMQ publisher with automatic reconnection."""

from __future__ import annotations

import logging
import time
from typing import Optional

import pika
from pika.exceptions import AMQPConnectionError, AMQPChannelError

from simulator.config import RabbitMQConfig

logger = logging.getLogger(__name__)


class RabbitMQPublisher:
    """Thread-safe-ish RabbitMQ publisher.

    Each pump thread owns its own ``RabbitMQPublisher`` instance so there
    is no shared channel state.  The class handles transient connection
    failures by reconnecting with exponential back-off.
    """

    MAX_RETRIES = 5
    BASE_BACKOFF_SEC = 1.0

    def __init__(self, config: RabbitMQConfig) -> None:
        self._config = config
        self._connection: Optional[pika.BlockingConnection] = None
        self._channel: Optional[pika.adapters.blocking_connection.BlockingChannel] = None

    # ------------------------------------------------------------------
    # Connection lifecycle
    # ------------------------------------------------------------------

    def connect(self) -> None:
        """Open a blocking connection and declare the exchange."""
        credentials = pika.PlainCredentials(
            self._config.username, self._config.password
        )
        params = pika.ConnectionParameters(
            host=self._config.host,
            port=self._config.port,
            credentials=credentials,
            heartbeat=self._config.heartbeat,
            blocked_connection_timeout=self._config.connection_timeout,
        )

        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                self._connection = pika.BlockingConnection(params)
                self._channel = self._connection.channel()
                self._channel.exchange_declare(
                    exchange=self._config.exchange,
                    exchange_type="topic",
                    durable=True,
                )
                logger.info(
                    "Connected to RabbitMQ",
                    extra={
                        "event_type": "rabbitmq_connected",
                        "host": self._config.host,
                        "port": self._config.port,
                    },
                )
                return
            except (AMQPConnectionError, ConnectionError, OSError) as exc:
                wait = self.BASE_BACKOFF_SEC * (2 ** (attempt - 1))
                logger.warning(
                    "RabbitMQ connection attempt %d/%d failed: %s  "
                    "(retrying in %.1fs)",
                    attempt,
                    self.MAX_RETRIES,
                    exc,
                    wait,
                    extra={"event_type": "rabbitmq_retry"},
                )
                time.sleep(wait)

        raise AMQPConnectionError(
            f"Failed to connect to RabbitMQ after {self.MAX_RETRIES} attempts"
        )

    def close(self) -> None:
        """Gracefully close the connection."""
        try:
            if self._connection and self._connection.is_open:
                self._connection.close()
                logger.info(
                    "RabbitMQ connection closed",
                    extra={"event_type": "rabbitmq_disconnected"},
                )
        except Exception:  # noqa: BLE001
            logger.debug("Error during RabbitMQ close", exc_info=True)

    # ------------------------------------------------------------------
    # Publishing
    # ------------------------------------------------------------------

    def publish(self, body: str, routing_key: str | None = None) -> None:
        """Publish a UTF-8 string to the configured exchange.

        Re-establishes the connection once on transient failure before
        propagating the exception.
        """
        rk = routing_key or self._config.routing_key

        try:
            self._publish_once(body, rk)
        except (AMQPConnectionError, AMQPChannelError, ConnectionError, OSError):
            logger.warning(
                "Publish failed; reconnecting",
                extra={"event_type": "rabbitmq_reconnect"},
            )
            self.close()
            self.connect()
            self._publish_once(body, rk)

    def _publish_once(self, body: str, routing_key: str) -> None:
        if self._channel is None or self._channel.is_closed:
            raise AMQPConnectionError("Channel is not open")

        self._channel.basic_publish(
            exchange=self._config.exchange,
            routing_key=routing_key,
            body=body.encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,  # persistent
            ),
        )
