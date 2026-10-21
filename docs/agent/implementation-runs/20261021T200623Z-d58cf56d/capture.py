#!/usr/bin/env python3
"""Retain an actual command's combined output and exit code."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

destination = Path(__file__).parent / sys.argv[1]
command = sys.argv[2:]
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
destination.write_text(json.dumps({
    "command": command,
    "exitCode": result.returncode,
    "outputSha256": hashlib.sha256(result.stdout).hexdigest(),
    "output": result.stdout.decode("utf-8", errors="strict"),
}, indent=2) + "\n")
print(result.stdout.decode("utf-8", errors="strict"))
print(f"Retained {destination}: exit={result.returncode}")
sys.exit(result.returncode)
