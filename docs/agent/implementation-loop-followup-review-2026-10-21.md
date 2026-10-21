# Follow-up adversarial loop-readiness review — 2026-10-21

## Owner-requested repair disposition — 2026-10-21

The owner requested the repairs. **IR-08–IR-13 are corrected in the driver,
protocol, plan and regression suite.** The current deterministic suite has **74
passing tests** (the earlier 48 retained plus 26 additions). A fresh live-session
qualification of this revised protocol is recorded in
[driver operations](implementation-runner.md#current-follow-up-repair-qualification--2026-10-21).
The original findings below remain pre-repair provenance, not claims that those
same defects remain. Tooling repairs do not authorize or activate application work.

| Finding | Repair and regression evidence |
| --- | --- |
| IR-08 | Independent group-review coverage is separate from findings. A last-task discovery now executes implement → fix → verify → first review, never verify → complete without review. |
| IR-09 | Review/verify direct-edit permissions are narrowed, and actual committed paths are checked against current outputs/state/approved test-only roots. Runtime/build repairs are rejected; adding a focused test remains supported. |
| IR-10 | Require and preserve the release-gate inventory. Operator gates cannot change inside a unit; an explicitly task-owned gate requires its exact completed owner task, reported transition, committed passing check output and authority record. No agent waiver. |
| IR-11 | Ready independent children retain inherited prerequisites; completed independent capabilities get due review. A blocked planning unit can save an evidenced runnable lane/unblock and exit before fresh work starts. |
| IR-12 | Implementation boundaries require a completed outcome, evidenced blocker or real planning-only split with concrete child outcomes/checks/dependencies. In-progress note churn and code mixed into planning are rejected. |
| IR-13 | Retain original group obligations and split pending review scopes in a planning-only unit; fresh sessions check bounded leaves with actual code anchors. Preserve remaining/blocked scopes and allow explicit scoped invalidation/unblocking. Full group coverage and finding fix/verification still precede closure, including D-303's final mock comparison. |

Six focused regressions were run red before driver repairs (three wrongly accepted
handoffs and three refusals of the required valid continuation); the complete
expanded suite passes afterward. Additional positive/negative cases cover all
eight original diagnostic observations, actual source/build changes, test-only
edits, gate authority, review checkpoint/split/narrowing/omission, fixes during a
partial review, evidence-backed scope blockers and inherited child prerequisites.
The [original diagnostic source](review-evidence/implementation-loop-followup-2026-10-21.py)
is retained as historical reproduction material; repaired guards can now stop it
at the first rejected handoff. Use the qualification suite for current regressions.

All application tasks remain pending, authorization remains `not-authorized` and
mode remains `draft`. The state adds inactive review-scope accounting, review
test-only paths and explicit gate authority; no gate was passed or waived. Actual
build/device-permission qualification, resource availability and external release
prerequisites remain separate activation work, not silently completed by these fixes.

## Original verdict and scope — before repairs

**Not ready for an unattended whole-R1 run yet.** The B1–B8 delivery order is a
reasonable foundation, and the earlier IR-01–IR-07 repairs remain in place. This
follow-up identifies six additional issues: three ways to accept an invalid
completion/review boundary, independent-work scheduling failures, a weak
no-progress guard and an unbounded grouped-review obligation.

Reviewed commit: `aeed73cf78596c5085f30e8c51205edc015d79e8`.
Driver SHA-256: `f6734bc4570aa59c2e4c5ac05a9d9b80f552b8dfb2b17c1db6a5597e4e1530e7`.
Line references below refer to that revision. These are **review findings and
recommended repairs**, not applied fixes, new product requirements or application
execution findings. No application implementation, activation, permission change
or delivery-stage advancement was performed.

Reviewed the task plan, state, runner, prompt, permissions, qualification tests,
delivery/review rules, environment evidence and relevant scope/runtime/E2E
contracts. This is a readiness review, not an exhaustive fresh audit of all 715
acceptance scenarios or proof that the task list omits no feature.

## Executed evidence

- `python3 scripts/test-implementation-loop.py`: **48 tests passed**.
- Eight additional diagnostic probes are retained in
  [the reproducible probe source](review-evidence/implementation-loop-followup-2026-10-21.py).
  Run `python3 docs/agent/review-evidence/implementation-loop-followup-2026-10-21.py`
  from this checkout. It prints observations; exit zero means the probe finished,
  **not** that the runner is safe. It imports the existing fake-agent/Git fixture,
  uses disposable repositories/remotes under `/tmp/opencode`, and launches no
  model sessions. Four accepted-handoff probes finished clean and actually pushed
  to their local bare remotes. Other probes exercise selection/transition refusal.
- The verifier probe also executes the arithmetic fixture and asserts output 5;
  the other check results are controlled fixture evidence, not real app tests.
- Actual-project `status`: `not-authorized`, next hint I01, no retained run.
  The real state and all application tasks remain unchanged.

## IR-08 — High: fixing a last-task discovery can replace the first grouped review

**Sources:** `scripts/implementation-loop.py:222–234,270–298`;
[loop contract](implementation-loop.md), priority 2 and “Review a group”;
[task plan](implementation-plan.md), B1–B8 review boundaries.

**Trigger:** the last implementation task in a batch completes and reports a
critical defect discovered in previously delivered work. This is explicitly
supported by `newFindingIds`. The finding needs immediate fix/verification before
new scope, but the integrated capability has never had its first grouped review.

**Observed:** the valid committed/pushed fixture executes:

```text
implement F1 → implement F2 (+ R1) → fix R1 → verify R1 → complete
B1.status = reviewed
No review session ran.
```

The verifier's clean finding result marks the batch `reviewed` whenever all tasks
are implemented. The state does not distinguish “first group review still due”
from “group reviewed, only findings remain.” Targeted verification is deliberately
not a broad group audit, so unrelated batch defects can bypass independent review.
The existing early-critical test only checks verification while another task is
still pending; it misses this end-of-batch case.

**Correction:** retain first-group-review completion separately from issue status.
A clean verification before that review must return the completed batch to
`ready-for-review`, not `reviewed`. Preserve targeted closure after a real group
review. Add both positive and negative boundary regressions.

## IR-09 — High: a verifier can repair code and certify its own new repair

**Sources:** `scripts/implementation-loop.py:193–208,228–234,529–537`;
[loop contract](implementation-loop.md#fix-and-verify);
[unit prompt](implementation-agent.md), one supplied mode and fresh verification.

**Trigger:** a fresh verifier discovers that a proposed fix is still wrong, makes
an easy correction while running the check, then marks the finding verified. This
is a plausible “finish the small fix” agent mistake, not a hostile-agent scenario.

**Observed:** with R1 awaiting verification and `arithmetic.py` still subtracting,
the **verify** session changes it to addition, executes it and obtains 5, saves
code/report/state/evidence, and marks R1 verified. The driver accepts the unit:

```text
session mode: verify
committed code: print(2 + 3)
code at B1.reviewedCommit: print(2 - 3)
next: complete
```

Role validation checks finding/status transitions but does not constrain product
changes by role. An honestly reported `changedPaths` list is enough. The new repair
therefore receives no separate fresh verification, and its review anchor describes
different code from the actual passing result. The earlier self-verification repair
blocks a **fix** role setting `verified`; it does not block this equivalent path.

**Correction:** explicitly forbid product repairs in verification and enforce a
product-source/build-input boundary, established with the actual repository layout
in I01. Permit reports and focused diagnostic/regression-test additions without
allowing runtime behavior changes to be certified in that same unit. A failed
verification should reopen the issue for a fix session. Apply the corresponding
report-not-product-fix discipline to grouped reviews. Test an actual source change,
not only a wrong JSON status.

## IR-10 — High: release gates can disappear during an ordinary implementation unit

**Sources:** `scripts/implementation-loop.py:67–123,318–323,477–490`;
`docs/agent/implementation-state.json:77–82`;
[plan completion rules](implementation-plan.md#completion-and-remaining-activation-work).

**Trigger:** an agent rewrites the state and omits the release-gate array or trims
an unresolved gate while reconciling completion/limitations. The activation and
runner controls are guarded, but the release-gate inventory is not.

**Observed:** start with an unverified `reference-device-performance` gate. The
first ordinary implementation unit changes `externalReleaseGates` to `[]`. The
driver accepts it; the rest of the normal implement/review cycle reaches complete
without a measurement, owner disposition or gate-specific evidence.

`selection()` treats an absent/empty gate list as all gates satisfied. Neither the
state validator nor before/after checks protect gate IDs, removals or status
transitions. This is different from the already-known honest final external-gate
blocker: here the blocker silently disappears.

**Correction:** retain the required gate inventory and reject disappearance or
unreported gate changes. Define which gate transitions agents can evidence and
which require an operator/owner record; require explicit evidence/authority for
`passed` or `not-applicable`. An ordinary implement/report unit cannot waive
hardware, rights or release prerequisites. Test removal, omission and unsupported
status changes, as well as legitimate evidenced completion.

## IR-11 — Medium: safe independent work can still be reported globally blocked

**Sources:** `scripts/implementation-loop.py:282–314,505–506,524–525`;
[loop priorities](implementation-loop.md#one-fresh-agent-per-bounded-unit), safe
independent work and explicit explained task adjustments.

Three reproducible cases expose incomplete independent-lane handling:

1. **A blocked child hides a ready sibling.** F1 has children F1a/F1b. F1a is
   blocked; F1b is pending with `dependsOn: []` and `independent: true`.
   `selection()` returns `blocked`. The recursive candidate returns immediately
   for the first unfinished child, even when that child has no candidate. The
   independent fallback explicitly excludes children.
2. **A completed independent batch cannot get its due review.** B1 remains
   blocked; B2's explicitly independent work is implemented and B2 is
   `ready-for-review`. `selection()` returns `blocked`. Only the first unfinished
   batch is considered for review; the independent fallback considers
   implementation tasks only.
3. **The blocked session cannot save a newly discovered safe lane as progress.**
   F1 is blocked, F2 pending, with no recorded lane yet. The fresh agent recognizes
   F2 is independent, adds the explained `taskAdjustments` and explicit dependencies,
   and reports progress without doing F2. The handoff is rejected with
   `No runnable work for claimed progress`. Reporting `blocked` instead would also
   fail once the new lane is runnable.

The result is an avoidable manual stop even though approved work/review remains.
This is particularly relevant when oversized I01/I14/I33–I35 work is split and
only one child needs unavailable tooling or hardware. Existing tests cover a
top-level independent task discovered *during* an implementation unit, not these
child/review/blocked-selection boundaries.

**Correction:** consider explicitly eligible independent descendants and their
due group reviews, preserving normal order for undeclared dependencies. Allow a
bounded planning/bookkeeping handoff to establish a justified lane from a blocked
selection, then launch a fresh implementation/review unit. Do not silently skip
blocked prerequisites or authorize the blocked session to code unrelated work.

## IR-12 — Medium: metadata churn satisfies the no-progress guard

**Sources:** `scripts/implementation-loop.py:183–191,326–327,505–510`;
[unit prompt](implementation-agent.md), progress and planning-only task splits.

**Observed:** five consecutive implementation units select F1, leave it
`in-progress`, add no children and change only its `note` field in task state.
Each has the fixture's routine committed report/check output. All five are
accepted; F2 stays pending. The run ends only at the configured run-count limit,
and its next selection is F1 again.

The guard compares entire task dictionaries, so a new “still planning” note counts
as advancement. The fixture's generic probe file also changes, but no acceptance
outcome is completed; the driver does not require one. This is not a demand to
mechanically prove code correctness. It is a missing structural distinction
between a bounded completed unit and repeated reporting about the same unit.

**Correction:** define meaningful implementation-boundary progress: completion,
an evidenced blocker, or a real planning-only split into concrete pending children.
If partial implementation boundaries are intentionally supported, record bounded
sub-outcomes explicitly rather than accepting arbitrary metadata changes. Stop
repeated note-only/non-outcome units; preserve valid evidence-only diagnosis work
and planning splits. Add a repeated in-progress metadata-churn regression.

## IR-13 — Medium planning risk: grouped reviews have no bounded continuation path

**Sources:** [plan task-splitting rules](implementation-plan.md#work-unit-and-dependency-rules),
B5/B6/B8 review scope; `scripts/implementation-loop.py:228–234`;
[runner timeout behavior](implementation-runner.md#commands).

Implementation tasks can be split before coding. Reviews cannot: once all group
tasks are implemented, a clean review handoff must set the entire batch reviewed.
A probe that saves partial `reviewCoverage` while retaining `ready-for-review` is
rejected with `Batch transition bypasses its selected role or remaining work`.

That constraint is proven; **it is not proven that a real B6/B8 review will exceed
the chosen timeout**. The credible risk is that a large integrated review or its
normal-APK tests do not fit one invocation. The only legal exits are then finish
the whole review, report an actual blocker/failure, or time out and require manual
reconciliation. A reviewer must not invent a finding to save partial coverage, or
declare the whole capability reviewed after sampling whichever part fits.

**Correction:** retain reviews by coherent capability, not after every child/task,
but support bounded review sub-units against an anchored code revision and explicit
remaining review scope. Mark the group reviewed only after all its required review
scope is covered. Alternatively, demonstrate that the intended complete group
reviews and required checks fit deliberately selected budgets. Do not assume the
3600-second example or the tiny arithmetic smoke establishes this.

## Plan and operational assessment

### Keep these parts

- Entry/decks/lobby before claiming gameplay E2E; real normal app/backend paths.
- Shared authority, durable ordering and accepted/predicted/UI separation from
  their first use, rather than retrofitting an authoritative local demo.
- Tests with implementation, grouped behavioral reviews and fresh targeted
  verification. The casual/cooperative focus correctly rejects speculative
  competitive and superseded reset-order machinery.
- Explicit full-R1 scope, complete traceability obligation and child splitting.
  No additional confirmed feature omission was established in this review;
  that is not a claim that the future I01 ledger is already complete.
- Current I29 actor-confirmation wording and I19 generic-Link ownership. Do not
  resurrect the earlier corrected findings or conflate those tools again.

### Known prerequisites still able to stop the run — not new defects

1. **Activation must reconcile all controlling instructions.** The state remains
   correctly unauthorized. Owner implementation authorization must be reflected
   consistently in AGENTS, delivery stage/status, handoff, plan and state, without
   promoting unrelated proposals. Flipping only the JSON flag leaves fresh agents
   reading contradictory “specification-only/current stage 1” instructions.
2. **The actual build/device permission profile is still unqualified.** The prior
   live runner smoke used tiny fixture commands, not Gradle/Android builds, remote
   Windows emulator management, artifact redaction or real Canvas targeting.
   Exercise the intended adapters under the actual per-session permissions before
   launch. Do not solve denied legitimate commands by secretly widening grants.
   The permission profile is not an OS sandbox; broad Python/wrapper execution
   still requires trusted scripts and resource ownership.
3. **Hardware and external release prerequisites are real.** Existing emulators
   do not prove physical-phone frame budgets, current Android/tablet coverage,
   ARM64 image qualification, upgrade signing or real optional-FCM delivery where
   configured. Inventory access early; split unavailable measurements from safe
   software work. Rights/signing/distribution/operator readiness cannot be invented
   by I36. Software-implemented, verified and externally releasable are different
   outcomes; the current runner's complete requires the external gates too.
4. **36 tasks are not a run estimate.** Even without splits, findings or reopenings,
   36 implementations + eight group reviews + final completion use 45 sessions.
   Each N-child split adds N+1 sessions relative to an unsplit parent; bounded
   fix/verify pairs add more. The example `--max-runs 100` can honestly pause before
   R1. Budget enough for the measured work; do not auto-restart past an operator's
   budget or call budget exhaustion completion.
5. **Unattended is not failure-proof.** Remote Git/auth/network failures, OpenCode
   or provider outages, tool denials, failed checks and timeouts can stop the driver
   by design. There is no automatic failed-unit recovery. Have an operator-visible
   stop/result and a practiced same-worktree recovery path; bounded read-only
   transient retries are different from rerunning implementation or bypassing a
   failure. Keep owned test-process/service cleanup part of that recovery.
6. **Final test size needs measurement.** I01 should establish executable task/
   scenario coverage accounting and usable commands, not spend an unbounded first
   unit trying to finish every feature's tests. Refine large I14/I18/I22–I26/I34
   selections into concrete children early, preserve cross-task pending assertions,
   and measure realistic suite duration as it grows. A committed text log is useful
   provenance, not independent proof that the right tests ran or that UI worked.

## Recommended next batch

Repair IR-08–IR-12 with focused negative and positive regressions; resolve IR-13's
review/budget mismatch. Keep the same selected model and no-fallback rule. Then
rerun deterministic qualification and a bounded fresh-session cycle against the
**current repaired driver**, including the new failure paths, before clearing
readiness. Qualify the actual build/device permission path and inventory resources
separately. None of those tooling checks activates application work.

Do not start I01, waive external gates or apply product behavior changes as a
consequence of this review. Preserve this report and the earlier findings as
provenance when repairs are later requested.

## Owner follow-up during this review — final interface comparison

The owner asked whether the plan ends with reviewing all interfaces against the
mocks and fixing differences. I32 previously supplied an earlier usability pass,
but did not explicitly require a complete final comparison after the later work.
The plan now adds the
[final B8 interface-to-mock gate](implementation-plan.md#final-interface-to-mock-review--required-b8-completion-gate):
I36 prepares a complete inventory/evidence, the fresh B8 reviewer compares every
current mocked state/orientation and additional implemented interfaces, and fresh
fix/verify units correct and recheck discrepancies. The loop contract and unit
prompt now carry that requirement. No task IDs/counts, application state or driver
code changed. This planning change does not resolve IR-08–IR-13; in particular the
larger explicit final-review obligation strengthens the need to address IR-13.
