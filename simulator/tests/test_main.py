"""Tests for simulator.main — CLI and orchestration (smoke-level)."""

from __future__ import annotations

import signal
import threading
import time
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from simulator.main import _parse_args, main


class TestParseArgs:
    def test_defaults(self) -> None:
        args = _parse_args([])
        assert args.config == Path("config/simulator/config.yaml")
        assert args.log_level == "INFO"

    def test_custom_config(self, tmp_path: Path) -> None:
        p = tmp_path / "custom.yaml"
        args = _parse_args(["--config", str(p)])
        assert args.config == p

    def test_log_level(self) -> None:
        args = _parse_args(["--log-level", "DEBUG"])
        assert args.log_level == "DEBUG"


class TestMainFunction:
    @patch("simulator.main.start_metrics_server")
    @patch("simulator.main.PumpWorker")
    def test_main_starts_and_stops(
        self,
        mock_worker_cls: MagicMock,
        mock_metrics: MagicMock,
        config_yaml_path: Path,
    ) -> None:
        mock_worker = MagicMock()
        mock_worker.is_alive.return_value = False
        mock_worker_cls.return_value = mock_worker

        def _stop_after_delay() -> None:
            time.sleep(0.5)
            signal.raise_signal(signal.SIGINT)

        stopper = threading.Thread(target=_stop_after_delay, daemon=True)
        stopper.start()

        main(["--config", str(config_yaml_path)])

        mock_worker.start.assert_called_once()
        mock_worker.join.assert_called_once()
        mock_metrics.assert_called_once()
