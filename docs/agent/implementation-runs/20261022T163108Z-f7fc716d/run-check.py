"""Retain exact current-unit command output, including failed diagnostics, without shell pipelines."""
import json
from pathlib import Path
import subprocess
import sys
import time

name, *command = sys.argv[1:]
started = time.time()
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
path = Path(__file__).parent / (name + '.json')
path.write_text(json.dumps(dict(command=command, startedAt=started, elapsedSeconds=time.time()-started,
    exitCode=result.returncode, output=result.stdout), indent=2) + '\n')
print(result.stdout[-4000:])
print(path, 'exitCode', result.returncode)
sys.exit(result.returncode)
