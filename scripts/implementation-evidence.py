#!/usr/bin/env python3
"""Reusable report/evidence operations. Never execute tests, stage files or certify a review.

report CONTEXT.json DETAILS.json       Generate identity/changedPaths; serial state separate, managed proposal in details.
validate REPORT.json [--pending-evidence PATH]  Check the unsaved handoff, not product correctness.
validate-proposal REPORT.json [--saved]  Validate worker artifacts without editing authoritative state.
validate-acceptance REPORT.json --before SNAPSHOT --after SNAPSHOT  Sole writer's latest-state boundary.
retain-junit OUTPUT.json XML...        Preserve exact JUnit bytes, including red diagnostics.
reconcile OUTPUT.json [TASK=]REPORT@COMMIT... Audit child records and latest declared source inputs.
"""
from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("implementation_loop", ROOT / "scripts/implementation-loop.py")
loop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(loop)


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root)


def changed_paths(root):
    tracked = git(root, "diff", "HEAD", "--name-only", "-z").decode().split("\0")
    untracked = git(root, "ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")
    return set(tracked + untracked) - {""}


def write(root, path, value):
    destination = loop.repo_path(root, path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")


def make_report(root, context, details):
    identity = {"runId": context["runId"], "sessionID": context["sessionID"],
                "startCommit": context["startCommit"], "mode": context["selection"]["mode"],
                "target": context["selection"]["target"], "statePath": context.get("state", loop.STATE)}
    for key, value in identity.items():
        loop.require(key not in details or details[key] == value, "Details cannot override supplied identity/selection")
    report = {"findingIds": [], "newFindingIds": [], "reviewedUnitIds": [], "outcome": "progress",
              **details, **identity}
    loop.require(report.get("summary") and report.get("selectionReason") and report.get("checks"),
                 "Details need a concise result, selection reason and actual checks")
    path = context["report"]
    paths = changed_paths(root) | {path}
    prefix = f"{loop.REPORTS}/{context['runId']}"
    for check in report["checks"]:
        evidence = check["evidence"]
        if (evidence.startswith(prefix + "-") or evidence.startswith(prefix + "/")) and not (root / evidence).exists():
            paths.add(evidence)  # The validator's foreground capture is written after it exits.
    report["changedPaths"] = sorted(paths)
    write(root, path, report)
    return report


def check_output(root, check, commit=None):
    """Verify known lossless output envelopes, without relabelling old red results."""
    path = loop.repo_path(root, check["evidence"])
    data = git(root, "show", commit + ":" + check["evidence"]) if commit else path.read_bytes()
    if path.suffix != ".json":
        return
    record = json.loads(data)
    if not isinstance(record, dict) or "exitCode" not in record:
        return
    loop.require(record["exitCode"] == check["exitCode"], "Captured and reported exit codes differ")
    if "output" in record and "outputSha256" in record:
        raw, digest = record["output"].encode(), record["outputSha256"]
    elif "outputBase64" in record and "outputSha256" in record:
        raw, digest = base64.b64decode(record["outputBase64"], validate=True), record["outputSha256"]
    elif "stdout" in record and "sha256" in record:
        raw, digest = record["stdout"].encode(), record["sha256"]
    else:
        return
    loop.require(hashlib.sha256(raw).hexdigest() == digest, "Captured output hash differs")


def validate(root, report_path, pending=None):
    report = loop.load(loop.repo_path(root, report_path))
    loop.require("proposal" not in report and "workerProposal" not in report,
                 "Use validate-proposal or validate-acceptance for managed handoffs")
    loop.require(report_path == f"{loop.REPORTS}/{report['runId']}.json", "Report must use its supplied run namespace")
    state_path = report.get("statePath", loop.STATE)
    loop.repo_path(root, state_path)
    before = json.loads(git(root, "show", report["startCommit"] + ":" + state_path))
    after = loop.load(root / state_path)
    choice = loop.selection(before)
    loop.require(git(root, "rev-parse", "HEAD").decode().strip() == report["startCommit"],
                 "Starting HEAD changed before handoff")
    loop.require(report["mode"] == choice["mode"] and report["target"] == choice["target"], "Wrong selection")
    loop.validate_state(after)
    loop.validate_transition(before, after, choice, report)
    loop.require(after["runs"] == before["runs"] + [report_path] and after["activeRun"] is None,
                 "Run history/active boundary differs")
    loop.require(all(after.get(k) == before.get(k) for k in ("authorization", "authorizationRecord", "runner")),
                 "Authorization/runner controls changed")
    loop.require(after["mode"] == before["mode"] or report["outcome"] == "complete", "Activation changed")
    required = [c for c in report.get("checks", []) if c.get("kind") == "required"]
    loop.require(required or report["outcome"] in {"blocked", "failed"}, "No required check")
    if pending:
        loop.repo_path(root, pending)
        prefix = f"{loop.REPORTS}/{report['runId']}"
        loop.require(pending.startswith(prefix + "-") or pending.startswith(prefix + "/"),
                     "Pending capture must belong to this run")
        loop.require(any(c.get("evidence") == pending and c.get("kind") == "required" and
                         c.get("exitCode") == 0 for c in required), "Pending capture needs its declared required check")
    for check in report.get("checks", []):
        loop.require(check.get("kind") in {"diagnostic", "required"} and check.get("command") and
                     type(check.get("exitCode")) is int, "Malformed check")
        loop.require(check["kind"] == "diagnostic" or check["exitCode"] == 0 or report["outcome"] == "failed",
                     "Required check failed")
        if check["evidence"] != pending:
            check_output(root, check)
    actual = changed_paths(root) | ({pending} if pending else set())
    loop.current_completion_outputs(report, actual)
    loop.require({state_path, report_path} <= actual and len(report["changedPaths"]) == len(set(report["changedPaths"]))
                 and actual == set(report["changedPaths"]), "Owned changedPaths differ from actual changes")
    runner = object.__new__(loop.Runner)
    runner.root, runner.state_path, runner.state_relative = root, root / state_path, state_path
    runner.role_paths(before, choice, report, actual, report_path)
    for path in actual:
        loop.repo_path(root, path)
    git(root, "diff", "--check")
    return {"validated": report_path, "next": loop.selection(after),
            "pendingCapture": pending, "limit": "Pre-save structure only; controller still verifies committed bytes, metadata and actual push"}


def runner_for(root, state_path):
    runner = object.__new__(loop.Runner)
    runner.root, runner.state_path, runner.state_relative = root, root / state_path, state_path
    return runner


def proposed_state(before, report, report_path):
    """Object-level compare-and-set proposal, not a worker-authored state snapshot."""
    after = copy.deepcopy(before)
    seen = set()
    changes = report["proposal"]["changes"]
    loop.require(isinstance(changes, list) and changes, "Proposal needs bounded object changes")
    for change in changes:
        loop.require(isinstance(change, dict) and set(change) == {"collection", "id", "before", "after"},
                     "Proposal change needs collection/id/before/after")
        collection, identifier = change["collection"], change["id"]
        loop.require(collection in {"tasks", "batches", "findings", "externalReleaseGates"} and
                     isinstance(identifier, str) and (collection, identifier) not in seen,
                     "Invalid/duplicate proposal object")
        seen.add((collection, identifier))
        previous = next((x for x in after[collection] if x["id"] == identifier), None)
        replacement = change["after"]
        loop.require(previous == change["before"] and isinstance(replacement, dict) and
                     replacement.get("id") == identifier, "Proposal before mismatch/deletion/reassignment")
        if previous is None:
            after[collection].append(copy.deepcopy(replacement))
        else:
            after[collection][after[collection].index(previous)] = copy.deepcopy(replacement)
    after["runs"].append(report_path)
    return after


def state_boundary(before, after, choice, report, report_path, *, speculative=False):
    """Shared serial role rules plus immutable inventory/history and meaningful progress."""
    loop.validate_state(before)
    loop.validate_state(after)
    loop.require(before.get("authorization") == "authorized" and before.get("authorizationRecord") and
                 before.get("mode") == "active" and before.get("runner", {}).get("model") == loop.MODEL,
                 "Managed work requires existing activation/authorization and exact model controls")
    mutable = {"tasks", "batches", "findings", "externalReleaseGates", "runs"}
    loop.require({k: v for k, v in before.items() if k not in mutable} ==
                 {k: v for k, v in after.items() if k not in mutable}, "State controls changed")
    for collection in ("tasks", "batches", "findings"):
        old = {x["id"]: x for x in before[collection]}
        new = {x["id"]: x for x in after[collection]}
        loop.require(old.keys() <= new.keys(), "State inventory deleted")
        if collection == "batches":
            loop.require(list(old) == list(new), "Review batch inventory changed")
        else:
            loop.require(all(new[i]["batch"] == x["batch"] for i, x in old.items()), "Scope reassigned")
    loop.require(after["runs"] == before["runs"] + [report_path], "Run history boundary differs")
    loop.require(report.get("outcome") == "progress", "Managed units propose progress, not global completion/stop")
    loop.validate_transition(before, after, choice, report, speculative=speculative)
    loop.require(loop.progress_view(before) != loop.progress_view(after), "No task/finding/review progress")
    collection = "tasks" if choice["mode"] == "implement" else "findings" if choice["mode"] in {"fix", "verify"} else "batches"
    identifiers = report.get("findingIds", []) if collection == "findings" else [choice["target"]]
    old = {x["id"]: x for x in before[collection]}
    new = {x["id"]: x for x in after[collection]}
    loop.require(identifiers and any(old.get(i) != new.get(i) for i in identifiers), "Selected role scope did not advance")


def validate_proposal(root, report_path, saved=False, commit=None):
    """Read-only worker validation. Selection is deliberately deferred to acceptance."""
    commit = commit or (git(root, "rev-parse", "HEAD").decode().strip() if saved else None)
    read_bytes = lambda path: git(root, "show", commit + ":" + path) if commit else loop.repo_path(root, path).read_bytes()
    report = json.loads(read_bytes(report_path))
    loop.require(report_path == f"{loop.REPORTS}/{report['runId']}.json", "Wrong proposal namespace")
    loop.require(report.get("sessionID") and report.get("summary") and report.get("selectionReason"),
                 "Proposal needs supplied session/result/selection identity")
    state_path = report.get("statePath", loop.STATE)
    loop.repo_path(root, state_path)
    before_bytes = git(root, "show", report["startCommit"] + ":" + state_path)
    loop.require(read_bytes(state_path) == before_bytes, "Managed worker must leave authoritative state read-only")
    before = json.loads(before_bytes)
    proposal = report.get("proposal", {})
    for key in ("sourceIds", "sourcePaths", "testPaths", "allowedPaths"):
        values = proposal.get(key)
        loop.require(isinstance(values, list) and values and all(isinstance(x, str) and x for x in values) and
                     len(values) == len(set(values)), f"Proposal needs unique nonempty {key}")
        if key != "sourceIds":
            for path in values:
                loop.require(str(loop.repo_path(root, path).relative_to(root)) == path, "Use exact canonical paths")
    choice = {"mode": report["mode"], "target": report["target"], "findingIds": [
        f["id"] for f in before["findings"] if f["batch"] == report["target"] and
        f["status"] == ("open" if report["mode"] == "fix" else "fixed-awaiting-check") and
        f["disposition"] == "actionable"]}
    loop.require(choice["mode"] in {"implement", "fix", "review", "verify"}, "Not a managed worker role")
    after = proposed_state(before, report, report_path)
    state_boundary(before, after, choice, report, report_path, speculative=True)
    if commit:
        git(root, "merge-base", "--is-ancestor", report["startCommit"], commit)
        actual = set(git(root, "diff", "--no-renames", "--name-only", "-z", report["startCommit"], commit).decode().split("\0")) - {""}
        git(root, "diff", "--check", report["startCommit"], commit)
    else:
        loop.require(git(root, "rev-parse", "HEAD").decode().strip() == report["startCommit"], "Worker starting HEAD changed")
        actual = changed_paths(root)
        git(root, "diff", "--check")
    loop.require(len(report["changedPaths"]) == len(set(report["changedPaths"])) and
                 actual == set(report["changedPaths"]) and report_path in actual and state_path not in actual,
                 "Worker changedPaths differ or state was edited")
    loop.require(actual <= set(proposal["allowedPaths"]), "Worker escaped leased allowedPaths")
    for path in proposal["sourcePaths"] + proposal["testPaths"]:
        loop.require(path in actual or git(root, "ls-tree", "--format=%(objecttype)", report["startCommit"], "--", path).strip() == b"blob",
                     "Missing declared source/test input")
    checks = report.get("checks", [])
    loop.require(any(c.get("kind") == "required" for c in checks), "No required worker check")
    for check in checks:
        loop.require(check.get("kind") in {"required", "diagnostic"} and check.get("command") and
                     type(check.get("exitCode")) is int and
                     (check["kind"] == "diagnostic" or check["exitCode"] == 0), "Malformed/failed worker check")
        support = check.get("supportPaths", [])
        loop.require(isinstance(support, list) and len(support) == len(set(support)), "Invalid worker supportPaths")
        paths = [check["evidence"], *support]
        loop.require(all(loop.run_owned(report, p) and p in actual for p in paths), "Worker evidence must be fresh run-owned output")
        for path in paths:
            loop.repo_path(root, path)
            read_bytes(path)
        check_output(root, check, commit)
    loop.current_completion_outputs(report, actual)
    runner_for(root, state_path).role_paths(before, choice, report, actual, report_path, after)
    if saved:
        runner_for(root, state_path).safe_git()
    return {"validatedProposal": report_path, "commit": commit, "sessionID": report["sessionID"],
            "branch": git(root, "symbolic-ref", "--short", "HEAD").decode().strip(),
            "limit": "Artifact/role validation only; supervisor must verify lease, actual model/session metadata and latest-state acceptance"}


def validate_acceptance(root, report_path, before_path, after_path):
    """Sole writer, after candidate code is committed; never merge/copy a worker state."""
    report = loop.load(loop.repo_path(root, report_path))
    reference = report["workerProposal"]
    worker_path, worker_commit = reference["report"], reference["commit"]
    validate_proposal(root, worker_path, commit=worker_commit)
    worker_bytes = git(root, "show", worker_commit + ":" + worker_path)
    loop.require(loop.repo_path(root, worker_path).read_bytes() == worker_bytes, "Worker report bytes changed during integration")
    worker = json.loads(worker_bytes)
    for check in worker["checks"]:
        for path in [check["evidence"], *check.get("supportPaths", [])]:
            loop.require(loop.repo_path(root, path).read_bytes() == git(root, "show", worker_commit + ":" + path),
                         "Worker evidence bytes changed during integration")
    state_path = report.get("statePath", loop.STATE)
    loop.require(state_path == worker.get("statePath", loop.STATE), "Worker/acceptance state differs")
    head = git(root, "rev-parse", "HEAD").decode().strip()
    before_bytes = git(root, "show", head + ":" + state_path)
    loop.require(Path(before_path).read_bytes() == before_bytes, "Acceptance before snapshot is not latest HEAD state")
    loop.require(Path(after_path).resolve() == loop.repo_path(root, state_path), "After snapshot must be the sole writer's current state")
    before, after = json.loads(before_bytes), loop.load(Path(after_path))
    loop.require(report["startCommit"] == head and report_path == f"{loop.REPORTS}/{report['runId']}.json" and
                 report["runId"] != worker["runId"] and report.get("sessionID") and
                 report.get("summary") and report.get("selectionReason"), "Invalid acceptance identity/code anchor")
    choice = loop.selection(before)
    loop.require((report["mode"], report["target"]) == (worker["mode"], worker["target"]) ==
                 (choice["mode"], choice["target"]), "Latest selection/prerequisites/due review must precede acceptance")
    for key in ("findingIds", "newFindingIds", "reviewedUnitIds", "blockedReviewUnitIds"):
        loop.require(report.get(key, []) == worker.get(key, []), "Acceptance escaped independently proposed scope")
    for key in ("implementationUnits", "ancestorClosures", "gateUpdates"):
        loop.require([x["id"] for x in report.get(key, [])] == [x["id"] for x in worker.get(key, [])],
                     "Acceptance added/dropped proposed completion or gate scope")
    for collection in ("tasks", "findings", "externalReleaseGates"):
        proposed = {c["id"]: c["after"] for c in worker["proposal"]["changes"] if c["collection"] == collection}
        previous = {x["id"]: x for x in before[collection]}
        changed = {x["id"]: x for x in after[collection] if x != previous.get(x["id"])}
        loop.require(changed.keys() == proposed.keys() and all(
            all(x.get(k) == proposed[i].get(k) for k in ("status", "disposition")) for i, x in changed.items()),
            "Acceptance changed unproposed objects/outcomes")
    original = json.loads(git(root, "show", worker["startCommit"] + ":" + state_path))
    if choice["mode"] == "implement":
        tasks = {t["id"]: t for t in before["tasks"]}
        old = next(t for t in original["tasks"] if t["id"] == choice["target"])
        loop.require(tasks[choice["target"]] == old, "Selected task changed since dispatch; reconcile a fresh proposal")
        parents = {c: t for t in original["tasks"] for c in t.get("children", [])}
        while old:
            loop.require(all(tasks[d]["status"] == "implemented" for d in old.get("dependsOn", [])),
                         "Original inherited prerequisites are not implemented")
            old = parents.get(old["id"])
    inspected = worker["startCommit"] if choice["mode"] in {"review", "verify"} else head
    loop.require(report.get("inspectedCommit") == inspected, "Acceptance must retain the independent inspected code anchor")
    git(root, "merge-base", "--is-ancestor", inspected, head)
    state_boundary(before, after, choice, {**report, "startCommit": inspected}, report_path)
    actual = changed_paths(root)
    loop.require(actual == set(report["changedPaths"]) and len(actual) == len(report["changedPaths"]) and
                 {state_path, report_path} <= actual, "Acceptance changedPaths differ")
    outputs = {report_path, state_path}
    loop.require(any(c.get("kind") == "required" for c in report.get("checks", [])), "No required acceptance check")
    for check in report["checks"]:
        loop.require(check.get("kind") in {"required", "diagnostic"} and check.get("command") and
                     type(check.get("exitCode")) is int and (check["kind"] == "diagnostic" or check["exitCode"] == 0),
                     "Malformed/failed acceptance check")
        support = check.get("supportPaths", [])
        loop.require(isinstance(support, list) and len(support) == len(set(support)), "Invalid acceptance supportPaths")
        paths = [check["evidence"], *support]
        loop.require(all(loop.run_owned(report, p) and p in actual for p in paths), "Acceptance needs fresh run-owned reconciliation output")
        outputs.update(paths)
        for path in paths:
            loop.repo_path(root, path).read_bytes()
        check_output(root, check)
    loop.require(actual <= outputs, "Candidate code must be saved before acceptance; no unrelated acceptance edits")
    loop.current_completion_outputs(report, actual)
    git(root, "diff", "--check")
    return {"validatedAcceptance": report_path, "latestStateCommit": head, "inspectedCommit": inspected,
            "workerCommit": worker_commit, "next": loop.selection(after),
            "limit": "Pre-save only; sole supervisor verifies actual push, metadata, changed-input review validity and integration seams"}


def retain_junit(root, output, paths):
    suites = {}
    for name in paths:
        data = loop.repo_path(root, name).read_bytes()
        node = ET.fromstring(data)
        loop.require(node.tag in {"testsuite", "testsuites"}, "Expected JUnit XML")
        suites[name] = {"sha256": hashlib.sha256(data).hexdigest(), "base64": base64.b64encode(data).decode(),
                        "tests": node.attrib}
    loop.require(suites, "No JUnit paths supplied")
    write(root, output, suites)
    return {"retainedSuites": len(suites), "output": output, "limit": "Retention is not a green-test claim"}


def reconcile(root, output, records):
    children, inputs = [], {}
    previous = None
    for entry in records:
        report_path, commit = entry.rsplit("@", 1)
        task_id = None
        if "=" in report_path:
            task_id, report_path = report_path.split("=", 1)
        loop.repo_path(root, report_path)
        git(root, "merge-base", "--is-ancestor", commit, "HEAD")
        if previous:
            git(root, "merge-base", "--is-ancestor", previous, commit)
        previous = commit
        data = git(root, "show", commit + ":" + report_path)
        loop.require(data == (root / report_path).read_bytes(), "Child report changed since its supplied commit")
        report = json.loads(data)
        loop.require(report.get("outcome") == "progress" and report.get("mode") == "implement",
                     "Expected implemented child report, not a review/completion claim")
        child_state = json.loads(git(root, "show", commit + ":" + loop.STATE))
        task_id = task_id or report["target"]
        named = {report["target"]} | {u["id"] for u in report.get("implementationUnits", [])} | {
            c["id"] for c in report.get("ancestorClosures", [])}
        loop.require(task_id in named, "Child was not delivered by supplied report")
        task = next(t for t in child_state["tasks"] if t["id"] == task_id)
        loop.require(task["status"] == "implemented" and task.get("implementationRecord") == report_path,
                     "Child task is not implemented at supplied commit")
        checks = []
        for check in report["checks"]:
            loop.require(check["kind"] in {"required", "diagnostic"} and
                         (check["kind"] == "diagnostic" or check["exitCode"] == 0), "Child has a failed required check")
            for path in [check["evidence"], *check.get("supportPaths", [])]:
                loop.repo_path(root, path)
                loop.require((root / path).read_bytes() == git(root, "show", commit + ":" + path),
                             "Child evidence/support differs from committed bytes")
            check_output(root, check)
            checks.append({"evidence": check["evidence"], "kind": check["kind"], "exitCode": check["exitCode"]})
        loop.require(any(c["kind"] == "required" for c in checks), "Child has no required check")
        children.append({"task": task["id"], "record": report_path, "commit": commit,
                         "reportSha256": hashlib.sha256(data).hexdigest(), "reusedChecks": checks})
        source_paths = set(report["changedPaths"])
        if "workerProposal" in report:
            reference = report["workerProposal"]
            loop.repo_path(root, reference["report"])
            worker_data = git(root, "show", reference["commit"] + ":" + reference["report"])
            loop.require(worker_data == git(root, "show", commit + ":" + reference["report"]),
                         "Accepted worker report differs from its saved proposal")
            worker = json.loads(worker_data)
            source_paths.update(worker["changedPaths"])
            source_paths.update(worker["proposal"]["sourcePaths"] + worker["proposal"]["testPaths"])
        for path in source_paths:
            loop.repo_path(root, path)
            if not path.startswith("docs/agent/"):
                inputs[path] = commit
    changed = []
    dirty = changed_paths(root)
    for path, commit in inputs.items():
        # Compare tracked blob identities, including deletions; a later child may
        # supersede an earlier input. Report affected seams instead of rerunning tests.
        if path in dirty or git(root, "ls-tree", commit, "--", path) != git(root, "ls-tree", "HEAD", "--", path):
            changed.append(path)
    result = {"children": children, "changedInputsSinceLastChild": sorted(changed),
              "limit": "Evidence reuse only; explain changed inputs and check missing integration seams. No new product execution or ancestor closure implied."}
    loop.require(children, "No child records supplied")
    write(root, output, result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    report = sub.add_parser("report"); report.add_argument("context"); report.add_argument("details")
    check = sub.add_parser("validate"); check.add_argument("report"); check.add_argument("--pending-evidence")
    proposal = sub.add_parser("validate-proposal"); proposal.add_argument("report"); proposal.add_argument("--saved", action="store_true")
    acceptance = sub.add_parser("validate-acceptance"); acceptance.add_argument("report")
    acceptance.add_argument("--before", required=True); acceptance.add_argument("--after", required=True)
    retain = sub.add_parser("retain-junit"); retain.add_argument("output"); retain.add_argument("paths", nargs="+")
    reuse = sub.add_parser("reconcile"); reuse.add_argument("output"); reuse.add_argument("records", nargs="+")
    args = parser.parse_args()
    root = Path.cwd().resolve()
    loop.require(git(root, "rev-parse", "--show-toplevel").decode().strip() == str(root), "Run at repository root")
    try:
        if args.command == "report": result = make_report(root, loop.load(Path(args.context)), loop.load(Path(args.details)))
        elif args.command == "validate": result = validate(root, args.report, args.pending_evidence)
        elif args.command == "validate-proposal": result = validate_proposal(root, args.report, args.saved)
        elif args.command == "validate-acceptance": result = validate_acceptance(root, args.report, args.before, args.after)
        elif args.command == "retain-junit": result = retain_junit(root, args.output, args.paths)
        else: result = reconcile(root, args.output, args.records)
        print(json.dumps(result, indent=2))
    except (loop.Halt, OSError, ValueError, KeyError, StopIteration, ET.ParseError) as error:
        parser.exit(1, f"Evidence check failed: {error}\n")


if __name__ == "__main__":
    main()
