"""Ordinary incremental image builder with this lane's invocation-only Gradle capacity limit."""
import importlib.util
from pathlib import Path
import sys

spec = importlib.util.spec_from_file_location('builder', Path('scripts/build-server-image.py'))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
original = builder.run
def run(*args):
    if args[0] == './scripts/gradle.sh':
        args = (*args, '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx1280m -Dfile.encoding=UTF-8')
    original(*args)
builder.run = run
builder.main()
