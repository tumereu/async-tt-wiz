# Implementation-loop driver

The driver is **[scripts/implementation-loop.py](../../scripts/implementation-loop.py)**,
using Python's standard library on Linux/WSL and OpenCode V2. The owner authorized
building and qualifying this tooling under D-302, **not starting application
implementation**. The real [state](implementation-state.json) remains
`draft` / `not-authorized`.

Every role is pinned to **`openai/gpt-6.1-sol#high`**. There is no model override
flag or fallback. The driver checks the recorded model, creates a fresh top-level
session with that exact provider/model/variant, passes it explicitly to
`opencode run`, and checks completed session metadata again. Existing global
OpenCode configuration and the owner's current interactive session are unchanged.

Read the [task plan](implementation-plan.md), [behavioral review/loop contract](implementation-loop.md)
and [one-unit agent instructions](implementation-agent.md). This driver executes
that contract; it is not a general multi-agent framework or a product scaffold.

## Commands

From the repository root, inspect without starting an agent:

```sh
python3 scripts/implementation-loop.py status
```

`next` is a guarded selection hint, **not authorization**. The real checkout may
show I01 while still refusing execution. The following future activation example
also refuses to run until explicit owner authorization is recorded:

```sh
python3 scripts/implementation-loop.py run --max-runs 100 --timeout-seconds 3600
```

Both budgets are required operator choices, not implicitly unlimited. A run-count
limit pauses after a saved unit and is not project completion. The time budget
covers each headless agent invocation; API/startup/shutdown operations have their
own bounded timeouts. On expiry the driver interrupts the **server-owned session**
before terminating its own CLI process. It does not restart the shared OpenCode
service or kill unrelated sessions. These are run-count/time limits, **not a
guaranteed dollar cap**; model billing/pricing remains external.

For a graceful stop, in another terminal:

```sh
python3 scripts/implementation-loop.py stop
```

The active agent finishes its bounded unit/checks/save; the driver launches no
next agent. To clear that request later, without starting anything:

```sh
python3 scripts/implementation-loop.py clear-stop
```

Ctrl+C attempts to interrupt the owned session immediately and preserves a halted
run. A hard process/host kill cannot guarantee cleanup; the persistent active-run
record prevents a new driver from blindly starting alongside the old session.

Exit 0 means completion **or an explicitly reported pause**, 2 means a saved global
blocker, 1 means preflight/run/handoff failure, and 130 means keyboard interruption.
Read the printed status; do not interpret exit 0 alone as project completion.

`--repo PATH` selects another actual Git repository root (used by disposable
qualification); `--state PATH` selects a repository-relative state record. Neither
option bypasses authorization, model, clean-tree, upstream or save checks. Runtime
files live beneath the repository's Git **common** directory, so linked worktrees
share one cooperative writer lock. This is not a lock on arbitrary human editors.

## What drives each iteration

1. Obtain the nonblocking writer lock. Refuse an unacknowledged interrupted/failed
   run. Check the stop flag, explicit owner authorization, active state and pinned
   model. Require a clean tree/index and HEAD equal to the **actual** configured
   remote branch, not merely a stale local tracking ref.
2. Derive a priority hint: fixes awaiting verification → actionable fixes → due
   grouped review → next ready task/child. Skip closed/nonblocking findings. Honor
   explicit dependencies/independent lanes around blockers; otherwise keep task/
   batch order. A scoped review stays valid across unrelated/report-only commits.
   A new agent confirms the hint against the actual contracts/records and explains
   the choice, rather than trusting stale conversational memory.
3. Create a fresh OpenCode session using the documented API and exact high model.
   Do **not** resume an old implementation conversation or fork its reasoning.
   Pass only this new session ID to the headless CLI. Supply the bounded unit,
   source records, report path and wall-time budget.
4. The agent implements/reviews/fixes/verifies one unit, retains check evidence,
   updates state/report and uses `scripts/save.sh` on owned paths. Review findings
   must concern actual behavior/player impact. A reproduced defect can have a red
   **diagnostic**; required validation cannot remain red on a success handoff.
5. Require the session to finish successfully/idle with the pinned model. Verify
   report identity/selection, retained history/evidence, meaningful target progress,
   unchanged authorization/runner controls, committed report/state and exact owned
   changed-path set. Check whitespace, ancestry, clean tree/index and actual remote
   confirmation. Only then record success and start the next fresh session.

The report's check command is descriptive evidence, **not shell text for the
driver to execute**. The driver checks structure, reported exits and output-file
existence; it cannot independently prove test honesty or application correctness.
Normal executable checks and subsequent behavioral review remain required.

The driver does not weaken failed checks, force-push, stage files, discard changes
or automatically repeat implementation. A task-scoped blocker may leave another
approved independent task runnable. A global blocker or unmet release criterion
stops distinctly from completion. Already completed/pushed state is reported
without spending another model session.

## Permissions and scope

[implementation-permissions.json](implementation-permissions.json) is the explicit
ordered **per-session** V2 permission profile. It allows repository reads/edits,
normal inspection, trusted repository/build/test scripts and selected local test
tools; it denies unlisted operations, questions, subagents and direct edits to
driver/prompt/profile. External tool file access is restricted except the approved
scratch area. No `--auto`, global allow-all config, new provider or credential is
installed. Pending permission requests halt instead of waiting unattended forever.

Before product activation, inspect this profile against I01's actual build/test
needs and explicitly approve necessary changes. A denied legitimate command is a
blocker to resolve, not permission for the agent to expand its own grants. The
profile/driver/prompt and state runner/authorization controls are checked for
changes after each unit; update these outside an active unit after review.

**This is not an OS sandbox.** Allowed Python/Gradle/repository scripts execute
with the host user's authority; shell directory inference is best effort. The
profile reduces accidental tool use and preserves explicit denies, but cannot
make untrusted scripts safe or enforce every filesystem/network effect. Agents
remain bound by project instructions: no production data/deployment, paid-resource
acquisition, broad destructive cleanup or unrelated work. Keep raw logs private.

## Records and recovery

Committed unit reports/check output go under `docs/agent/implementation-runs/`.
The exact JSON fields and state updates are in [the agent prompt](implementation-agent.md).
The driver writes private journals under `<git-common-dir>/implementation-loop/`:

- `writer.lock`, `STOP`, and persistent `active.json` while running/halted.
- A directory per run containing `agent.jsonl`, completed `session.json` and
  `result.json` with the actual resulting commit/push status or failure reason.
- `fix-attempts.json`: stop after two unresolved fix attempts without a new
  diagnosis. No-progress or missing/invalid reports stop immediately.

No attempt to embed a commit's own hash in that commit is made. Reports reference
the starting/reviewed code commit; the private journal records resulting commits.
Failed/interrupted records and dirty work remain available—never silently reset.

After inspecting the logs, stopping any still-active recorded session and repairing
the underlying issue **with applicable checks passing**, acknowledge the halt:

```sh
python3 scripts/implementation-loop.py recover
```

Recovery refuses an active prior session, dirty/staged work, an unpushed/divergent
HEAD or unresolved failed required-check report. It archives the acknowledgement
and removes only the owned active-run fence. It neither stages/discards files,
promotes tasks nor launches an agent. Inspect task/finding state before explicitly
starting again; recovery is not a substitute for correcting an invalid handoff.

For a failed push, repair the cause and retry the **same** `scripts/save.sh`
operation, not the implementation agent. Never bypass a rejected hook or force
push. If a previous session is still running, inspect/interrupt that exact session
through OpenCode before recovery; do not restart the shared service.

## Qualification

Run deterministic checks without any model/provider use:

```sh
python3 scripts/test-implementation-loop.py
```

These create only owned disposable repositories/remotes under `/tmp/opencode` and
exercise serial selection, child/dependency handling, grouped review/fix/verify,
real Git commit/push boundaries, failed saves, timeout interruption dispatch,
stop/lock/recovery and preservation of unrelated dirty/staged work. They do not
touch the real branch or launch a fake application in place of product E2E.

### Results — 2026-10-21, OpenCode v2.1.1

- **25 automated tests passed**, using controlled fake agent outcomes and real
  disposable Git repositories/remotes. Coverage includes grouped dispatch,
  children/dependencies, already-completed restart, required versus diagnostic
  failures, wrong model/selection, missing reports/evidence, no progress, repeated
  unresolved fixes, dirty/staged/unexpected paths, authorization/control changes,
  rejected push, stop/writer lock, timeout dispatch and guarded manual recovery.
- A real no-tool model probe finished successfully with the exact high variant.
- **Six fresh real sessions passed:** implement F1 → implement F2 → review B1 →
  fix R1 → verify R1 → complete. Each used `openai/gpt-6.1-sol#high`, ended idle,
  and committed/pushed its report/state/evidence to the disposable local bare
  remote before the next launch. Final commit:
  `9b1d3d9ccf7e97f403ee810d3042076a06da087e`; tree/index and actual remote matched.
  Group review reproduced the deliberately seeded subtraction result −1 instead
  of 5; the separate fix used addition and verification obtained 5. This qualifies
  orchestration, **not blind reviewer quality or product behavior**.
- Current-code audit checked all six selections, retained history, report/session
  identity, committed owned-path sets and check evidence. Restarting completed
  fixture state with the current driver launched **no additional session**.
- Real eight-second timeout probe stopped after **8.43 seconds**, with session
  outcome `interrupted` and no active session remaining; exact high model retained.
- The actual project refused `run` with **Implementation is not authorized**.
  Its state remains `draft/not-authorized`, all 36 tasks/eight groups pending.

The six-session smoke used a frozen driver with SHA-256
`d77e8cc74d48efec247f1da26c8524e172f9520912e0941506b94221a15bace1`.
Later history/recovery guards are included in the qualified current driver,
SHA-256 `4f197fbfa1f139f545e752d66af02e995da496faccec6759a031da44500252de`;
the automated suite, current-code live-trace audit, completed restart and real
timeout probe passed after those additions. The entire six-session cycle was
not rerun after those guard-only changes.

### Retained private evidence

Archive: `~/.local/share/async-tt-wiz-e2e-tools/runner-20261021/`.
`SHA256SUMS.json` inventories **232 files**, verified after copying; manifest
SHA-256: `70562b7553c8bc1c842e30093d94afeab496a6219db9b8fd17aafebf4038acbc`.
It includes:

- `live/`: fixture source/history/local bare remote, frozen driver, runner log,
  `audit.json`, six committed unit reports/check logs and private Git journals.
- `model-probe/`: created/completed exact-model metadata and CLI output.
- `interrupt-probe/`: actual cancellation log and `result.json`.
- `qualified-source/`: current driver/tests/prompt/permission snapshot;
  `atw-runner-automated-tests.log` and the fixture/archive preparation scripts.

Original live scratch: `/tmp/opencode/atw-live-runner-20261021T135609Z/`.
The archive is an immutable evidence copy, not a relocated runnable checkout:
fixture Git configuration/session paths retain original absolute scratch paths.
Do not run agents or save changes inside the archive. Verify file digests against
its manifest when inspecting later. Raw local logs are not published or pushed.

Passing runner checks does not pass normal Android app/backend E2E, exhaustively
prove every failure path safe or activate the implementation plan. Inspect actual
build/test permissions and choose budgets before any owner-authorized activation.
The [Android environment qualification](e2e-environment-smoke-2026-10-21.md) has
its own separate evidence and limitations.
