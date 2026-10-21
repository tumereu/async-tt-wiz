# Adversarial implementation-loop readiness review — 2026-10-21

## Repair disposition — 2026-10-21

The owner subsequently requested the recommended fixes. **IR-01–IR-07 are now
corrected**, with 48 passing automated qualification tests (the original 25 plus
23 new tests, several with multiple cases). The original findings and pre-repair
line references below are retained as provenance, not claims that the defects
remain in the repaired driver.

| Finding | Repair and retained verification |
| --- | --- |
| IR-01 | Count only the report's attempted subset; a four-finding fix/verify cycle completes without charging untouched issues. |
| IR-02 | Retain counts through awaiting-check and failed verification; two unsuccessful cycles halt with count 2 durably recorded. A second successful repair can verify, and only an evidenced new diagnosis resets its history. |
| IR-03 | Validate role/target transitions and status/disposition combinations; reject fixer self-verification, implement-as-review, unreported/unselected advancement and cross-batch/multiply-parented/unfinished children. Positive tests preserve planning splits, early critical fixes, scoped reopenings and independent lanes. |
| IR-04 | Check report/state and every referenced check output against actual committed blob bytes. Reject ignored/altered evidence and verify retained outputs in a clean clone. |
| IR-05 | Record owning worktree, branch and canonical state path; recovery from a sibling worktree, another branch/state or an ownership-free legacy record preserves the fence. Correct-context recovery remains covered. |
| IR-06 | I29 now says actor confirmation warning of removal for both, explicitly no opponent consent/vote; the absent-opponent and stale/retry checks remain assigned. |
| IR-07 | I19 explicitly owns persistent card/player Link, selection scope, uniqueness/no-op, removal and endpoint cleanup, with native-UI/server/recovery/history tests distinct from pings/source relationships. |

The [runner operations record](implementation-runner.md#current-review-repair-qualification--2026-10-21)
identifies the repaired driver digest and qualification limits. The regression
script is [checked in](../../scripts/test-implementation-loop.py). This repair
batch used controlled disposable agents/Git remotes; it did **not** rerun the prior
six-session live model cycle or claim independent application verification.
Application state remains untouched, `draft/not-authorized`, all tasks pending.
The activation/resource/permission/release checks below remain explicit pending
preconditions, not silently cleared gates. The plan now brings their inventory
forward to activation and states the implemented/verified/releasable distinction.

## Original verdict and scope

**Do not treat the current qualification as sufficient for an unattended whole-R1
run yet.** Five executable driver counterexamples remain, plus two task-wording/
coverage risks and known activation/resource gates. The broad B1–B8 order is
reasonable: real entry/decks/lobby before gameplay, shared authority from the
first shared operation, tests with delivery and reviews by coherent capability.
No application implementation, activation, product-policy change or driver repair
was performed by this review.

Reviewed commit: `c3123a953992903ede091a700965c82a21280bf1`. Sources: the task plan,
loop contract, driver, prompt, permissions, state, qualification tests, delivery
workflow and relevant product/verification/environment contracts. Findings below
were **open review findings at review time**, not owner decisions or entries in the inactive
application execution queue. High means a likely avoidable halt or violation of
the promised review loop; medium means a narrower recovery/evidence/scope risk.

## Executed evidence

- `python3 scripts/test-implementation-loop.py`: **25 tests passed**. They do not
  cover the five counterexamples below.
- Five extra probes used the existing disposable Git fixture, real local bare
  remotes and controlled fake agents, without a model call or product work.
  Probe source: `/tmp/opencode/atw-adversarial-loop-review.py`; results:
  `/tmp/opencode/atw-adversarial-loop-review-results.json`. The observed outcomes
  are recorded under each finding so the review survives scratch cleanup.
- The probes test orchestration acceptance, not application behavior or reviewer
  quality. The real state remains `draft/not-authorized`.

## IR-01 — High: bounded fixes falsely exhaust untouched findings' retry budgets

**Evidence:** `scripts/implementation-loop.py:373–385` loops over every finding in
the driver's selection, rather than the report's actually attempted subset.
`docs/agent/implementation-agent.md:13–16` explicitly permits a bounded subset.

**Reproduction:** start with four actionable open findings R1–R4. Session 1 fixes
only R1, session 2 verifies R1, session 3 fixes only R2. All three reports/checks/
saves are otherwise valid. Session 3 halts with:

```text
Repeated unresolved fix without a new diagnosis: R3
```

R3 and R4 were never attempted. R1 is verified and R2 awaits verification. The
driver has already accepted/pushed useful work but installs a manual-recovery
fence instead of continuing. A review with several unrelated findings is ordinary,
not an exotic ordering.

**Correction:** account only for findings actually attempted in the bounded fix
report, and leave unrelated counters unchanged. Add a multi-finding/subset fixture
that reaches all fixes/verifications without a false halt.

## IR-02 — High: failed verification resets the no-progress safeguard

**Evidence:** `scripts/implementation-loop.py:377–379` clears a finding's counter
as soon as a fix changes it away from `open`, including `fixed-awaiting-check`.
Verification runs never update the counter. The contract at
`docs/agent/implementation-loop.md:196–199` promises to stop repeated unresolved
fixes without a new diagnosis.

**Reproduction:** one finding with unchanged diagnosis alternates
`open → fixed-awaiting-check → open`. Eight valid committed/pushed runs produced:

```text
fix, verify, fix, verify, fix, verify, fix, verify
paused: configured session-count budget reached
next: fix R1
```

The only bound was the overall configured budget; the finding safeguard never
fired. A verifier repeatedly rejecting an ineffective repair is precisely the
case that should not consume the entire run allowance.

**Correction:** retain attempts through awaiting-verification, count unsuccessful
fix/verification cycles, and clear on verified closure or an explicit supported
disposition. Preserve the distinction between a new diagnosis and cosmetic record
changes. Test reopening after verification, not only fixes that stay `open`.

## IR-03 — High: a fixing agent can close its own independent verification

**Evidence:** `scripts/implementation-loop.py:67–108` validates status values but
not role-specific transitions. Its fix/verify check at lines 343–348 only requires
some reported finding record to change. The prompt and loop contract require a
separate fresh verifier.

**Reproduction:** a selected **fix** session changes its finding straight from
`open` to `verified`, sets the fully implemented batch `reviewed` with an ancestor
review commit, saves/pushes the proper report/state/evidence and exits. The driver
accepts the unit. The next selection is **complete**, with no verify session.

This need not be a malicious agent: conflating “I fixed and tested it” with
“verified” is a plausible handoff error. The driver currently does not enforce
the independence it advertises. Similar unrestricted task/batch transitions can
also defeat the one-unit/group-review boundary.

**Correction:** validate before/after transitions against the selected role and
target. A fix cannot newly verify its own repairs; independent closure needs a
verification report/session. Enforce child/batch inheritance and keep unrelated
progress unchanged except explicitly recorded invalidation/blocker bookkeeping.
Add negative transition tests, including implement-as-review and fix-as-verify.

## IR-04 — Medium: accepted check evidence need not be committed

**Evidence:** `scripts/implementation-loop.py:329–335` only checks that an evidence
file exists locally. The later changed-path comparison validates the report's
claimed paths, not whether every referenced evidence file is in the saved commit.
Operations documentation promises committed reports/check output.

**Reproduction:** write required-check output to a repository-relative ignored
`*-checks.log`, reference it in the report, and commit/push only state/report.
The driver accepts progress. Observed: `evidenceExists: true`,
`evidenceTracked: false`.

A normal build-output directory is often ignored. Cleanup or another checkout
then loses the evidence despite a successful handoff, weakening fresh-session
review and the final verification record.

**Correction:** require the referenced report evidence to exist in the resulting
commit, and ensure it matches the bytes validated locally. If large artifacts
must remain external, define a deliberate durable artifact reference/manifest
instead of accepting incidental ignored files. Test verification from a clean
checkout, not just `is_file()` in the writer's workspace.

## IR-05 — Medium: recovery can acknowledge the wrong worktree

**Evidence:** journals/locks are shared through the Git common directory
(`scripts/implementation-loop.py:243–245`). Active records do not identify the
owning worktree/branch. `recover()` at lines 425–441 checks the invoking checkout,
and skips report checks when that checkout does not contain the report.

**Reproduction:** create a second clean linked worktree before a run. Interrupt
the first checkout with unsaved state/report changes, then invoke recovery from
the second worktree with a clean HEAD matching its actual upstream. Recovery
returns `recovered` and removes the shared fence although the original checkout
is still dirty. Observed: `originalTreeStillDirty: true`,
`originalFenceStillExists: false`.

No file was deleted, but the required reconciliation of the interrupted unit was
bypassed, and a new run can begin elsewhere without accounting for that work.
Linked worktrees are explicitly supported by the operations contract.

**Correction:** store the owning worktree/location, state path and branch in the
active record. Recover against that ownership or reject invocation elsewhere with
a clear instruction. Add a two-worktree recovery test that preserves the fence
until the original unit is reconciled.

## Task-plan risks

### IR-06 — Medium: active deletion wording can invent an opponent-consent gate

`docs/agent/implementation-plan.md:210` describes I29 as “extra both-player
confirmation.” The primary contract,
`docs/design/03-contracts/accounts-decks-and-lobby.md:529–555`, says **either**
participant confirms a dialog warning that deletion affects both; opponent consent
and a deletion vote are explicitly excluded. I28 immediately before it does
require other-player acceptance, increasing the chance of carrying that behavior
into I29.

**Derived correction, no owner decision needed:** say “extra actor confirmation
warning that the shared game is removed for both; no opponent consent.” Retain
the existing incarnation/retry tests. This is ambiguity in the task summary, not
an unresolved product choice or an implemented application defect.

### IR-07 — Medium: persistent generic links have no explicit task owner

Persistent binary undirected card links, target picking, uniqueness/no-op behavior,
context-list removal and endpoint cleanup are confirmed in
`docs/design/03-contracts/visual-action-language.md:207–225`. They are not the same
as event-scoped Ping/Unping or source/projection relationship highlights.

I19 explicitly covers those latter tools; I17's enumerated card actions and I21's
combat/Stack work do not explicitly assign the generic Link workflow. The broad
contract/seed references and I01 coverage ledger might catch it, so this is an
ownership risk, not proof the plan excludes links. Without correction it is easy
to discover an ordinary manual-play capability only during I34's final accounting.

**Correction:** name persistent Link/remove-Link/endpoint cleanup in one B5 task
and its native-UI/server/history tests. Keep pings and source relationships distinct.
Do not create a new feature or turn this into an extra per-task review.

## Expected stops and activation work — not additional defects

1. **Authorization is still absent, correctly.** Activation must reconcile the
   explicit owner record, state and still-current stage/status instructions.
   This review, the smoke tests and tooling permission are not app approval.
2. **The driver defines complete more strictly than “R1 software implemented.”**
   `selection()` requires every `externalReleaseGates` entry passed/not-applicable.
   The four real entries remain unverified. I36 can document external gates but
   cannot autonomously obtain rights clearance, release secrets/hosting or hardware.
   Expect an honest global `blocked`, even with tasks/reviews finished, unless the
   prerequisites are supplied. Agree how to report software implementation,
   verification and external release readiness separately; never waive a required
   measurement or mark a gate passed to make the loop finish.
3. **Qualified emulators are not the entire lab.** The environment report only
   qualifies API-29 software-rendered Windows emulators. I33/I35 additionally need
   physical-phone frame evidence, current Android/tablet/non-GMS/upgrade paths and
   amd64/arm64 interface qualification. Inventory the available devices/access and
   signing setup before a long run, rather than first discovering the shortage in
   B8. Missing physical measurements are explicitly not passes.
4. **Actual build permissions/preflight remain unqualified.** The existing profile
   allows repository wrappers and broad Python execution but denies unlisted
   direct tools and external directories. Smoke sources/tools live outside the
   allowed scratch area; Windows launch/shutdown and normal-app Canvas targeting
   still need reproducible owned adapters. Exercise the intended I01 commands
   under the real per-session profile before assuming the earlier smoke qualifies
   unattended execution. This profile is not an OS sandbox, as already documented.
5. **36 tasks do not mean 36 bounded sessions.** I01, I14, I18 and I22–I26 contain
   substantial multi-layer work. Use the already-authorized child-splitting
   mechanism before coding oversized units, including explicit outcomes and tests.
   Session-count exhaustion is a pause; a timeout is a preserved halt requiring
   reconciliation, not an automatic continuation of a half-finished giant task.

## Original recommendation and remaining activation work

The original recommendation was to repair IR-01–IR-05 with focused disposable
regression tests, clarify IR-06 and assign IR-07, then rerun qualification including
the new negative/subset cases. That batch is now applied as recorded above, with
prior qualification provenance preserved. Perform the activation/resource/
permission checklist separately with the owner. Leave application authorization
inactive meanwhile.

The review does not establish that every possible runner or R1 defect has been
found; its blockers are concrete counterexamples, not a demand for speculative
hardening or a new unlimited product-specification audit.
