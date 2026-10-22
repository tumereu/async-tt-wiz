#!/usr/bin/env python3
"""Entry point for the test-only owned Windows/WSL device controller."""
from pathlib import Path
import runpy
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
runpy.run_module('e2e.devices', run_name='__main__')
