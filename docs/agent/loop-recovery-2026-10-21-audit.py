"""Revalidate the preserved I01c planning handoff without starting a session."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import types

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
RUN = "20261021T182623Z-26bd32d9"
REPORT = f"docs/agent/implementation-runs/{RUN}.json"
SAVED = "89f0a0228d59fa4e025ea071b7f37baae5a8ba87"
STATE = "docs/agent/implementation-state.json"
HELPER = REPORT[:-5] + "-planning-check.py"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    original = json.loads(git("show", f"{SAVED}:{REPORT}"))
    report = json.loads((ROOT / REPORT).read_text())
    expected = copy.deepcopy(original)
    expected["checks"][1]["supportPaths"] = [HELPER]
    assert report == expected, "Recovery must only declare the original check helper"
    before = json.loads(git("show", f"{original['startCommit']}:{STATE}"))
    after = json.loads(git("show", f"{SAVED}:{STATE}"))
    assert (ROOT / STATE).read_bytes() == git("show", f"{SAVED}:{STATE}")
    actual = set(git("diff", "--name-only", original["startCommit"], SAVED).decode().splitlines())
    assert actual == set(original["changedPaths"])

    # Reproduce the exact original guard failure using its preserved driver bytes.
    old = types.ModuleType("old_planning_guard")
    old.__file__ = str(ROOT / "scripts/implementation-loop.py")
    exec(compile(git("show", f"{SAVED}:scripts/implementation-loop.py"), old.__file__, "exec"), old.__dict__)
    choice = {"mode": "implement", "target": "I01c", "findingIds": []}
    try:
        old.Runner(ROOT).role_paths(before, choice, original, actual, REPORT)
    except old.Halt as error:
        assert str(error) == f"Product changes forbidden in implement/planning unit: {HELPER}"
        print("PASS exact original planning-helper rejection reproduced")
    else:
        raise AssertionError("Original guard unexpectedly accepted the helper")

    spec = importlib.util.spec_from_file_location("repaired_loop", ROOT / "scripts/implementation-loop.py")
    loop = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loop)
    loop.validate_state(after)
    loop.validate_transition(before, after, choice, report)
    runner = loop.Runner(ROOT)
    runner.role_paths(before, choice, report, actual, REPORT)
    for check in report["checks"]:
        path = check["evidence"]
        assert (ROOT / path).read_bytes() == git("show", f"{SAVED}:{path}")
        evidence = json.loads((ROOT / path).read_text())
        assert evidence["exitCode"] == check["exitCode"] == 0
        assert hashlib.sha256(evidence["output"].encode()).hexdigest() == evidence["outputSha256"]
    assert (ROOT / HELPER).read_bytes() == git("show", f"{SAVED}:{HELPER}")
    runner.committed_file(HELPER, SAVED)
    assert loop.selection(after) == {**choice, "target": "I01c1"}
    info = runner.client.info(report["sessionID"])
    assert info["model"] == loop.MODEL_REF and info["outcome"] == "succeeded"
    assert not runner.client.is_active(report["sessionID"])
    print("PASS corrected guard, exact transition, original evidence/hashes/helper and unchanged state")
    print("PASS original exact-high session is succeeded/idle; next selection is I01c1")
    print("PASS no task, review, release gate or original private journal promoted/rewritten")
    for path in ["scripts/implementation-loop.py", loop.PROMPT, STATE, HELPER]:
        print(hashlib.sha256((ROOT / path).read_bytes()).hexdigest(), path)


if __name__ == "__main__":
    main()
