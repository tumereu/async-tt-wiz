#!/usr/bin/env python3
"""Cross-worktree exclusive test leases; nonblocking, with retained failure fences.

run --owner RUN --resource NAME [--resource NAME...] -- COMMAND...
recover --owner RUN --resource NAME... --cleanup-evidence PATH

Lease the whole provision/test/cleanup command, not individual device calls.
Isolated host checks need no shared lease. This coordinates this repository only.
"""
import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys


class Busy(RuntimeError):
    pass


def journal(root):
    common = Path(subprocess.check_output(["git", "rev-parse", "--git-common-dir"], cwd=root, text=True).strip())
    return (common if common.is_absolute() else root / common).resolve() / "implementation-loop/resources"


def atomic(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(path)


def name(value):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}", value):
        raise argparse.ArgumentTypeError("Lease names/owners must be simple IDs, not paths")
    return value


def lease(root, owner, resources, command=None, cleanup=None):
    directory = journal(root)
    directory.mkdir(parents=True, exist_ok=True)
    resources = sorted(set(resources))
    with ExitStack() as stack:
        handles, records = [], {}
        for resource in resources:
            # The orchestrator owns the existing serial writer fence too. A legacy
            # controller cannot accidentally run alongside managed worker lanes.
            lock_path = directory.parent / "writer.lock" if resource == "integration-writer" else directory / (resource + ".lock")
            handle = stack.enter_context(lock_path.open("a"))
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise Busy(f"Resource {resource} is in use; queue it, do not rerun or kill its owner")
            handles.append(handle)
            path = directory / (resource + ".json")
            records[resource] = json.loads(path.read_text()) if path.exists() else None
        # Acquire every lock before changing any record. Canonical order avoids
        # partial reservations/deadlocks when workers request overlapping bundles.
        if cleanup is not None:
            cleanup = (root / cleanup).resolve() if not cleanup.is_absolute() else cleanup.resolve()
            prefix = root / "docs/agent/implementation-runs" / owner
            if not cleanup.is_relative_to(root) or not (str(cleanup).startswith(str(prefix) + "/") or
                                                       str(cleanup).startswith(str(prefix) + "-")):
                raise Busy("Cleanup evidence must be in the retained owner's run namespace")
            data = cleanup.read_bytes()
            proof = json.loads(data)
            raw = proof.get("output", "").encode()
            if proof.get("outputSha256") != hashlib.sha256(raw).hexdigest() or not proof.get("command"):
                raise Busy("Cleanup evidence needs a captured command and matching output hash")
            if proof.get("exitCode") != 0 or any(record is None or record.get("owner") != owner
                                                for record in records.values()):
                raise Busy("Recovery needs matching retained owners, no running command and successful cleanup evidence")
            for resource, record in records.items():
                recovered = {**record, "cleanupEvidence": str(cleanup), "cleanupSha256": hashlib.sha256(data).hexdigest(),
                             "recoveredAt": datetime.now(timezone.utc).isoformat()}
                atomic(directory / (resource + ".recovered.json"), recovered)
                (directory / (resource + ".json")).unlink()
            return 0
        if any(records.values()):
            raise Busy("Retained resource fence: inspect the owner, perform exact cleanup and recover before reuse")
        record = {"owner": owner, "worktree": str(root), "resources": resources,
                  "pid": os.getpid(), "startedAt": datetime.now(timezone.utc).isoformat(), "command": command}
        for resource in resources:
            atomic(directory / (resource + ".json"), record)
        process, code = None, 1
        try:
            process = subprocess.Popen(command, cwd=root, start_new_session=True,
                                       pass_fds=tuple(handle.fileno() for handle in handles))
            # Children inherit the advisory locks: killing just this wrapper does
            # not make still-running owned commands appear free to another worker.
            def interrupt(signum, _frame):
                raise KeyboardInterrupt
            old = signal.signal(signal.SIGTERM, interrupt)
            try:
                code = process.wait()
            finally:
                signal.signal(signal.SIGTERM, old)
        except KeyboardInterrupt:
            if process is not None and process.poll() is None:
                os.killpg(process.pid, signal.SIGINT)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
            code = 130
        except OSError:
            code = 1
            raise
        finally:
            # Red/interrupted work retains the durable fence even after its OS
            # locks close. Recovery requires explicit owned-cleanup evidence.
            if process is not None and process.returncode == 0 and code == 0:
                for resource in resources:
                    (directory / (resource + ".json")).unlink()
        return code if code >= 0 else 128 - code


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["run", "recover"])
    parser.add_argument("--owner", required=True, type=name)
    parser.add_argument("--resource", required=True, action="append", type=name)
    parser.add_argument("--cleanup-evidence", type=Path)
    argv = sys.argv[1:]
    separator = argv.index("--") if "--" in argv else len(argv)
    options = parser.parse_args(argv[:separator])
    command = argv[separator + 1:]
    if options.mode == "run" and (not command or options.cleanup_evidence) or options.mode == "recover" and (command or not options.cleanup_evidence):
        parser.error("run needs COMMAND; recover needs only --cleanup-evidence")
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()).resolve()
    try:
        return lease(root, options.owner, options.resource, command, options.cleanup_evidence)
    except Busy as error:
        print(str(error), file=sys.stderr)
        return 75  # Scheduling contention, not a failed product assertion.
    except (OSError, ValueError) as error:
        print(f"Resource lease failed; preserve its fence: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
