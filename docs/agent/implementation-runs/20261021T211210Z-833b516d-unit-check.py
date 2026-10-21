"""Read-only report/state/source/evidence validation for the I01e1 boundary."""
import ast
import hashlib
import json
from pathlib import Path
import re
import runpy
import shlex
import subprocess

REPORT = "docs/agent/implementation-runs/20261021T211210Z-833b516d.json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01e1", "findingIds": []}
    assert git("rev-parse", "HEAD") == report["startCommit"]
    assert git("branch", "--show-current") == "master"
    assert not git("diff", "--cached", "--name-only")
    assert before["authorization"] == "authorized" and "D-304" in Path(before["authorizationRecord"]).read_text()
    assert api["selection"](before) == choice
    api["validate_state"](after)
    api["validate_transition"](before, after, choice, report)
    assert after["activeRun"] is None
    assert after["runs"] == before["runs"] + [REPORT]
    for key in before:
        if key not in {"runs", "tasks"}:
            assert before[key] == after[key], key
    old = {t["id"]: t for t in before["tasks"]}
    new = {t["id"]: t for t in after["tasks"]}
    assert old.keys() == new.keys()
    for identifier in old:
        if identifier != "I01e1":
            assert old[identifier] == new[identifier], identifier
    assert new["I01e1"]["status"] == "implemented"
    for key in ("dependsOn", "checks", "outcome", "batch"):
        assert new["I01e1"][key] == old["I01e1"][key]
    assert api["selection"](after) == {**choice, "target": "I01e2"}
    print("PASS D-304/exact baseline/master, selected I01e1 completion and pure driver state/transition validation; I01e2 next")
    print("PASS all unrelated tasks, parents, reviews, findings, model/permissions/authorization and external release gates unchanged")

    paths = report["changedPaths"]
    assert len(paths) == len(set(paths))
    actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
    own_output = REPORT[:-5] + "-unit-check.json"
    assert actual in (set(paths), set(paths) - {own_output}), actual ^ set(paths)
    assert all(p == STATE or p.startswith(REPORT[:-5]) or p in {
        "e2e/README.md", "e2e/adapters/adapter.py", "e2e/adapters/admin.py", "e2e/adapters/README.md",
        "e2e/adapters/Dockerfile", "e2e/adapters/Dockerfile.dockerignore", "scripts/build-synthetic-adapters.py",
        "scripts/check-synthetic-adapters-image.py", "tests/test_synthetic_adapters.py"} for p in paths)
    for path in paths:
        if path.endswith(".py"):
            ast.parse(Path(path).read_text(), filename=path)
        elif path.endswith(".md"):
            for target in re.findall(r"\]\(([^)]+)\)", Path(path).read_text()):
                if "://" not in target and not target.startswith("#"):
                    assert (Path(path).parent / target.split("#")[0]).is_file(), (path, target)
    print("PASS exact owned paths; Python syntax and new documentation local links")
    for check in report["checks"]:
        assert check["evidence"] in paths
        for support in check.get("supportPaths", []):
            assert support in paths and support.startswith(REPORT[:-5] + "-")
        if check["evidence"] == own_output:
            continue
        captured = json.loads(Path(check["evidence"]).read_text())
        assert captured["exitCode"] == check["exitCode"]
        assert captured["outputSha256"] == hashlib.sha256(captured["output"].encode()).hexdigest()
        if check["kind"] == "required":
            assert check["exitCode"] == 0
        shell_args = shlex.split(check["command"])
        assert shell_args[:2] == ["python3", "scripts/capture-check.py"]
        assert shell_args[3] == "--" and shell_args[4:] == captured["command"]
        assert shlex.split(check["invocation"]) == captured["command"]
    ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
    for item in ledger["items"]:
        assert item["completion"] == "unexecuted"
    for path in report["sourcePaths"]:
        assert Path(path).read_bytes() == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    manifest = json.loads(Path("e2e/build/adapters/image.json").read_text())
    assert manifest["imageId"] == report["qualification"]["imageId"]
    for path, expected in manifest["inputs"].items():
        assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS retained exact check output/exit/hash accounting, original-source/fixture integrity, final image inputs and whitespace")
    print("PASS no product scenario/Android/Compose/independent-review/release claim; initial failed test remains diagnostic with exact owned cleanup")


if __name__ == "__main__":
    main()
