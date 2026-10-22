#!/usr/bin/env python3
"""Run only against an explicitly owned, combined normal-APK/Compose device."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from e2e.device_runner import main

if __name__ == '__main__':
    raise SystemExit(main())
