"""Read-only I01d parent transition, documentation and lossless evidence checks."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess


PREFIX = "docs/agent/implementation-runs/20261021T210246Z-d1993a37"
REPORT = PREFIX + ".json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01d", "findingIds": []}
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
        if identifier != "I01d":
            assert old[identifier] == new[identifier], identifier
    assert new["I01d"]["status"] == "implemented"
    assert new["I01d"]["implementationRecord"] == REPORT
    assert all(new[child]["status"] == "implemented" for child in new["I01d"]["children"])
    assert new["I01"]["status"] == "in-progress"
    assert api["selection"](after) == {**choice, "target": "I01e"}
    assert after["findings"] == []
    print("PASS actual authorized baseline/priority and driver state/transition validation")
    print("PASS only I01d parent completed after both children; I01e next, I01f/B1/I01 remain pending")
    print("PASS all other tasks/reviews/findings/authorization/model/permissions/dependencies/external gates unchanged")

    assert report["sessionID"] == "ses_e9fb9f13dffezfur4NOIZDLoQL"
    assert report["runId"] == "20261021T210246Z-d1993a37"
    assert report["outcome"] == "progress"
    assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
    paths = set(report["changedPaths"])
    assert len(paths) == len(report["changedPaths"])
    for check in report["checks"]:
        assert check["evidence"] in paths
        for path in check.get("supportPaths", []):
            assert path in paths and path.startswith(PREFIX + "-")
        if check["evidence"] == PREFIX + "-handoff-check.json":
            continue
        evidence = json.loads(Path(check["evidence"]).read_text())
        assert check["exitCode"] == evidence["exitCode"]
        assert check["kind"] == "diagnostic" or check["exitCode"] == 0
        assert hashlib.sha256(evidence["output"].encode()).hexdigest() == evidence["outputSha256"]
    for path in paths - {PREFIX + "-handoff-check.json"}:
        assert Path(path).is_file(), path
        if path.endswith(".json"):
            json.loads(Path(path).read_text())
    doc = Path("client-storage/README.md")
    text = doc.read_text()
    assert text.count("```") % 2 == 0
    for target in re.findall(r"\]\(([^)]+)\)", text):
        if not target.startswith(("http://", "https://", "#")):
            assert (doc.parent / target.split("#")[0]).is_file(), target
    actual = set(git("diff", "--name-only").splitlines())
    actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    assert actual in (paths, paths - {PREFIX + "-handoff-check.json"}), (actual - paths, paths - actual)
    assert all(path in {STATE, "client-storage/README.md"} or path.startswith(PREFIX) for path in actual)
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact owned changed-path inventory, lossless actual outputs/exit codes, no required red check, JSON/Markdown links/whitespace")
    print("PASS device instrumentation remains COMPILED NOT EXECUTED; no independent review/release/full product pass claimed")


if __name__ == "__main__":
    main()
