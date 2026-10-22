"""Retain exact command output for this bounded implementation unit."""
import json
from pathlib import Path
import subprocess
import sys

label, *command = sys.argv[1:]
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
path = Path(__file__).with_name('20261022T013255Z-043555f3-' + label + '.json')
path.write_text(json.dumps({'command': command, 'exitCode': result.returncode, 'output': result.stdout}, indent=2) + '\n')
print(result.stdout)
raise SystemExit(result.returncode)
