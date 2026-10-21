"""Read-only validation of the I01e split; never start services or a loop."""
import copy
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


REPORT = "docs/agent/implementation-runs/20261021T210831Z-b3901e8c.json"
STATE = "docs/agent/implementation-state.json"
CHILDREN = ["I01e1", "I01e2"]


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01e", "findingIds": []}

    assert git("rev-parse", "HEAD") == report["startCommit"]
    assert git("branch", "--show-current") == "master"
    assert not git("diff", "--cached", "--name-only")
    assert before["mode"] == "active" and before["authorization"] == "authorized"
    assert "D-304" in Path(before["authorizationRecord"]).read_text()
    assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
    assert api["selection"](before) == choice
    assert before["findings"] == []
    print("PASS exact baseline/master, clean index, D-304 authorization and supplied I01e priority")

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
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != "I01e")
    assert new["I01e"] == {**old["I01e"], "status": "in-progress", "children": CHILDREN,
                          "planningRecord": REPORT}
    for identifier in CHILDREN:
        task = new[identifier]
        assert task["batch"] == "B1" and task["status"] == "pending"
        assert task["outcome"].strip() and task["checks"]
        assert all(isinstance(check, str) and check.strip() for check in task["checks"])
        assert task["dependsOn"] and "I01e" not in task["dependsOn"]
        assert "I01f" not in task["dependsOn"]
    assert new["I01e1"]["dependsOn"] == ["I01a", "I01b"]
    assert new["I01e2"]["dependsOn"] == ["I01a", "I01b", "I01c", "I01e1"]
    assert all(old[identifier]["status"] == "implemented" for identifier in old["I01e"]["dependsOn"])
    assert "I01e" in new["I01f"]["dependsOn"]
    print("PASS pure driver state/transition validation; same-B1 pending children and complete parent scope retained")
    print("PASS no reverse device dependency; I01f actual Android reachability/storage/device qualification retained")
    print("PASS unrelated tasks, findings, reviews, authorization, runner, permissions and external gates unchanged")

    # Simulate selection in memory only. Do not complete any implementation in state.
    simulated = copy.deepcopy(after)
    by_id = {task["id"]: task for task in simulated["tasks"]}
    for identifier in CHILDREN:
        assert api["selection"](simulated) == {**choice, "target": identifier}
        by_id[identifier]["status"] = "implemented"
    assert api["selection"](simulated) == choice
    by_id["I01e"]["status"] = "implemented"
    assert api["selection"](simulated) == {**choice, "target": "I01f"}
    by_id["I01f"]["status"] = "implemented"
    assert api["selection"](simulated) == {**choice, "target": "I01"}
    by_id["I01"]["status"] = "implemented"
    assert api["selection"](simulated) == {**choice, "target": "I02"}
    print("PASS simulated I01e1 -> I01e2 -> I01e qualification -> I01f -> I01 qualification -> I02")
    print("PASS no independent review after children; full B1 review still follows I01-I04 together")

    ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
    items = {item["id"]: item for item in ledger["items"]}
    for identifier in report["planning"]["sourceIds"]["directScenarioPortions"]:
        assert "I01" in items[identifier]["implementationDependencies"]
        assert items[identifier]["completion"] == "unexecuted"
    for identifier in report["planning"]["sourceIds"]["architectureConstraintPortions"]:
        assert items[identifier]["completion"] == "unexecuted"
    for identifier in ["T-625", "T-626", "T-629", "T-659"]:
        assert items[identifier]["implementationDependencies"] == ["I05"]
    assert items["T-421"]["implementationDependencies"] == ["I04"]
    for path in report["sourcePaths"]:
        assert Path(path).is_file(), path
        assert Path(path).read_bytes() == subprocess.check_output(
            ["git", "show", report["startCommit"] + ":" + path]), path
    assert not [path for path in git("ls-files").splitlines()
                if Path(path).name.lower() in {"compose.yml", "compose.yaml", "docker-compose.yml", "docker-compose.yaml"}]
    print("PASS unchanged source references; I01 infrastructure and later I04/I05 assertions remain unexecuted")

    assert report["runId"] == "20261021T210831Z-b3901e8c"
    assert report["sessionID"] == "ses_e9fb4ac5affepNIzXV3TvDTwhv"
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
    # capture-check writes this check's own retained output only after we exit.
    assert actual == set(paths) - {prefix + "-planning-check.json"} or actual == set(paths)
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact planning-only owned paths, retained prior check hashes and whitespace")
    print("PASS no runtime/build/UI/driver changes, service/device execution or product/review/release completion claim")


if __name__ == "__main__":
    main()
