#!/usr/bin/env python3
"""Test-only real Compose lifecycle (not production orchestration)."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from e2e.environment import main

if __name__ == "__main__":
    main()
