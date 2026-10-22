"""Retain this unit's foreground command output without hiding its exit status."""
import json
from pathlib import Path
import subprocess
import sys

path = Path(__file__).parent / (sys.argv[1] + '.json')
command = sys.argv[2:]
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
path.write_text(json.dumps(dict(command=command, exitCode=result.returncode, output=result.stdout), indent=2) + '\n')
print(result.stdout)
sys.exit(result.returncode)
