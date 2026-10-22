"""Retain actual run-owned launch logs and tool manifest provenance only."""
import hashlib
import json
from pathlib import Path

instances = ('3da4bb54f5eb4f069a76b0a2c1db514c', '4e4aa4f6959340149f8610206f1f1661',
             '0262904813bd40eab878e4f9f0ea5c8a', '588f2d11cb934eb4a97e4d2ae670fd96',
             'f66dc58d08154120ac0bd317c1a7879f', '859b213df1784ff1bf32b05fa8a6f38e',
             'cebbcf988acc4a7ea1c3392560636829', '49e933a75b0f49c5ae0a27714f1436d2',
             '3df6efc0059f410ab2e397ea1958cb17', '79566d3f599144f88d9bed69467fc53d')
for identity in instances:
    directory = Path('/tmp/opencode/atw-device-' + identity)
    for name in ('instance.json', 'baseline-windows.json', 'windows-start.out', 'windows-start.err',
                 'capture.json', 'cleaned.json', 'versions.json'):
        path = directory / name
        if path.exists():
            data = path.read_bytes()
            print(json.dumps({'instance': identity, 'file': name, 'sha256': hashlib.sha256(data).hexdigest(),
                              'output': data.decode(errors='replace')}))
for path in (Path('/tmp/opencode/atw-device-tools/package-lock.json'),
             Path('/tmp/opencode/atw-device-tools/node_modules/appium/package.json'),
             Path('/tmp/opencode/atw-device-tools/appium-home/package-lock.json'),
             Path('/tmp/opencode/atw-device-tools/appium-home/node_modules/appium-uiautomator2-driver/package.json')):
    data = path.read_bytes()
    manifest = json.loads(data)
    print(json.dumps({'path': str(path), 'sha256': hashlib.sha256(data).hexdigest(),
                      'manifest': manifest}))
