"""Tests for simulator.logging_config — structured JSON logging."""

from __future__ import annotations

import json
import logging

from simulator.logging_config import JSONFormatter, setup_logging


class TestJSONFormatter:
    """Verify the structured log format."""

    def test_output_is_valid_json(self) -> None:
        formatter = JSONFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname="test.py",
            lineno=1,
            msg="hello %s",
            args=("world",),
            exc_info=None,
        )
        line = formatter.format(record)
        data = json.loads(line)
        assert data["message"] == "hello world"
        assert data["level"] == "INFO"

    def test_extra_fields_carried(self) -> None:
        formatter = JSONFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname="test.py",
            lineno=1,
            msg="event",
            args=(),
            exc_info=None,
        )
        record.pump_id = "PUMP_001"  # type: ignore[attr-defined]
        record.event_type = "telemetry_published"  # type: ignore[attr-defined]
        line = formatter.format(record)
        data = json.loads(line)
        assert data["pump_id"] == "PUMP_001"
        assert data["event_type"] == "telemetry_published"

    def test_timestamp_present(self) -> None:
        formatter = JSONFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.DEBUG,
            pathname="test.py",
            lineno=1,
            msg="ts test",
            args=(),
            exc_info=None,
        )
        data = json.loads(formatter.format(record))
        assert "timestamp" in data
        assert data["timestamp"].endswith("Z")


class TestSetupLogging:
    def test_root_logger_configured(self) -> None:
        setup_logging(level=logging.WARNING)
        root = logging.getLogger()
        assert root.level == logging.WARNING
        assert len(root.handlers) == 1
        assert isinstance(root.handlers[0].formatter, JSONFormatter)
