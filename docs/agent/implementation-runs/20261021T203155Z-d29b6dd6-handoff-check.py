"""Read-only handoff validation for I01d1. Never create a loop/session/device."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


PREFIX = "docs/agent/implementation-runs/20261021T203155Z-d29b6dd6"
REPORT = PREFIX + ".json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def generated_manifest(path):
    text = json.loads(Path(path).read_text())["output"]
    line = next(line for line in text.splitlines() if line.startswith("PASS SQLDelight real generated"))
    return json.loads(line.split(": ", 1)[1])


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01d1", "findingIds": []}
    assert git("rev-parse", "HEAD") == report["startCommit"]
    assert git("branch", "--show-current") == "master"
    assert not git("diff", "--cached", "--name-only")
    assert before["authorization"] == "authorized" and "D-304" in Path(before["authorizationRecord"]).read_text()
    assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
    assert api["selection"](before) == choice
    api["validate_state"](after)
    api["validate_transition"](before, after, choice, report)
    assert after["activeRun"] is None
    assert after["runs"] == before["runs"] + [REPORT]
    for key in before:
        if key not in {"tasks", "runs"}:
            assert after[key] == before[key], key
    old = {task["id"]: task for task in before["tasks"]}
    new = {task["id"]: task for task in after["tasks"]}
    assert old.keys() == new.keys()
    for identifier in old:
        if identifier != "I01d1":
            assert old[identifier] == new[identifier], identifier
    assert new["I01d1"]["status"] == "implemented"
    assert new["I01d1"]["implementationRecord"] == REPORT
    assert new["I01d"]["status"] == "in-progress"
    assert api["selection"](after) == {**choice, "target": "I01d2"}
    assert after["findings"] == []
    print("PASS exact authorized clean starting baseline, selected I01d1 priority and pure driver transition validation")
    print("PASS only I01d1 implemented; I01d parent/B1 review open; I01d2 next, no reverse device dependency")
    print("PASS all other tasks/reviews/findings/authorization/runner/permissions/external release gates unchanged")

    assert report["sessionID"] == "ses_e9fd6310dffeDVxo2TgnIOZEmg"
    assert report["runId"] == "20261021T203155Z-d29b6dd6"
    assert report["outcome"] == "progress"
    assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
    assert generated_manifest(PREFIX + "-artifacts-pass.json") == generated_manifest(PREFIX + "-artifacts-final.json")
    print("PASS actual SQLDelight-generated schema/query bytes identical across independent clean regenerations and both variants")
    paths = set(report["changedPaths"])
    assert len(paths) == len(report["changedPaths"])
    for check in report["checks"]:
        assert check["evidence"] in paths
        if check["evidence"] == PREFIX + "-handoff-check.json":
            continue  # capture-check atomically creates this output after exit.
        evidence = json.loads(Path(check["evidence"]).read_text())
        assert check["exitCode"] == evidence["exitCode"]
        assert check["kind"] == "diagnostic" or check["exitCode"] == 0
        data = evidence["output"].encode("utf-8")
        assert hashlib.sha256(data).hexdigest() == evidence["outputSha256"]
        for path in check.get("supportPaths", []):
            assert path in paths and path.startswith(PREFIX + "-")
    assert all(Path(path).exists() for path in paths - {PREFIX + "-handoff-check.json"})
    actual = set(git("diff", "--name-only").splitlines())
    actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    assert actual in (paths, paths - {PREFIX + "-handoff-check.json"}), (actual - paths, paths - actual)
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact owned changed-path inventory, retained check bytes/exit codes, no required red check and whitespace")
    print("PASS I01d1 storage foundation only; no Android execution/auth/game/sync/full-tree/independent-review/release pass claimed")


if __name__ == "__main__":
    main()
