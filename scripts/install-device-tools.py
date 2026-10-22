#!/usr/bin/env python3
"""Install locked test-only Appium tools into an exclusive /tmp/opencode prefix."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--node', required=True)
    parser.add_argument('--npm', required=True)
    args = parser.parse_args()
    directory = Path(args.directory)
    assert directory.is_absolute() and directory.parent == Path('/tmp/opencode')
    assert not directory.is_symlink() and directory.resolve() == directory
    node, npm = Path(args.node), Path(args.npm)
    assert node.is_absolute() and node.is_file() and npm.is_absolute() and npm.is_file()
    assert not directory.exists(), 'Never replace an existing tool installation'
    directory.mkdir(mode=0o700)
    env = {k: v for k, v in os.environ.items() if not k.startswith(('APPIUM_', 'ADB_', 'ANDROID_', 'npm_config_'))}
    env['PATH'] = str(node.parent) + os.pathsep + env.get('PATH', '')
    env['APPIUM_HOME'] = str(directory / 'driver')
    provenance = {'node': str(node), 'npm': str(npm), 'inputs': {}, 'packages': {}}

    def run(command, cwd=directory):
        result = subprocess.run(command, cwd=cwd, env=env, capture_output=True, text=True, timeout=300)
        print(json.dumps({'command': command, 'cwd': str(cwd), 'exitCode': result.returncode,
                          'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        assert result.returncode == 0, 'Tool installation/qualification failed; retained prefix is not ready'
        return result.stdout

    run([str(node), '--version'])
    run([str(node), str(npm), '--version'])
    for component in ('appium', 'driver'):
        target = directory / component
        target.mkdir()
        for name in ('package.json', 'package-lock.json'):
            source = ROOT / 'e2e/device-tools' / component / name
            shutil.copyfile(source, target / name)
            provenance['inputs'][component + '/' + name] = hashlib.sha256(source.read_bytes()).hexdigest()
        run([str(node), str(npm), 'ci', '--no-audit', '--no-fund'], cwd=target)
        for name in ('package.json', 'package-lock.json'):
            assert hashlib.sha256((target / name).read_bytes()).hexdigest() == provenance['inputs'][component + '/' + name]
        packages = {}
        for entry in (target / 'node_modules').glob('*/package.json'):
            package = json.loads(entry.read_text())
            packages[package['name']] = {'version': package['version'], 'license': package.get('license'),
                                         'manifestSha256': hashlib.sha256(entry.read_bytes()).hexdigest()}
        for entry in (target / 'node_modules').glob('@*/*/package.json'):
            package = json.loads(entry.read_text())
            packages[package['name']] = {'version': package['version'], 'license': package.get('license'),
                                         'manifestSha256': hashlib.sha256(entry.read_bytes()).hexdigest()}
        provenance['packages'][component] = packages
    main_js = str(directory / 'appium/node_modules/appium/build/lib/main.js')
    run([str(node), main_js, '--version'])
    installed = json.loads(run([str(node), main_js, 'driver', 'list', '--installed', '--json']))
    assert installed['uiautomator2']['version'] == '8.7.0'
    assert installed['uiautomator2']['installPath'] == str(directory / 'driver/node_modules/appium-uiautomator2-driver')
    provenance['appiumMain'] = main_js
    provenance['appiumHome'] = env['APPIUM_HOME']
    (directory / 'qualified.json').write_text(json.dumps(provenance, indent=2) + '\n')
    print(json.dumps(provenance, indent=2), flush=True)


if __name__ == '__main__':
    main()
