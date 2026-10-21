"""Read-only I01d2 transition/owned-evidence check. Never launches a session or device."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


PREFIX = "docs/agent/implementation-runs/20261021T204702Z-a524f8a6"
REPORT = PREFIX + ".json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01d2", "findingIds": []}
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
        if identifier != "I01d2":
            assert old[identifier] == new[identifier], identifier
    assert new["I01d2"]["status"] == "implemented"
    assert new["I01d2"]["implementationRecord"] == REPORT
    assert new["I01d"]["status"] == "in-progress"
    assert api["selection"](after) == {**choice, "target": "I01d"}
    assert new["I01f"]["dependsOn"] == old["I01f"]["dependsOn"]
    assert after["findings"] == []
    print("PASS actual authorized baseline/priority and pure driver state/transition validation")
    print("PASS only I01d2 implemented; I01d combined qualification next; B1 review/I01f device execution remain pending")
    print("PASS all other tasks/findings/reviews/authorization/model/permissions/dependencies/external gates unchanged")

    assert report["sessionID"] == "ses_e9fc8568effe8Rs71dCRNWKdYV"
    assert report["runId"] == "20261021T204702Z-a524f8a6"
    assert report["outcome"] == "progress"
    assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
    paths = set(report["changedPaths"])
    assert len(paths) == len(report["changedPaths"])
    for check in report["checks"]:
        assert check["evidence"] in paths
        if check["evidence"] == PREFIX + "-handoff-check.json":
            continue
        evidence = json.loads(Path(check["evidence"]).read_text())
        assert check["exitCode"] == evidence["exitCode"]
        assert check["kind"] == "diagnostic" or check["exitCode"] == 0
        assert hashlib.sha256(evidence["output"].encode()).hexdigest() == evidence["outputSha256"]
        for path in check.get("supportPaths", []):
            assert path in paths and path.startswith(PREFIX + "-")
    for path in paths - {PREFIX + "-handoff-check.json"}:
        assert Path(path).is_file(), path
        if path.endswith(".json"):
            json.loads(Path(path).read_text())
    assert Path("client-storage/README.md").read_text().count("```") % 2 == 0
    actual = set(git("diff", "--name-only").splitlines())
    actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    assert actual in (paths, paths - {PREFIX + "-handoff-check.json"}), (actual - paths, paths - actual)
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact owned changed-path inventory, lossless actual output/exit codes, no required red checks, JSON/Markdown/whitespace")
    print("PASS Android instrumentation remains COMPILED NOT EXECUTED; no auth/sync/game/upgrade/release pass claimed")


if __name__ == "__main__":
    main()
