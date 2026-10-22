"""Retain actual foreground command output for this unit."""
import json
import subprocess
import sys
from pathlib import Path

name, *command = sys.argv[1:]
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
Path(__file__).with_name(name + '.json').write_text(json.dumps(dict(command=command,
    exitCode=result.returncode, output=result.stdout), indent=2) + '\n')
print(result.stdout)
sys.exit(result.returncode)
