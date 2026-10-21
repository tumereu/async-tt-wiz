"""Read-only validation of this planning unit; never start a loop or service."""
import copy
import json
from pathlib import Path
import runpy
import subprocess


REPORT = "docs/agent/implementation-runs/20261021T182623Z-26bd32d9.json"
STATE = "docs/agent/implementation-state.json"
CHILDREN = ["I01c1", "I01c2", "I01c3"]


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01c", "findingIds": []}

    assert git("rev-parse", "HEAD") == report["startCommit"]
    assert git("branch", "--show-current") == "master"
    assert not git("diff", "--cached", "--name-only")
    assert before["mode"] == "active" and before["authorization"] == "authorized"
    assert "D-304" in Path(before["authorizationRecord"]).read_text()
    assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
    assert api["selection"](before) == choice
    assert before["findings"] == []
    print("PASS exact baseline/master, clean index, D-304 authorization and supplied I01c priority")

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
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != "I01c")
    expected_parent = {**old["I01c"], "status": "in-progress", "children": CHILDREN,
                       "planningRecord": REPORT}
    assert new["I01c"] == expected_parent
    for identifier in CHILDREN:
        task = new[identifier]
        assert task["batch"] == "B1" and task["status"] == "pending"
        assert task["outcome"].strip() and task["checks"]
        assert all(isinstance(check, str) and check.strip() for check in task["checks"])
        assert task["dependsOn"] and "I01c" not in task["dependsOn"]
    assert new["I01c1"]["dependsOn"] == ["I01a"]
    assert new["I01c2"]["dependsOn"] == ["I01c1"]
    assert new["I01c3"]["dependsOn"] == ["I01c1", "I01c2"]
    print("PASS pure driver state/transition validation; three pending same-B1 children; original parent scope retained")
    print("PASS unrelated tasks, findings, reviews, authorization, runner, permissions and release gates unchanged")

    # Exercise only in-memory selection copies, not task completion in saved state.
    simulated = copy.deepcopy(after)
    by_id = {task["id"]: task for task in simulated["tasks"]}
    for identifier in CHILDREN:
        assert api["selection"](simulated) == {**choice, "target": identifier}
        by_id[identifier]["status"] = "implemented"
    assert api["selection"](simulated) == choice
    by_id["I01c"]["status"] = "implemented"
    assert api["selection"](simulated) == {**choice, "target": "I01d"}
    print("PASS simulated order I01c1 -> I01c2 -> I01c3 -> I01c qualification -> I01d; no child review added")

    ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
    items = {item["id"]: item for item in ledger["items"]}
    for identifier in report["planning"]["sourceIds"]["directScenarioPortions"]:
        assert "I01" in items[identifier]["implementationDependencies"]
        assert items[identifier]["completion"] == "unexecuted"
    for path in report["sourcePaths"]:
        assert Path(path).is_file(), path
        assert Path(path).read_bytes() == subprocess.check_output(
            ["git", "show", report["startCommit"] + ":" + path]), path
    print("PASS source references exist unchanged; T-630/T-662 retain I01 ownership and unexecuted accounting")

    assert report["runId"] == "20261021T182623Z-26bd32d9"
    assert report["sessionID"] == "ses_ea0491e44ffePodiQ3nc5olXCO"
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
    actual = set(git("diff", "--name-only").splitlines())
    actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    # capture-check writes its own output atomically after this command exits.
    assert actual == set(paths) - {REPORT[:-5] + "-planning-check.json"} or actual == set(paths)
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact planning-only owned paths and whitespace; no runtime/build/UI/driver changes")
    print("PASS planning validation only; no application check, task completion, independent review or gate pass claimed")


if __name__ == "__main__":
    main()
