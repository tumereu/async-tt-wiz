# Implementation-loop driver

[scripts/implementation-loop.py](../../scripts/implementation-loop.py) is the existing
**serial** OpenCode V2 controller, not an autonomous parallel daemon. It executes the
[canonical contract](implementation-loop.md) using the [compact prompt](implementation-agent.md).
D-304 authorizes application work; D-309 authorizes [managed isolated lanes](implementation-orchestrator.md),
not concurrent invocations of this CLI. Preparation starts nothing and edits no live state.

## Commands

Run from the **owning checkout**, inspect ownership/STOP/retained-run/session state
first. Do not recover another worktree's unit or start alongside an existing controller.

```sh
python3 scripts/implementation-loop.py status
python3 scripts/implementation-loop.py run --max-runs 5 --timeout-seconds 86400
```

`status` launches no agent. `run` requires clean/pushed state, authorization, exact
model and explicit positive budgets. Use a blocking shell call with no tool timeout.
The legacy five-run invocation is not the managed concurrency policy: count all roles
against the original remaining total and supervise continuously, without batch checkpoints.
`--repo PATH`/`--state PATH` select context, not bypasses. `next` is only a guarded hint.

```sh
python3 scripts/implementation-loop.py stop
python3 scripts/implementation-loop.py recover
python3 scripts/implementation-loop.py clear-stop
```

`stop` prevents another launch while the active unit finishes/save/pushes. `recover`
acknowledges only an inspected/repaired halted run in its recorded worktree, branch
and canonical state path, after the old session is inactive and the handoff is
clean/pushed/valid. Use only if a retained run exists. `clear-stop` is an explicit
resume action, not routine cleanup. Ctrl+C interrupts the owned server session;
hard kills may leave retained work. Never delete a fence or discard files to recover.

Exit 0 means complete **or paused**; 2 is a saved global blocker, 1 a failed boundary,
130 interruption. Read actual state/result. A timeout interrupts only the owned
session/CLI, not the shared service or unrelated sessions. Budgets are not dollar caps.

## What drives each iteration

1. Acquire the shared Git-common-directory writer lock; check retained fence, stop,
   authorization/model, clean index/tree and HEAD against the actual configured remote.
2. Select due verify/fix, due group review, then ready implementation, honoring
   dependencies/independent lanes. Start a fresh session, never resume/fork old reasoning.
3. Supply `THIS RUN`, scoped per-session permissions and exact
   `openai/gpt-6.1-sol#high`; verify actual model and succeeded/inactive outcome.
4. Validate report identity, meaningful progress, roles/transitions, immutable controls,
   retained report/check bytes, exact changed paths, whitespace, ancestry/branch,
   clean tree and actual push. Only then journal resulting commit and continue.

Reported check commands are evidence, not shell commands for the driver to execute;
the validator cannot prove test honesty. Executable tests and independent reviews
remain required. Recovered transport failures may leave CLI exit 1: acceptance still
requires succeeded/idle exact-model server metadata and **all** normal handoff guards;
retain actual `cliExitCode`/raw error events. No other failure becomes success.

### Group review coverage and bounded continuation

See [canonical review/transition rules](implementation-loop.md#review-a-group): complete
coverage, role boundaries and meaningful checkpoints remain mandatory. No missing
first group review is supplied by targeted verification. Historical reports stay unchanged.

### Release-gate authority

See [gate rules](implementation-loop.md#external-release-gates-and-completion). No
inventory/authority changes inside units; exact owning-parent measurement evidence
is required. Rights/signing/operator gates cannot be waived by an agent.

## Permissions and scope

[implementation-permissions.json](implementation-permissions.json) is the explicit
ordered V2 per-session profile; units cannot widen it or runner/authorization controls.
The serial profile denies unit-spawned subagents. D-309 orchestration requires its
own authorized dispatch, not a unit secretly bypassing that deny. Review/verify
direct edits and committed diffs are limited to state/current output/approved test
paths, never runtime/build inputs. Check helpers require exact run-owned `supportPaths`.
Blocked/final roles cannot edit tests. Grants are not an OS sandbox; trusted scripts
still run with host authority. No global allow-all/model/config change is implied.

## Records and recovery

Committed reports/checks: `docs/agent/implementation-runs/`. Private journal:
`<git-common-dir>/implementation-loop/` contains `writer.lock`, `STOP`, `active.json`,
per-run raw/session/result records and persistent `fix-attempts.json`. Keep raw logs
private/redacted. Reports anchor starting code; journal records resulting commit/push.

Inspect logs, repair with affected checks, safely save/push, confirm recorded session
inactive, then recover in its exact ownership context. Recovery never stages/discards,
promotes tasks or launches agents. Legacy ownership fields require inspected provenance,
not guessed current paths. Do not infer missing review coverage from a verifier or
rewrite archived evidence. Failed push retries the same save, not implementation.

## Qualification

```sh
python3 scripts/test-implementation-loop.py
```

Deterministic fake-agent/disposable Git fixtures under `/tmp/opencode` qualify
selection, role/state/report/evidence/save/push/stop/recovery; not product acceptance.
Run once for changed driver/prompt inputs, not a fresh live cycle or product full
suite for prose. Live execution, device qualification and release readiness are
separate claims. See the [environment record](e2e-environment-smoke-2026-10-21.md).

The [D-309 qualification record](flow-improvements-2026-10-23.md) covers iterative
O(V+E) cycle validation, split-plus-delivery, evidenced ancestor closures and shared
tooling. These preserve independent review, role, committed-byte and release guards.
The CLI remains serial; new supervisors use [managed lanes](implementation-orchestrator.md)
and the [enforced resource plan](test-resource-plan.md), not concurrent CLI instances.

## Shared evidence commands

`python3 scripts/implementation-evidence.py --help` lists the exact CLI:

| Command arguments | Result |
| --- | --- |
| `report CONTEXT.json DETAILS.json` | Generate supplied identity/exact changed paths; details supply actual result/checks. Controller context is at `THIS RUN.contextPath`; state is edited separately. |
| `validate REPORT.json [--pending-evidence PATH]` | Check the current unsaved state/report/ownership; not product correctness or final pushed acceptance. |
| `retain-junit OUTPUT.json XML...` | Preserve exact XML bytes/hash/attributes, including failures. |
| `reconcile OUTPUT.json [TASK=]REPORT@COMMIT...` | Reuse exact committed child evidence in chronological order and identify changed inputs; no implicit parent pass. `TASK=` selects a child delivered in its parent's split unit. |

Capture actual commands with `python3 scripts/capture-check.py OUTPUT.json -- COMMAND ...`;
it creates parent directories and retains binary output/nonzero exits without sanitizing.
For validation's own foreground capture declare the required check in DETAILS, generate
REPORT, then capture `implementation-evidence.py validate REPORT --pending-evidence OUTPUT`.
Only that explicitly run-owned output may be pending until capture returns. Never use
`report` or `validate` as a substitute for running the delivered journey/affected tests.

## Historical qualification (lookup only)

[Immutable pre-D-309 operations/archives](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/implementation-runner.md)
preserve commands, digests, private archive paths and measured results. These are
evidence at their recorded revisions, not required per-unit reading or a claim that
the new parallel/report protocol has run. Retained anchors below preserve old links.

### Supervised planning-helper recovery — D-306, 2026-10-21

[Historical recovery/evidence](loop-recovery-2026-10-21.md).

### Autonomous repair qualification — D-305, 2026-10-21

[Historical save repair/evidence](save-recovery-2026-10-21.md).

### Current follow-up repair qualification — 2026-10-21

[Historical 74-test/eight-session qualification](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/implementation-runner.md#current-follow-up-repair-qualification--2026-10-21).

### Current review-repair qualification — 2026-10-21

[Historical 48-test repair qualification](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/implementation-runner.md#current-review-repair-qualification--2026-10-21).

### Earlier qualification — 2026-10-21, OpenCode v2.1.1

[Historical six-session/timeout evidence](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/implementation-runner.md#earlier-qualification--2026-10-21-opencode-v211).

### Retained private evidence

Private archives remain immutable; their manifests/locations are retained in the
historical record above. Never run/save agents in those archived checkouts.
