"""Read-only validation of the I01d split; never start a loop or application."""
import copy
import json
from pathlib import Path
import runpy
import subprocess


REPORT = "docs/agent/implementation-runs/20261021T202813Z-3cfbf7c6.json"
STATE = "docs/agent/implementation-state.json"
CHILDREN = ["I01d1", "I01d2"]


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
    assert before["mode"] == "active" and before["authorization"] == "authorized"
    assert "D-304" in Path(before["authorizationRecord"]).read_text()
    assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
    assert api["selection"](before) == choice
    assert before["findings"] == []
    print("PASS exact baseline/master, clean index, D-304 authorization and supplied I01d priority")

    api["validate_state"](after)
    api["validate_transition"](before, after, choice, report)
    assert after["activeRun"] is None
    assert after["runs"] == before["runs"] + [REPORT]
    for key in before:
        if key not in {"tasks", "runs"}:
            assert after[key] == before[key], key
    old = {task["id"]: task for task in before["tasks"]}
    new = {task["id"]: task for task in after["tasks"]}
    assert set(new) - set(old) == set(CHILDREN)
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != "I01d")
    assert new["I01d"] == {**old["I01d"], "status": "in-progress", "children": CHILDREN,
                          "planningRecord": REPORT}
    for identifier in CHILDREN:
        task = new[identifier]
        assert task["batch"] == "B1" and task["status"] == "pending"
        assert task["outcome"].strip() and task["checks"]
        assert all(isinstance(check, str) and check.strip() for check in task["checks"])
        assert task["dependsOn"] and "I01d" not in task["dependsOn"]
        assert "I01f" not in task["dependsOn"]
    assert new["I01d1"]["dependsOn"] == ["I01a"]
    assert new["I01d2"]["dependsOn"] == ["I01d1"]
    assert "I01d" in new["I01f"]["dependsOn"]
    print("PASS pure driver state/transition validation; pending same-B1 children and original parent scope retained")
    print("PASS no I01f reverse dependency; Android driver execution remains after adapter qualification")
    print("PASS unrelated tasks, findings, reviews, authorization, runner, permissions and external gates unchanged")

    # Simulate only in memory: prove selection reaches the parent qualification
    # before Compose, without completing any implementation in saved state.
    simulated = copy.deepcopy(after)
    by_id = {task["id"]: task for task in simulated["tasks"]}
    for identifier in CHILDREN:
        assert api["selection"](simulated) == {**choice, "target": identifier}
        by_id[identifier]["status"] = "implemented"
    assert api["selection"](simulated) == choice
    by_id["I01d"]["status"] = "implemented"
    assert api["selection"](simulated) == {**choice, "target": "I01e"}
    print("PASS simulated I01d1 -> I01d2 -> I01d qualification -> I01e; no child independent review")

    ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
    items = {item["id"]: item for item in ledger["items"]}
    for identifier in report["planning"]["sourceIds"]["directScenarioPortions"]:
        assert "I01" in items[identifier]["implementationDependencies"]
        assert items[identifier]["completion"] == "unexecuted"
    for identifier in report["planning"]["sourceIds"]["architectureConstraintPortions"]:
        assert items[identifier]["completion"] == "unexecuted"
    assert items["T-632"]["implementationDependencies"] == ["I35"]
    for path in report["sourcePaths"]:
        assert Path(path).is_file(), path
        assert Path(path).read_bytes() == subprocess.check_output(
            ["git", "show", report["startCommit"] + ":" + path]), path
    print("PASS source references unchanged; T-662 I01 ownership and later-scenario unexecuted accounting retained")

    assert report["runId"] == "20261021T202813Z-3cfbf7c6"
    assert report["sessionID"] == "ses_e9fd99207ffeb7lXJYWYZpkWZ1"
    assert report["mode"] == choice["mode"] and report["target"] == choice["target"]
    assert report["outcome"] == "progress"
    assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
    paths = report["changedPaths"]
    assert len(paths) == len(set(paths)) == 5
    assert set(paths) == {STATE, REPORT, REPORT[:-5] + "-planning-check.py",
                          REPORT[:-5] + "-planning-check.json", REPORT[:-5] + "-coverage-check.json"}
    for check in report["checks"]:
        assert check["kind"] == "required" and check["exitCode"] == 0
        assert check["evidence"] in paths
        for support in check.get("supportPaths", []):
            assert support in paths and support.startswith(REPORT[:-5] + "-")
    actual = set(git("diff", "--name-only").splitlines())
    actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    # capture-check atomically writes its own evidence after this process exits.
    assert actual == set(paths) - {REPORT[:-5] + "-planning-check.json"} or actual == set(paths)
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact planning-only owned paths and whitespace; no runtime/build/UI/driver changes")
    print("PASS planning validation only; no task completion, application/device pass, independent review or release claim")


if __name__ == "__main__":
    main()
