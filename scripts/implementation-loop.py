#!/usr/bin/env python3
"""Serial OpenCode session driver. Tooling only; never grants implementation authority."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import uuid

MODEL = "openai/gpt-6.1-sol#high"
MODEL_REF = {"providerID": "openai", "id": "gpt-6.1-sol", "variant": "high"}
STATE = "docs/agent/implementation-state.json"
PROMPT = "docs/agent/implementation-agent.md"
REPORTS = "docs/agent/implementation-runs"


class Halt(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Halt(message)


def load(path):
    return json.loads(path.read_text())


def atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(path)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_command(command, root, *, timeout=60):
    result = subprocess.run(command, cwd=root, stdin=subprocess.DEVNULL, text=True,
                            capture_output=True, timeout=timeout,
                            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"})
    if result.returncode:
        raise Halt(f"Command failed ({result.returncode}): {command!r}\n{result.stderr.strip()}")
    return result.stdout.strip()


def repo_path(root, relative):
    require(isinstance(relative, str) and relative and not Path(relative).is_absolute(),
            f"Expected repository-relative path: {relative!r}")
    result = (root / relative).resolve()
    require(result.is_relative_to(root) and result != root and ".git" not in result.relative_to(root).parts,
            f"Unsafe repository path: {relative}")
    return result


def validate_state(state):
    require(state.get("formatVersion") == 1, "Unsupported state format")
    require(isinstance(state.get("runs"), list) and state.get("activeRun") is None,
            "State must have a runs list and no unfinished activeRun")
    tasks, batches, findings = state["tasks"], state["batches"], state["findings"]
    for collection in (tasks, batches, findings):
        require(len({item["id"] for item in collection}) == len(collection), "Duplicate state IDs")
    batch_ids = {item["id"] for item in batches}
    task_ids = {item["id"] for item in tasks}
    for task in tasks:
        require(task["batch"] in batch_ids, "Task has unknown batch")
        require(task["status"] in {"pending", "in-progress", "implemented", "blocked"},
                "Unknown task status")
        for key in ("dependsOn", "children"):
            require(all(value in task_ids and value != task["id"] for value in task.get(key, [])),
                    f"Invalid {key} in {task['id']}")
        require(not task.get("independent") or "dependsOn" in task,
                "Independent work needs explicit dependencies")
    by_id = {t["id"]: t for t in tasks}
    parents = {}
    for task in tasks:
        require(len(task.get("children", [])) == len(set(task.get("children", []))), "Duplicate child IDs")
        for child in task.get("children", []):
            require(child not in parents and by_id[child]["batch"] == task["batch"],
                    "Children need one parent and must inherit its review batch")
            parents[child] = task["id"]
            require(task["status"] != "implemented" or by_id[child]["status"] == "implemented",
                    "Implemented parent has unfinished children")
    for batch in batches:
        members = [task for task in tasks if task["batch"] == batch["id"]]
        require(members, "Empty review batch")
        require(batch["status"] in {"pending", "ready-for-review", "changes-requested",
                                    "fixes-awaiting-check", "reviewed"}, "Unknown batch status")
        if batch["status"] == "reviewed":
            require(batch.get("reviewedCommit") and all(t["status"] == "implemented" for t in members),
                    "Reviewed batch has unfinished tasks or no reviewed commit")
            require(not any(needs_attention(f) for f in findings if f["batch"] == batch["id"]),
                    "Reviewed batch has unresolved findings")
    for finding in findings:
        require(finding["batch"] in batch_ids and finding["status"] in {
            "open", "fixed-awaiting-check", "verified", "withdrawn", "duplicate", "blocked"},
            "Invalid finding batch/status")
        require(finding.get("disposition") in {"actionable", "nonblocking-polish", "not-a-defect",
                                                "duplicate", "blocked-owner/environment"},
                "Finding needs an explicit disposition")
        required_disposition = {"fixed-awaiting-check": "actionable", "verified": "actionable",
                                "withdrawn": "not-a-defect", "duplicate": "duplicate"}.get(finding["status"])
        require(not required_disposition or finding["disposition"] == required_disposition,
                "Finding status/disposition mismatch")
    # Catch bad child/dependency graphs before launching an agent.
    def visit(identifier, path):
        require(identifier not in path, "Cycle in task dependencies/children")
        task = by_id[identifier]
        for child in task.get("children", []) + task.get("dependsOn", []):
            visit(child, path | {identifier})
    for identifier in by_id:
        visit(identifier, set())


def needs_attention(finding):
    return finding["status"] in {"open", "fixed-awaiting-check", "blocked"} and finding.get(
        "disposition") in {"actionable", "blocked-owner/environment"}


def validate_transition(before, after, choice, report):
    """Enforce one role/unit, allowing explicitly explained invalidation and planning."""
    mode, target = choice["mode"], choice["target"]
    old_tasks = {t["id"]: t for t in before["tasks"]}
    new_tasks = {t["id"]: t for t in after["tasks"]}
    old_issues = {f["id"]: f for f in before["findings"]}
    new_issues = {f["id"]: f for f in after["findings"]}
    old_batches = {b["id"]: b for b in before["batches"]}
    require([t["id"] for t in after["tasks"] if t["id"] in old_tasks] == list(old_tasks),
            "Agent reordered existing tasks")

    def identifiers(key):
        values = report.get(key, [])
        require(isinstance(values, list) and all(isinstance(v, str) for v in values) and
                len(values) == len(set(values)), f"Invalid report {key}")
        return set(values)

    def explained(key, known):
        values = report.get(key, [])
        require(isinstance(values, list) and all(isinstance(v, dict) and v.get("id") in known and
                isinstance(v.get("reason"), str) and v["reason"].strip() for v in values),
                f"Invalid explained {key}")
        require(len({v["id"] for v in values}) == len(values), f"Duplicate {key}")
        return {v["id"] for v in values}

    attempted = identifiers("findingIds")
    additions = identifiers("newFindingIds")
    adjustments = explained("taskAdjustments", old_tasks)
    reopened = explained("reopenedBatches", old_batches)
    reopened_issues = explained("reopenedFindings", old_issues)
    if mode == "review":
        require(all(i in new_issues and new_issues[i]["batch"] == target for i in attempted),
                "Review escaped its finding scope")
    else:
        require(attempted <= set(choice["findingIds"]), "Fix/verification escaped its finding scope")
    added_issues = new_issues.keys() - old_issues.keys()
    require(added_issues == additions | (attempted - old_issues.keys() if mode == "review" else set()),
            "New findings must be explicitly reported")

    new_ids = new_tasks.keys() - old_tasks.keys()
    descendants = set()
    def collect(identifier):
        for child in new_tasks[identifier].get("children", []):
            descendants.add(child)
            collect(child)
    if mode == "implement":
        collect(target)
    require(new_ids <= descendants and all(new_tasks[i]["status"] == "pending" for i in new_ids),
            "New children must be pending descendants of the selected implementation task")
    if new_ids:
        require(new_tasks[target]["status"] == "in-progress", "Split-only unit must leave parent in progress")
    for identifier, old in old_tasks.items():
        new = new_tasks[identifier]
        if mode == "implement" and identifier == target:
            require(old["status"] != "implemented", "Cannot reimplement a completed task")
            require(set(old.get("children", [])) <= set(new.get("children", [])), "Agent removed existing children")
        elif new != old:
            require(identifier in adjustments and old.get("children", []) == new.get("children", []) and
                    (new["status"] == old["status"] or old["status"] != "implemented" and
                     new["status"] in {"pending", "in-progress", "blocked"}),
                    "Unrelated task advancement needs a bounded planning/blocker adjustment")

    for identifier, new in new_issues.items():
        old = old_issues.get(identifier)
        if old == new:
            continue
        if old is None:
            require(new["status"] not in {"verified", "fixed-awaiting-check"},
                    "New finding cannot already be fixed/verified")
        elif mode == "fix" and identifier in attempted:
            require(old["status"] == "open" and new["status"] in {"open", "fixed-awaiting-check", "blocked"} and
                    new["disposition"] in {"actionable", "blocked-owner/environment"},
                    "Fix cannot verify/close its own finding or waive it as polish")
        elif mode == "verify" and identifier in attempted:
            require(old["status"] == "fixed-awaiting-check" and
                    new["status"] in {"verified", "open", "blocked", "withdrawn", "duplicate"} and
                    (new["status"] != "open" or new["disposition"] == "actionable"),
                    "Verification needs a prior fresh fix handoff and an explicit verified/reopened/disposed result")
        elif identifier in reopened_issues:
            require(old["status"] in {"verified", "withdrawn", "duplicate", "blocked"} and
                    new["status"] == "open" and new["disposition"] == "actionable",
                    "Reopening must retain the finding and request new actionable work")
        else:
            require(mode == "review" and identifier in attempted and new["status"] != "verified",
                    "Unreported finding change or verification outside a fresh verifier")
        if new["status"] in {"withdrawn", "duplicate", "blocked"}:
            require(isinstance(new.get("rationale"), str) and new["rationale"].strip(),
                    "Finding disposition needs a rationale")
        if old:
            require(new["batch"] == old["batch"], "Agent reassigned a finding")

    target_batch = new_tasks[target]["batch"] if mode == "implement" else target
    for batch in after["batches"]:
        old = old_batches[batch["id"]]
        issues = [f for f in after["findings"] if f["batch"] == batch["id"] and needs_attention(f)]
        attention = ("fixes-awaiting-check" if any(f["status"] == "fixed-awaiting-check" for f in issues)
                     else "changes-requested" if issues else None)
        if batch["id"] == target_batch and mode in {"implement", "review", "fix", "verify"}:
            done = all(t["status"] == "implemented" for t in after["tasks"] if t["batch"] == batch["id"])
            expected = attention or ("reviewed" if mode in {"review", "verify"} and done else
                                     "ready-for-review" if mode == "implement" and done else "pending")
            require(batch["status"] == expected, "Batch transition bypasses its selected role or remaining work")
            if expected == "reviewed":
                require(batch.get("reviewedCommit") == report["startCommit"], "Review must anchor the inspected starting commit")
            else:
                require(batch.get("reviewedCommit") in ([old.get("reviewedCommit"), report["startCommit"]]
                        if mode == "review" else [old.get("reviewedCommit")]), "Unexpected review anchor change")
        elif batch != old:
            require(batch["id"] in reopened and old["status"] == "reviewed" and
                    batch["status"] == (attention or "ready-for-review") and
                    batch.get("reviewedCommit") == old.get("reviewedCommit"),
                    "Unrelated batch advancement needs an explained scoped reopening")


def update_attempts(attempts, choice, report, findings):
    """Count attempted fixes, retaining the count until their separate verification."""
    unresolved = []
    if choice["mode"] not in {"fix", "verify"}:
        return unresolved
    issues = {f["id"]: f for f in findings}
    for identifier in report["findingIds"]:
        issue = issues[identifier]
        if issue["status"] in {"verified", "withdrawn", "duplicate"}:
            attempts.pop(identifier, None)
            continue
        old = attempts.get(identifier, {})
        diagnosis = " ".join(issue.get("diagnosis", "").split())
        if old and old.get("diagnosis", "") != diagnosis:
            require(diagnosis and issue.get("diagnosisEvidence") in {c["evidence"] for c in report["checks"]},
                    "Diagnosis change needs retained check evidence, not just a new assertion")
        count = old.get("count", 0) if old.get("diagnosis", "") == diagnosis else 0
        if choice["mode"] == "fix":
            count += 1
        attempts[identifier] = {"count": count, "diagnosis": diagnosis}
        if count >= 2 and issue["status"] in {"open", "blocked"}:
            unresolved.append(identifier)
    return unresolved


def selection(state):
    """Priority guard/hint; the fresh agent still reads and confirms the actual work."""
    validate_state(state)
    for status, mode in [("fixed-awaiting-check", "verify"), ("open", "fix")]:
        for batch in state["batches"]:
            issues = [f["id"] for f in state["findings"] if f["batch"] == batch["id"]
                      and f["status"] == status and f["disposition"] == "actionable"]
            if issues:
                return {"mode": mode, "target": batch["id"], "findingIds": issues}
    tasks = state["tasks"]
    by_id = {t["id"]: t for t in tasks}
    children = {child for t in tasks for child in t.get("children", [])}
    def candidate(task):
        if task["status"] in {"implemented", "blocked"}:
            return None
        if not all(by_id[d]["status"] == "implemented" for d in task.get("dependsOn", [])):
            return None
        for child in task.get("children", []):
            if by_id[child]["status"] != "implemented":
                return candidate(by_id[child])
        return {"mode": "implement", "target": task["id"], "findingIds": []}
    for batch in state["batches"]:
        if batch["status"] == "reviewed":
            continue  # A later/report-only HEAD does not invalidate this scoped review.
        members = [t for t in tasks if t["batch"] == batch["id"]]
        if all(t["status"] == "implemented" for t in members):
            if batch["status"] in {"pending", "ready-for-review"}:
                return {"mode": "review", "target": batch["id"], "findingIds": []}
            break  # An unresolved blocked finding is not another full-review request.
        previous_complete = True
        for task in members:
            if task["id"] in children:
                continue
            if previous_complete or "dependsOn" in task:
                found = candidate(task)
                if found:
                    return found
            previous_complete = previous_complete and task["status"] == "implemented"
        break
    # An explicitly recorded independent lane may continue around a blocked batch.
    for task in tasks:
        if task.get("independent") and task["id"] not in children:
            found = candidate(task)
            if found:
                return found
    outstanding = any(f["status"] in {"open", "fixed-awaiting-check", "blocked"}
                      and f["disposition"] in {"actionable", "blocked-owner/environment"}
                      for f in state["findings"])
    gates = all(g["status"] in {"passed", "not-applicable"} for g in state.get("externalReleaseGates", []))
    finished = all(t["status"] == "implemented" for t in tasks) and all(
        b["status"] == "reviewed" for b in state["batches"])
    return {"mode": "complete" if finished and gates and not outstanding else "blocked",
            "target": "completion" if finished and gates and not outstanding else "remaining-gates",
            "findingIds": []}


def progress_view(state):
    return {key: state[key] for key in ("tasks", "batches", "findings")}


class OpenCode:
    def __init__(self, root, executable="opencode"):
        self.root, self.executable = root, executable

    def api(self, method, path, body=None, *, timeout=60):
        command = [self.executable, "api", method, path]
        if body is not None:
            command += ["--data", json.dumps(body)]
        text = run_command(command, self.root, timeout=timeout)
        return json.loads(text) if text else {}

    def create(self, title, permissions):
        response = self.api("post", "/api/session", {
            "title": title, "agent": "build", "model": MODEL_REF,
            "location": {"directory": str(self.root)}, "permissions": permissions})["data"]
        require(response.get("model") == MODEL_REF and not response.get("parentID"),
                "New session did not retain the required model/variant or is a child")
        require(response["location"]["directory"] == str(self.root), "Session in wrong directory")
        return response["id"]

    def info(self, session):
        return self.api("get", f"/api/session/{session}")["data"]

    def interrupt(self, session):
        self.api("post", f"/api/session/{session}/interrupt")

    def is_active(self, session):
        return session in self.api("get", "/api/session/active")["data"]

    def execute(self, session, prompt, log, timeout):
        command = [self.executable, "run", "--session", session, "--model", MODEL,
                   "--agent", "build", "--format", "json", prompt]
        # No --continue, --fork, --auto, shell=True or global permission changes.
        with log.open("w") as output:
            process = subprocess.Popen(command, cwd=self.root, stdin=subprocess.DEVNULL,
                                       stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            deadline, next_check = time.monotonic() + timeout, time.monotonic() + 5
            try:
                while process.poll() is None:
                    require(time.monotonic() < deadline, "Agent exceeded the configured wall-time budget")
                    if time.monotonic() >= next_check:
                        pending = self.api("get", f"/api/session/{session}/permission",
                                           timeout=min(5, max(0.1, deadline - time.monotonic())))["data"]
                        require(not pending, "Agent needs an interactive permission decision")
                        next_check = time.monotonic() + 5
                    time.sleep(0.2)
                require(process.returncode == 0, f"OpenCode exited {process.returncode}; see {log}")
            except BaseException:
                try:
                    self.interrupt(session)  # Stopping the CLI alone does not stop server-owned work.
                finally:
                    if process.poll() is None:
                        os.killpg(process.pid, signal.SIGTERM)
                        try:
                            process.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            os.killpg(process.pid, signal.SIGKILL)
                            process.wait()
                raise
        info = self.info(session)
        require(info.get("model") == MODEL_REF, "Session model/variant changed")
        require(info.get("outcome") == "succeeded" and not self.is_active(session),
                "Session did not finish successfully/idle")
        return info


class Runner:
    def __init__(self, root, state_path=STATE, client=None):
        self.root = Path(root).resolve()
        require(self.git("rev-parse", "--show-toplevel") == str(self.root), "Run at a Git repository root")
        self.state_path = repo_path(self.root, state_path)
        self.state_relative = str(self.state_path.relative_to(self.root))
        common = Path(self.git("rev-parse", "--git-common-dir"))
        self.journal = (common if common.is_absolute() else self.root / common).resolve() / "implementation-loop"
        self.journal.mkdir(parents=True, exist_ok=True)
        self.client = client or OpenCode(self.root)

    def git(self, *args):
        return run_command(["git", *args], self.root)

    def committed_file(self, relative, commit):
        repo_path(self.root, relative)
        blob = self.git("hash-object", "--no-filters", "--", relative)
        entries = self.git("ls-tree", "--format=%(objectmode) %(objecttype) %(objectname)",
                           commit, "--", relative).splitlines()
        require(entries in ([f"100644 blob {blob}"], [f"100755 blob {blob}"]),
                f"Check evidence is not committed byte-for-byte: {relative}")

    def safe_git(self):
        require(not self.git("status", "--porcelain=v1", "--untracked-files=all"),
                "Dirty working tree/index: preserve and inspect it; nothing was staged or discarded")
        branch = self.git("symbolic-ref", "--short", "HEAD")
        remote = self.git("config", f"branch.{branch}.remote")
        ref = self.git("config", f"branch.{branch}.merge")
        require(remote != "." and ref.startswith("refs/heads/"), "A real configured upstream is required")
        upstream = self.git("ls-remote", "--exit-code", remote, ref).split()[0]
        require(upstream == self.git("rev-parse", "HEAD"), "HEAD and actual upstream differ; reconcile/save first")

    def authorized_state(self):
        state = load(self.state_path)
        validate_state(state)
        require(state.get("authorization") == "authorized" and state.get("authorizationRecord"),
                "Implementation is not authorized; runner preparation does not activate the task plan")
        require(state.get("mode") in {"active", "complete"}, "State is not active")
        require(state.get("runner", {}).get("model") == MODEL, f"Runner must explicitly pin {MODEL}")
        return state

    def unit(self, state, seconds):
        choice = selection(state)
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-") + uuid.uuid4().hex[:8]
        report_relative = f"{REPORTS}/{run_id}.json"
        report_path = repo_path(self.root, report_relative)
        require(not report_path.exists(), "Run report already exists")
        permission_path = repo_path(self.root, state["runner"]["permissions"])
        permissions = load(permission_path)
        require(isinstance(permissions, list) and permissions, "Explicit session permissions required")
        protected = {str(path): digest(path) for path in [permission_path, repo_path(self.root, PROMPT),
                                                          Path(__file__).resolve()]}
        start = self.git("rev-parse", "HEAD")
        directory = self.journal / run_id
        directory.mkdir()
        record = {"runId": run_id, "startCommit": start, "selection": choice,
                  "worktree": str(self.root), "branch": self.git("symbolic-ref", "--short", "HEAD"),
                  "state": self.state_relative,
                  "report": report_relative, "model": MODEL, "status": "starting"}
        active_path = self.journal / "active.json"
        atomic_json(active_path, record)
        try:
            session = self.client.create(f"ATW {choice['mode']} {choice['target']} {run_id}", permissions)
            record.update(sessionID=session, status="running")
            atomic_json(active_path, record)
            context = {**record, "state": self.state_relative, "wallTimeSeconds": seconds}
            prompt = repo_path(self.root, PROMPT).read_text() + "\n\nTHIS RUN:\n" + json.dumps(context, indent=2)
            info = self.client.execute(session, prompt, directory / "agent.jsonl", seconds)
            require(info.get("model") == MODEL_REF, "Wrong model in completed session")
            atomic_json(directory / "session.json", info)
            after = load(self.state_path)
            validate_state(after)
            old_tasks = {t["id"]: t for t in state["tasks"]}
            new_tasks = {t["id"]: t for t in after["tasks"]}
            require(old_tasks.keys() <= new_tasks.keys() and all(
                new_tasks[key]["batch"] == task["batch"] for key, task in old_tasks.items()),
                "Agent deleted/reassigned existing tasks")
            require([b["id"] for b in after["batches"]] == [b["id"] for b in state["batches"]],
                    "Agent deleted/reordered review batches")
            require({f["id"] for f in state["findings"]} <= {f["id"] for f in after["findings"]},
                    "Findings must be retained, not deleted")
            require(after.get("authorization") == state["authorization"] and
                    after.get("authorizationRecord") == state["authorizationRecord"] and
                    after.get("runner") == state["runner"], "Agent changed activation/runner controls")
            require(all(digest(Path(path)) == value for path, value in protected.items()),
                    "Agent changed runner/prompt/permission controls; manual review required")
            require(report_path.is_file(), "Agent exited without its durable run report")
            report = load(report_path)
            require(report.get("runId") == run_id and report.get("sessionID") == session and
                    report.get("startCommit") == start, "Report identity/start commit mismatch")
            require(report.get("mode") == choice["mode"] and report.get("target") == choice["target"],
                    "Agent skipped the required review/fix/implementation priority")
            require(report.get("summary") and report.get("selectionReason"), "Report needs outcome and selection evidence")
            require(report.get("outcome") in {"progress", "complete", "blocked", "failed"}, "Unknown outcome")
            require(after.get("mode") == state["mode"] or report["outcome"] == "complete",
                    "Agent changed loop activation mode")
            require(after["runs"] == state["runs"] + [report_relative],
                    "Run history must retain previous reports and append exactly this unit")
            validate_transition(state, after, choice, report)
            required = [check for check in report.get("checks", []) if check.get("kind") == "required"]
            require(required or report["outcome"] in {"blocked", "failed"}, "No required validation recorded")
            for check in report.get("checks", []):
                require(check.get("kind") in {"required", "diagnostic"} and check.get("command") and
                        type(check.get("exitCode")) is int, "Malformed check evidence")
                evidence = repo_path(self.root, check["evidence"])
                require(evidence.is_file(), "Missing check evidence")
                if check["kind"] == "required":
                    require(check["exitCode"] == 0 or report["outcome"] == "failed", "Required check failed")
            require(report["outcome"] != "failed", "Agent reported an unresolved failure")
            if report["outcome"] == "progress":
                require(choice["mode"] not in {"complete", "blocked"}, "No runnable work for claimed progress")
                require(progress_view(state) != progress_view(after), "No task/finding/review progress; refusing to spin")
                if choice["mode"] == "implement":
                    require(new_tasks[choice["target"]] != old_tasks[choice["target"]],
                            "Selected implementation task did not advance")
                elif choice["mode"] in {"fix", "verify"}:
                    before_issues = {f["id"]: f for f in state["findings"]}
                    after_issues = {f["id"]: f for f in after["findings"]}
                    require(report.get("findingIds") and any(
                        before_issues[key] != after_issues[key] for key in report["findingIds"]),
                        "Selected fix/verification findings did not advance")
                else:
                    require(next(b for b in state["batches"] if b["id"] == choice["target"]) !=
                            next(b for b in after["batches"] if b["id"] == choice["target"]),
                            "Selected review batch did not advance")
            if report["outcome"] == "complete":
                require(selection(after)["mode"] == "complete" and after.get("mode") == "complete",
                        "Premature completion: tasks/reviews/findings/release gates remain")
            if report["outcome"] == "blocked":
                require(selection(after)["mode"] == "blocked", "Independent eligible work remains; not globally blocked")
            self.git("merge-base", "--is-ancestor", start, "HEAD")
            finish = self.git("rev-parse", "HEAD")
            require(finish != start, "No committed report/state changes")
            actual = set(self.git("diff", "--no-renames", "--name-only", "-z", start, finish).split("\0")) - {""}
            claimed = report.get("changedPaths", [])
            require(len(claimed) == len(set(claimed)) and set(claimed) == actual,
                    "Committed changed paths differ from the owned-path report")
            require({report_relative, self.state_relative} <= actual, "Report/state not saved in this unit")
            for path in claimed:
                repo_path(self.root, path)
            for path in {report_relative, self.state_relative} | {c["evidence"] for c in report.get("checks", [])}:
                self.committed_file(path, finish)
            self.git("diff", "--check", start, finish)
            for batch in after["batches"]:
                if batch["status"] == "reviewed":
                    self.git("merge-base", "--is-ancestor", batch["reviewedCommit"], finish)
            require(self.git("symbolic-ref", "--short", "HEAD") == record["branch"],
                    "Agent changed the owned branch")
            self.safe_git()  # Includes actual remote confirmation, not just a local tracking ref.
            record.update(commit=finish, pushed=True)
            attempts = load(self.journal / "fix-attempts.json") if (self.journal / "fix-attempts.json").exists() else {}
            if choice["mode"] in {"fix", "verify"}:
                unresolved = update_attempts(attempts, choice, report, after["findings"])
                atomic_json(self.journal / "fix-attempts.json", attempts)
                require(not unresolved, f"Repeated unresolved fix without a new diagnosis: {', '.join(unresolved)}")
            record.update(status=report["outcome"], commit=finish, pushed=True)
            atomic_json(directory / "result.json", record)
            active_path.unlink()
            return record
        except BaseException as error:
            record.update(status="halted", error=str(error))
            atomic_json(directory / "result.json", record)
            atomic_json(active_path, record)
            raise

    def run(self, maximum, seconds):
        require(maximum > 0 and seconds > 0, "Positive session count and wall-time budgets required")
        with (self.journal / "writer.lock").open("a") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise Halt("Another loop writer holds the repository lock")
            require(not (self.journal / "active.json").exists(),
                    "Interrupted/failed run retained: inspect it and use recover; do not blindly restart")
            for _ in range(maximum):
                if (self.journal / "STOP").exists():
                    return {"status": "paused", "reason": "stop requested"}
                state = self.authorized_state()
                self.safe_git()
                if state["mode"] == "complete":
                    require(selection(state)["mode"] == "complete", "Recorded completion has unfinished criteria")
                    return {"status": "complete", "reason": "Already completed and pushed; no agent started"}
                result = self.unit(state, seconds)
                print(json.dumps(result), flush=True)
                if result["status"] in {"complete", "blocked"}:
                    return result
            return {"status": "paused", "reason": "configured session-count budget reached"}

    def recover(self):
        with (self.journal / "writer.lock").open("a") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise Halt("Cannot recover while the loop writer is running")
            active = self.journal / "active.json"
            require(active.exists(), "No retained interrupted/failed run")
            record = load(active)
            require(record.get("worktree") == str(self.root) and record.get("state") == self.state_relative and
                    record.get("branch") == self.git("symbolic-ref", "--short", "HEAD"),
                    "Recovery ownership mismatch: use the recorded worktree, branch and --state; "
                    "legacy records without ownership need manual provenance reconciliation")
            if record.get("sessionID"):
                require(not self.client.is_active(record["sessionID"]), "Prior agent still active; interrupt it first")
            self.safe_git()  # Never discard or stage partially saved work.
            report_path = repo_path(self.root, record["report"])
            if report_path.exists():
                report = load(report_path)
                require(report.get("mode") == record["selection"]["mode"] and
                        report.get("target") == record["selection"]["target"],
                        "Repair the invalid selection report before recovery")
                require(report.get("outcome") != "failed" and all(
                    check.get("exitCode") == 0 for check in report.get("checks", [])
                    if check.get("kind") == "required"),
                    "Unresolved failed required checks/report: repair and validate before recovery")
            validate_state(load(self.state_path))
            destination = self.journal / record["runId"] / "recovered.json"
            atomic_json(destination, {"inspectedRecord": record, "head": self.git("rev-parse", "HEAD")})
            active.unlink()
            return {"status": "recovered", "note": "No work was discarded; run is not automatically resumed"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--state", default=STATE)
    commands = parser.add_subparsers(dest="command", required=True)
    start = commands.add_parser("run", help="Start fresh sessions only with recorded implementation authorization")
    start.add_argument("--max-runs", type=int, required=True)
    start.add_argument("--timeout-seconds", type=int, required=True)
    commands.add_parser("status")
    commands.add_parser("stop", help="Let the current unit finish; do not start another")
    commands.add_parser("clear-stop", help="Remove only the owned stop flag; does not start work")
    commands.add_parser("recover", help="After manual inspection, acknowledge an idle, clean, pushed failed run")
    args = parser.parse_args(argv)
    try:
        runner = Runner(args.repo, args.state)
        if args.command == "run":
            result = runner.run(args.max_runs, args.timeout_seconds)
        elif args.command == "status":
            state = load(runner.state_path)
            result = {"authorization": state.get("authorization"), "model": MODEL,
                      "next": selection(state), "stopRequested": (runner.journal / "STOP").exists(),
                      "retainedRun": load(runner.journal / "active.json") if (runner.journal / "active.json").exists() else None}
        elif args.command == "stop":
            (runner.journal / "STOP").touch()
            result = {"status": "stop-requested"}
        elif args.command == "clear-stop":
            (runner.journal / "STOP").unlink(missing_ok=True)
            result = {"status": "stop-cleared", "note": "No agent started"}
        else:
            result = runner.recover()
        print(json.dumps(result, indent=2))
        return 2 if result.get("status") == "blocked" else 0
    except (Halt, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Loop stopped: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Loop interrupted; prior run/evidence retained. Inspect before recovery.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
