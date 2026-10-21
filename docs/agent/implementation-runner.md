# Implementation-loop driver

The driver is **[scripts/implementation-loop.py](../../scripts/implementation-loop.py)**,
using Python's standard library on Linux/WSL and OpenCode V2. D-302 authorized its
tooling qualification; **D-304 now explicitly authorizes application implementation**
under the [owner record](implementation-authorization.md). The real
[state](implementation-state.json) is `active` / `authorized`. No session starts
until the operator invokes `run` with positive budgets.

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

`next` is a guarded selection hint, not independent authorization. The real checkout
now has D-304 authorization, with I01 initially pending. For example, after choosing
these budgets, the operator can run:

```sh
python3 scripts/implementation-loop.py run --max-runs 100 --timeout-seconds 3600
```

The numbers above are examples, not a run launched by recording authorization.
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
   Explicit independent children and completed independent groups are included;
   inherited ancestor prerequisites still apply. A globally blocked selection may
   save planning-only evidence that makes a safe lane/review scope runnable, then
   exit before the new work starts in a fresh unit.
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
    changed-path set. Enforce role-specific state transitions: no implement-as-review,
    fix-as-verification or advancement of unselected tasks/findings. Explicit explained
    bookkeeping/reopening preserves safe independent work and affected-scope reviews.
    Check whitespace, ancestry, unchanged branch, clean tree/index and actual remote
   confirmation. Only then record success and start the next fresh session.

The report's check command is descriptive evidence, **not shell text for the
driver to execute**. The driver checks structure, reported exits and byte-for-byte
presence of report/state/check outputs in the resulting commit, not just local
file existence. Ignored build output alone is rejected. It cannot independently
prove test honesty or application correctness.
Normal executable checks and subsequent behavioral review remain required.

The driver does not weaken failed checks, force-push, stage files, discard changes
or automatically repeat implementation. A task-scoped blocker may leave another
approved independent task runnable. A global blocker or unmet release criterion
stops distinctly from completion. Already completed/pushed state is reported
without spending another model session.

### Group review coverage and bounded continuation

Every group retains its full `review.units` scope. Before checking an oversized
pending unit, a planning-only review session may split it into children, preserving
the original scope/ID. Fresh sessions add checked leaf IDs to `review.completed`
with each actual starting code commit. Unchecked or evidenced-blocked scopes stay
in the record. The driver rejects removed/narrowed obligations, unreported coverage
changes and completion with missing review. A last-task critical finding's targeted
fix/verification therefore cannot replace the first independent group review.
Ordinary verified fixes do not restart unaffected group coverage. Material changes
use explicit scoped invalidation/reopening; unavailable review scopes can later be
unblocked with retained availability evidence. See the
[unit schema](implementation-agent.md#bounded-group-review-checkpoints).

Implementation likewise needs a completed outcome, evidenced blocker or a genuine
planning-only split. Metadata-only changes to an in-progress task are rejected.
Budget exhaustion remains a pause, not completion; review checkpoints add real
sessions to the budget instead of assuming every group fits one invocation.

### Release-gate authority

The gate array is required and its IDs/order/authority are retained. Operator-owned
rights/signing/distribution/operator-readiness gates change only outside active
units with their authority record. The recorded task-owned reference measurement
gate may pass only in I33's completed parent unit with a `gateUpdates` entry,
retained passing required-check output and that run's report path as `record`.
Child evidence does not waive the parent's full qualification. Agent `not-applicable`
waivers, silent changes, omissions/removals and wrong-task passes are rejected.
Resolved operator gates likewise need a retained authority/evidence `record`.

## Permissions and scope

[implementation-permissions.json](implementation-permissions.json) is the explicit
ordered **per-session** V2 permission profile. It allows repository reads/edits,
normal inspection, trusted repository/build/test scripts and selected local test
tools; it denies unlisted operations, questions, subagents and direct edits to the
permission profile. D-305 allows evidenced driver/prompt repairs in
implement/fix roles; external tool file access is restricted except the approved
scratch area. No `--auto`, global allow-all config, new provider or credential is
installed. Pending permission requests halt instead of waiting unattended forever.

Before product activation, inspect this profile against I01's actual build/test
needs and explicitly approve necessary changes. A denied legitimate command is a
blocker to resolve, not permission for the agent to expand its own grants. The
permission profile and state runner/authorization controls remain immutable inside
units. Driver/prompt changes require D-305's scoped repair record and regression
checks; widening permission grants still requires the owner outside an active unit.

For review/verify, the driver appends a direct-edit deny-all with narrower allows
for state, **current-run** report/check outputs and `runner.reviewTestPaths` only.
Blocked/final-report roles allow no test edits. The actual committed diff is checked
as well: runtime source/resources, build inputs and unrelated/old report output
cannot be repaired or relabeled as check evidence in a verifier/reviewer. Test-only
roots cover the proposed conventional Kotlin/JVM/Android test layouts and must be
qualified against the actual I01 modules; do not import test code as runtime behavior
or widen the record inside a unit. A failed verification reopens the issue for a
fresh fix, rather than changing runtime code and certifying that repair itself.
These extra restrictions do not change global configuration or expand tool grants.

**This is not an OS sandbox.** Allowed Python/Gradle/repository scripts execute
with the host user's authority; shell directory inference is best effort. The
profile reduces accidental tool use and preserves explicit denies, but cannot
make untrusted scripts safe or enforce every filesystem/network effect. Agents
remain bound by project instructions: no production data/deployment, paid-resource
acquisition, broad destructive cleanup or unrelated work. Keep raw logs private.

## Records and recovery

### Autonomous repair qualification — D-305, 2026-10-21

The owner explicitly prefers autonomous troubleshooting, including whole/multiple
fresh sessions, to unattended halts on recoverable infrastructure errors. Current
units may diagnose/repair build, local test tooling and save failures instead of
immediately reporting failure. The
[unit schema](implementation-agent.md#autonomous-troubleshooting-and-orchestration-repairs--d-305)
also supports saved `troubleshooting` checkpoints: selected tasks stay in progress,
fixing findings stay open, and fresh run-owned evidence/diagnosis/next steps retain
the exact unfinished obligations. Replayed evidence/note churn is rejected. These
are not feature passes, independent reviews or failed product-fix attempts.

Implement/fix roles may repair the driver/prompt with `orchestrationRepair` reason,
exact changed paths and retained passing required-check evidence. The current
driver independently reruns the runner fixture suite before accepting that repair.
Permission-profile and state model/authorization/grant changes remain forbidden.
After a saved driver repair the controller replaces its own idle process and
retains the remaining session/time budgets; no parallel driver or extra unit grant.
All ordinary save/push, scope, review, truthful-evidence and stop guards still apply.

**85 deterministic runner tests and six save-helper tests pass** for this batch.
New coverage includes fresh-session troubleshooting continuation, multiple real
diagnostic advances, unchanged-evidence rejection, incomplete-checkpoint rejection,
uncompleted-task integrity, fix-attempt accounting, scoped orchestration provenance,
permission-expansion refusal and saved-driver replacement with remaining budgets.
The normal I01a offline clean build/JVM tests, APK assembly, lint, artifact/signature
and license checks also pass again. Exact evidence is in the
[save recovery record](save-recovery-2026-10-21.md) and its run report. This batch did
not launch another live model/driver cycle; earlier live qualification below applies
to its recorded revision, not proof of live execution of these new paths.

Missing external resources must not abandon features or halt remaining feasible
work: implement real configurable adapters and synthetic local checks without
violating grants; defer only unavailable external exercises. Rights, release
signing/distribution, physical-phone measurements and operator readiness stay
visibly unverified, not fake passes or reasons to skip implementable functionality.
Only safe handoffs may be accepted; explicit stop and operator budgets still apply.

Committed unit reports/check output go under `docs/agent/implementation-runs/`.
The exact JSON fields and state updates are in [the agent prompt](implementation-agent.md).
The driver writes private journals under `<git-common-dir>/implementation-loop/`:

- `writer.lock`, `STOP`, and persistent `active.json` while running/halted; the
  active record includes its owning worktree, branch and canonical state path.
- A directory per run containing `agent.jsonl`, completed `session.json` and
  `result.json` with the actual resulting commit/push status or failure reason.
- `fix-attempts.json`: count only actually attempted finding subsets. Counts survive
  awaiting-verification and failed verification; after two unsuccessful fixes under
  the same diagnosis, stop. The second proposed repair gets its fresh verification
  first. Verified/explicitly disposed issues clear counts; a different diagnosis
  needs a retained check-output reference (whitespace changes are not new diagnoses).
  Counts and known resulting commit/push status survive a guard-triggered halt.
  No-progress or missing/invalid reports stop immediately.

No attempt to embed a commit's own hash in that commit is made. Reports reference
the starting/reviewed code commit; the private journal records resulting commits.
Failed/interrupted records and dirty work remain available—never silently reset.
Legacy active states that say `reviewed` without the new independent coverage
record are rejected. Migrate them only from actual retained group-review reports
and inspected code anchors, outside an active unit; never infer review completion
from a fixer/verification result or clear history to make validation pass. The
real application state was still draft, so its new empty coverage records require
no completed-work migration.

After inspecting the logs, stopping any still-active recorded session and repairing
the underlying issue **with applicable checks passing**, acknowledge the halt:

```sh
python3 scripts/implementation-loop.py recover
```

Recovery must run in the recorded **worktree, branch and state-path** context;
a clean sibling worktree cannot acknowledge an interrupted unit elsewhere.
Equivalent normalized spellings of the same state path are accepted. Recovery
refuses missing/mismatched ownership, an active prior session, dirty/staged work,
an unpushed/divergent HEAD or unresolved failed required-check report. It archives the acknowledgement
and removes only the owned active-run fence. It neither stages/discards files,
promotes tasks nor launches an agent. Inspect task/finding state before explicitly
starting again; recovery is not a substitute for correcting an invalid handoff.
For a legacy active record without ownership fields, inspect its private logs and
session location and reconcile the original work first. Only after that manual
provenance check may the operator supply the exact `worktree`, `branch` and `state`
fields in that local active record and rerun recovery there. Never guess the current
checkout as the owner, remove the fence blindly or edit immutable evidence archives.

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

### Current follow-up repair qualification — 2026-10-21

The qualification observations below were recorded **before** D-304 activation.
Their draft/not-authorized project-guard outcomes are historical evidence of the
guard, not the current execution status. D-304 now satisfies that authorization
check; it does not retroactively pass normal-app/device/release qualification.

IR-08–IR-13 from the [follow-up review](implementation-loop-followup-review-2026-10-21.md)
are repaired. **74 automated tests pass**, retaining the earlier 48 and adding 26
regressions for first-review provenance, verifier/reviewer source/build boundaries,
scoped edit permissions, gate inventory/authority/evidence, independent descendants/
reviews/blocked planning, meaningful implementation progress and bounded review
split/checkpoint/fix/invalidation/unblock paths. Six focused probes were red before
the driver repair; the full expanded suite is green afterward.

Driver SHA-256:
`d51ee004fc88bc6603b5dc643231aae2c14b2a3612a2c27c00a9827870c2c460`.
Unit-prompt SHA-256:
`413e501ebc4663b68c437311b70b13460300b4cb7b8aff85274d563b28d7f6ef`.
Deterministic repair logs: `/tmp/opencode/atw-followup-repair-red.log` and
`/tmp/opencode/atw-followup-repair-check.log`; the checked-in qualification script
is the stable executable regression source. The project guard still refuses
**Implementation is not authorized**, and `status` shows I01/no retained run with
draft authorization intact. No application code/scaffolding, paid infrastructure,
global configuration change or release-gate waiver was introduced.

**Current live qualification passed with OpenCode v2.1.1:** eight fresh top-level
sessions executed implement F1 → implement F2 (+ last-task R1 discovery) → fix R1 →
verify R1 → review planning-only split → workflow review checkpoint → recovery/
integration review checkpoint → complete. Every session used the exact pinned high
model, succeeded, was idle and actually committed/pushed before the next launch.
Verification left the batch `ready-for-review` with empty coverage; the planning
unit retained that empty coverage; the first checked scope still left it ready;
only the final scoped review marked the group reviewed. No independent review was
skipped and no one-session/full-review assumption was needed.

The F2 arithmetic diagnostic was genuinely red (expected 5, observed −1); the fresh
fix, verifier and reviewers ran actual passing checks. The seeded defect/prescribed
fixture tests orchestration, **not blind review quality or product acceptance**.
The independent audit checked all eight exact model/idle/session identities,
committed report/state/check bytes, intermediate review statuses and actual remote
confirmation. The final three-test fixture suite passed again in a fresh Python
process. Restarting completed state launched **zero** additional sessions. Tree/
index and upstream matched, and no active-run fence remained. Fixture final commit:
`b955cd56761769053ac11f9a36a7a0ad2a241b2a`.

**Retained private evidence:**
`~/.local/share/async-tt-wiz-e2e-tools/runner-followup-20261021T161926Z/`.
The verified `SHA256SUMS.json` inventories **304 files**, manifest SHA-256
`2e6a970e1eca271ca8cf7c49165fd8bca92dc804299d533fc5a8d6f1a86db407`.
It retains the complete disposable repo/local bare remote, eight private journals,
committed reports/check output, frozen driver/prompt, current qualification sources,
red/green/guard logs, preparation/audit scripts, `independent-audit.json`, repeated
fixture test output and completed-restart result. Original scratch:
`/tmp/opencode/atw-followup-live-20261021T161926Z/`. This is immutable evidence, not
a relocated runnable checkout; paths in Git/session configuration remain original.
Do not run/save agents in the archive or publish raw logs. The older six-session
archive below remains separate provenance and was not overwritten.

This qualifies the revised orchestration/report/role protocol, not normal
Gradle/Android/Canvas builds, device permissions, real UI parity, reference hardware
or external release readiness. At qualification time, product authorization was
still draft/not-authorized; D-304 now supersedes that historical state only.

### Current review-repair qualification — 2026-10-21

**Historical IR-01–IR-07 repair batch; the follow-up qualification above is current.**
The owner requested the [readiness review's](implementation-loop-review-2026-10-21.md)
recommended repairs. IR-01–IR-05 now have focused regression coverage and IR-06/
IR-07 are clarified/assigned in the task plan. **48 automated tests pass**, retaining
the initial 25 and adding multi-finding bounded fixes, failed-verification cycles,
evidenced versus cosmetic diagnosis changes, second-attempt success, role/target
transition rejection, child inheritance/closure, planning-only splits, critical
mid-batch repairs, scoped reopenings, safe independent lanes, ignored/changed evidence,
evidence from a clean clone and worktree/branch/state/legacy recovery checks.

The regression probes were run red before the driver repairs and the complete
expanded suite was run green afterward. Actual-project `status` still reports
`not-authorized`, no retained run and next hint I01; a guarded one-run invocation
still refuses **Implementation is not authorized**, without launching a session.
Python syntax, documentation links and whitespace are checked separately.

Driver SHA-256 at that repair:
`f6734bc4570aa59c2e4c5ac05a9d9b80f552b8dfb2b17c1db6a5597e4e1530e7`.
Repair logs: `/tmp/opencode/atw-loop-repair-red.log`,
`/tmp/opencode/atw-loop-repair-check.log` and
`/tmp/opencode/atw-loop-repair-project-guard.log`; stable regression source is the
checked-in qualification script. No new live model/session cycle was run for this
repair batch; the six-session/timeout evidence below belongs to earlier revisions.
No model pin, API surface, global config, permission grants or application
authorization was changed. This is automated tooling qualification, not an
independent product review, full application acceptance or activation approval.

### Earlier qualification — 2026-10-21, OpenCode v2.1.1

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
- At that qualification, the code audit checked all six selections, retained history, report/session
  identity, committed owned-path sets and check evidence. Restarting completed
  fixture state with the then-current driver launched **no additional session**.
- Real eight-second timeout probe stopped after **8.43 seconds**, with session
  outcome `interrupted` and no active session remaining; exact high model retained.
- The actual project refused `run` with **Implementation is not authorized**.
  Its state remains `draft/not-authorized`, all 36 tasks/eight groups pending.

The six-session smoke used a frozen driver with SHA-256
`d77e8cc74d48efec247f1da26c8524e172f9520912e0941506b94221a15bace1`.
Later history/recovery guards were included in the then-qualified driver,
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
