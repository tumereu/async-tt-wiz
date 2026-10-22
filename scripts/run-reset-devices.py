#!/usr/bin/env python3
"""Explicit two-owned-device normal-APK reset test; no second implementation loop."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from e2e.reset_runner import main

if __name__ == '__main__':
    main()
