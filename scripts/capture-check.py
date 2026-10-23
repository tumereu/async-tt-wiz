#!/usr/bin/env python3
"""Retain command output losslessly as JSON (including trailing spaces/CRs).

Usage: python3 scripts/capture-check.py OUTPUT.json -- COMMAND [ARG ...]
The command's exit code is preserved. No shell interpretation or success rerolls.
"""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys


def main():
    output, separator, *command = sys.argv[1:]
    if separator != "--" or not command:
        raise SystemExit("Usage: capture-check.py OUTPUT.json -- COMMAND [ARG ...]")
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    record = {
        "command": command,
        "exitCode": result.returncode,
        "outputSha256": hashlib.sha256(result.stdout).hexdigest(),
    }
    try:
        record["output"] = result.stdout.decode("utf-8")
    except UnicodeDecodeError:
        record["outputBase64"] = base64.b64encode(result.stdout).decode("ascii")
    destination = Path(output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n")
    print(f"exitCode={result.returncode}; retained output: {output}")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
