"""Read-only I01e2 planning validation; no driver/services are constructed."""
import copy
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


REPORT = "docs/agent/implementation-runs/20261021T212906Z-37001bdf.json"
STATE = "docs/agent/implementation-state.json"
CHILDREN = ["I01e2a", "I01e2b"]


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git("show", report["startCommit"] + ":" + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path("scripts/implementation-loop.py")
    choice = {"mode": "implement", "target": "I01e2", "findingIds": []}

    assert git("rev-parse", "HEAD") == report["startCommit"]
    assert git("branch", "--show-current") == "master"
    assert not git("diff", "--cached", "--name-only")
    assert before["mode"] == "active" and before["authorization"] == "authorized"
    assert "D-304" in Path(before["authorizationRecord"]).read_text()
    assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
    assert api["selection"](before) == choice
    assert before["findings"] == []
    print("PASS exact baseline/master, empty index, D-304 authorization and supplied I01e2 priority")

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
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != "I01e2")
    assert new["I01e2"] == {**old["I01e2"], "status": "in-progress", "children": CHILDREN,
                           "planningRecord": REPORT}
    for identifier in CHILDREN:
        task = new[identifier]
        assert task["batch"] == "B1" and task["status"] == "pending"
        assert task["outcome"].strip() and task["checks"]
        assert all(isinstance(check, str) and check.strip() for check in task["checks"])
        assert task["dependsOn"] and not set(task["dependsOn"]) & {"I01", "I01e", "I01e2", "I01f"}
    assert new["I01e2a"]["dependsOn"] == old["I01e2"]["dependsOn"]
    assert new["I01e2b"]["dependsOn"] == ["I01e2a"]
    assert all(old[identifier]["status"] == "implemented" for identifier in old["I01e2"]["dependsOn"])
    assert new["I01e"]["children"] == ["I01e1", "I01e2"]
    assert "I01e" in new["I01f"]["dependsOn"]
    print("PASS pure driver state/transition validation; two pending same-B1 children with explicit dependencies")
    print("PASS original I01e2/I01e/I01 outcomes/checks and I01f device dependency retained unchanged")
    print("PASS unrelated tasks, findings, reviews, authorization, model/permissions and release gates unchanged")

    # Pure in-memory scheduling proof: never save simulated implementation statuses.
    simulated = copy.deepcopy(after)
    by_id = {task["id"]: task for task in simulated["tasks"]}
    sequence = CHILDREN + ["I01e2", "I01e", "I01f", "I01"]
    for identifier in sequence:
        assert api["selection"](simulated) == {**choice, "target": identifier}
        by_id[identifier]["status"] = "implemented"
    assert api["selection"](simulated) == {**choice, "target": "I02"}
    for identifier in ["I02", "I03", "I04"]:
        assert api["selection"](simulated) == {**choice, "target": identifier}
        by_id[identifier]["status"] = "implemented"
    assert api["selection"](simulated) == {"mode": "review", "target": "B1", "findingIds": []}
    print("PASS simulated I01e2a -> I01e2b -> I01e2 -> I01e -> I01f -> I01 -> I02-I04 -> B1 review")
    print("PASS both parent combined qualifications and whole B1 review remain mandatory; no child reviews")

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
        assert Path(path).read_bytes() == subprocess.check_output(
            ["git", "show", report["startCommit"] + ":" + path]), path
    assert not [path for path in git("ls-files").splitlines()
                if Path(path).name.lower() in {"compose.yml", "compose.yaml", "docker-compose.yml", "docker-compose.yaml"}]
    print("PASS unchanged primary sources and unexecuted coverage; no Compose input already exists")

    assert report["runId"] == "20261021T212906Z-37001bdf"
    assert report["sessionID"] == "ses_e9fa1d393ffeKDLFI9QhrspuIN"
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
    # capture-check writes this invocation's own output after this helper exits.
    assert actual in (set(paths) - {prefix + "-planning-check.json"}, set(paths))
    subprocess.run(["git", "diff", "--check"], check=True)
    print("PASS exact planning-only path set, check support registration, retained prior-output hashes and whitespace")
    print("PASS no runtime/build/UI/driver inputs changed or product/device/review/release completion claimed")


if __name__ == "__main__":
    main()
