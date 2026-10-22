"""Read-only I01f split validation; no driver, services or devices are started."""
import copy
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


REPORT = "docs/agent/implementation-runs/20261021T235653Z-f324bb89.json"
STATE = "docs/agent/implementation-state.json"
CHILDREN = ["I01f1", "I01f2", "I01f3"]


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01f", "findingIds": []}

    assert git("rev-parse", "HEAD") == report["startCommit"]
    assert git("branch", "--show-current") == "master"
    assert not git("diff", "--cached", "--name-only")
    assert before["mode"] == "active" and before["authorization"] == "authorized"
    assert "D-304" in Path(before["authorizationRecord"]).read_text()
    assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
    assert before["findings"] == []
    assert api["selection"](before) == choice
    print("PASS exact start commit/master, empty index, D-304 authorization and supplied I01f priority")

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
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != "I01f")
    assert new["I01f"] == {**old["I01f"], "status": "in-progress", "children": CHILDREN,
                           "planningRecord": REPORT}
    for identifier in CHILDREN:
        task = new[identifier]
        assert task["batch"] == "B1" and task["status"] == "pending"
        assert task["outcome"].strip() and task["checks"]
        assert all(isinstance(check, str) and check.strip() for check in task["checks"])
        assert task["dependsOn"] and not set(task["dependsOn"]) & {"I01", "I01f", "I02"}
    assert new["I01f1"]["dependsOn"] == old["I01f"]["dependsOn"]
    assert new["I01f2"]["dependsOn"] == ["I01f1"]
    assert new["I01f3"]["dependsOn"] == ["I01f2"]
    assert all(old[identifier]["status"] == "implemented" for identifier in old["I01f"]["dependsOn"])
    assert new["I01"]["children"] == ["I01a", "I01b", "I01c", "I01d", "I01e", "I01f"]
    print("PASS pure driver state/transition validation; three pending same-B1 children with explicit dependencies")
    print("PASS I01f original outcome/all four checks and I01 foundation-wide qualification unchanged")
    print("PASS unrelated tasks, findings, group reviews, authorization/model/permissions and external gates unchanged")

    # Scheduling experiment is in memory only, never saved to actual task state.
    simulated = copy.deepcopy(after)
    by_id = {task["id"]: task for task in simulated["tasks"]}
    for identifier in CHILDREN + ["I01f", "I01", "I02", "I03", "I04"]:
        assert api["selection"](simulated) == {**choice, "target": identifier}, identifier
        by_id[identifier]["status"] = "implemented"
    assert api["selection"](simulated) == {"mode": "review", "target": "B1", "findingIds": []}
    print("PASS simulated I01f1 -> I01f2 -> I01f3 -> I01f -> I01 -> I02-I04 -> full B1 review")
    print("PASS no child reviews, skipped parent qualification or skipped pending account capability")

    ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
    items = {item["id"]: item for item in ledger["items"]}
    for identifier in report["planning"]["sourceIds"]["directScenarioPortions"]:
        assert "I01" in items[identifier]["implementationDependencies"]
        assert items[identifier]["completion"] == "unexecuted"
    for identifier in report["planning"]["sourceIds"]["laterBehaviorConstraints"]:
        assert items[identifier]["completion"] == "unexecuted"
    assert items["T-632"]["implementationDependencies"] == ["I35"]
    for path in report["sourcePaths"]:
        assert Path(path).read_bytes() == subprocess.check_output(
            ["git", "show", report["startCommit"] + ":" + path]), path
    print("PASS source documents, fixture/coverage accounting and unexecuted product outcomes unchanged")

    assert report["runId"] == "20261021T235653Z-f324bb89"
    assert report["sessionID"] == "ses_e9f1a89d4ffeOTJBw1gtYAcIjj"
    assert report["mode"] == choice["mode"] and report["target"] == choice["target"]
    assert report["outcome"] == "progress"
    assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
    paths = report["changedPaths"]
    prefix = REPORT[:-5]
    assert len(paths) == len(set(paths)) == 6
    assert set(paths) == {STATE, REPORT, prefix + "-planning-check.py", prefix + "-planning-check.json",
                          prefix + "-coverage-check.json", prefix + "-fixture-check.json"}
    for check in report["checks"]:
        assert check["kind"] == "required" and check["exitCode"] == 0
        assert check["evidence"] in paths
        for support in check.get("supportPaths", []):
            assert support in paths and support.startswith(prefix + "-")
        if check["evidence"] != prefix + "-planning-check.json":
            captured = json.loads(Path(check["evidence"]).read_text())
            assert captured["exitCode"] == 0
            assert captured["outputSha256"] == hashlib.sha256(captured["output"].encode()).hexdigest()
    actual = set(git("diff", "--name-only").splitlines())
    actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    # capture-check writes this invocation's own output only after this helper exits.
    assert actual in (set(paths) - {prefix + "-planning-check.json"}, set(paths)), actual
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact planning-only changed paths, support registration, retained output hashes and whitespace")
    print("PASS no runtime/build/UI/runner edits or device/service execution; no acceptance/review/release pass")


if __name__ == "__main__":
    main()
