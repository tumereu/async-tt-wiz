#!/usr/bin/env python3
"""Reusable report/evidence operations. Never execute tests, stage files or certify a review.

report CONTEXT.json DETAILS.json       Generate identity/changedPaths; edit state separately.
validate REPORT.json [--pending-evidence PATH]  Check the unsaved handoff, not product correctness.
retain-junit OUTPUT.json XML...        Preserve exact JUnit bytes, including red diagnostics.
reconcile OUTPUT.json [TASK=]REPORT@COMMIT... Audit child records and latest declared source inputs.
"""
from __future__ import annotations

import argparse
import base64
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


def check_output(root, check):
    """Verify known lossless output envelopes, without relabelling old red results."""
    path = loop.repo_path(root, check["evidence"])
    data = path.read_bytes()
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
        for path in report["changedPaths"]:
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
    retain = sub.add_parser("retain-junit"); retain.add_argument("output"); retain.add_argument("paths", nargs="+")
    reuse = sub.add_parser("reconcile"); reuse.add_argument("output"); reuse.add_argument("records", nargs="+")
    args = parser.parse_args()
    root = Path.cwd().resolve()
    loop.require(git(root, "rev-parse", "--show-toplevel").decode().strip() == str(root), "Run at repository root")
    try:
        if args.command == "report": result = make_report(root, loop.load(Path(args.context)), loop.load(Path(args.details)))
        elif args.command == "validate": result = validate(root, args.report, args.pending_evidence)
        elif args.command == "retain-junit": result = retain_junit(root, args.output, args.paths)
        else: result = reconcile(root, args.output, args.records)
        print(json.dumps(result, indent=2))
    except (loop.Halt, OSError, ValueError, KeyError, StopIteration, ET.ParseError) as error:
        parser.exit(1, f"Evidence check failed: {error}\n")


if __name__ == "__main__":
    main()
