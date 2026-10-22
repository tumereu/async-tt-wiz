# Delivery-first execution — D-307

Owner direction recorded 2026-10-22. **Optimize real elapsed time to deliver the
whole agreed R1**, not the number of checks, reports or infrastructure subtasks.
The owner approved testing/fixing affected behavior while building, with the full
suite and its resulting fixes at the end. Do not run a full suite again merely
"to be sure". This refines D-300–D-306's execution policy, not the product scope.

## Authority and application

This is an approved owner instruction, not an unselected proposal. It supersedes
earlier requirements to repeat broad clean builds, whole regression suites,
unchanged failure matrices or scaffold/device qualification at every child or
ancestor boundary. Reproducibility, required scenarios and final acceptance remain
obligations; their broad execution moves to final qualification where specified
below. Historical reports remain truthful records of what actually ran.

This change is prepared on a separate documentation branch. **Do not merge it,
migrate live state or repair the active harness while a worker/controller owns the
original checkout.** Preparing this branch neither stops nor restarts the loop.
The paused application procedure below is part of adopting the change, not a
claim that it has already happened.

## During implementation: affected checks, then continue

1. Select the next real user-facing capability in the approved B1–B8 order, with
   only the infrastructure needed to implement and exercise it. Work toward normal
   entry → import → invitation/preparation → shared table → the complete manual
   toolkit/history/lifecycle. These are delivery milestones, not an MVP replacing R1.
2. Build the affected code and run the tests that can credibly be affected by the
   change, including its actual ordinary user journey when available. Include
   directly affected integration/recovery/security/data-integrity tests; common
   user journeys take priority over rare fault permutations and harness polish.
   Severe evidenced security or accepted-state failures still take priority.
3. Fix demonstrated failures and rerun the failed/affected tests. If a failure
   reveals a wider affected area, expand to that area with a concrete reason. A
   genuinely cross-cutting change can justify a broad affected suite; a full-suite
   run is not a default save, session, parent, review or supervisor-exit ritual.
4. Once the affected checks pass, retain their output, save/push and continue.
   Fresh agents, another commit, ancestor closure and a desire for confidence do
   not by themselves invalidate passing evidence. Do not do a second clean build,
   disable caches, use `--rerun-tasks`, cold boot all devices or repeat a full
   passing journey without a changed relevant input, an actual failure to diagnose,
   or a specifically missing assertion. Use ordinary incremental builds by default.

Use the existing report fields: briefly identify the delivered capability and
affected check scope in `selectionReason`/`summary`. No new tracking framework,
quota of checks or planning-only session is needed just to choose test scope.
Passing tests do not mean unexecuted scenarios passed. Record genuinely deferred
required assertions with their existing source IDs and final owner, rather than
silently deleting them or describing them as already executed.

## Ancestor closure is evidence reconciliation, not recertification

Avoid further recursive splitting unless necessary for a bounded useful outcome.
Existing parent tasks still close only after their children and their **current,
explicitly scoped** outcomes are complete. The driver still selects one unit at a
time; this policy does not permit an agent to mark unrelated ancestors complete.

For a selected parent, inspect child implementation records and source/artifact
identities. Run a small current-unit evidence audit that checks those records and
any genuinely missing integration assertion. Retain that audit's actual output as
the unit's required check. Reference existing child results with their exact
records/commits and relevant input identities; do not copy old output as a new
execution or assume a matching task status proves integration.

If children already exercised the integrated path with unchanged relevant inputs,
do not run that path again. A chain such as I01f1b2 → I01f1b → I01f1 must not
trigger the same build, emulator fault matrix and cleanup suite at every level.
If an input changed, rerun only the affected checks or missing seam, not unrelated
parts of the ancestry. The audit is real validation, not an empty metadata pass.

## Findings and tooling: evidence, not imagined risks

Require an existing requirement, a reproduction or credible code-level failure
path, and a meaningful consequence before creating a blocking finding. No open-
ended theoretical hardening, style review or competitive/mission-critical rules.
Required uncommon behavior is still implemented; its broad test matrix can wait
for final qualification rather than blocking the next common user-facing feature.

Repair a tool when it obstructs the affected check or safe use of owned resources.
Do not expand it into an exhaustive platform certification project. Preserve
unrelated work and exact resource ownership, but do not equate independently
changing machine-wide listener/process inventory with agent-caused damage.
Use ownership/provenance and actual interference evidence to distinguish a real
cleanup bug from ambient host activity; fix a mistaken oracle and its affected
regressions, rather than rerunning until an otherwise idle host happens to match.
Never remove or stop unrelated resources to make a check pass.

## Final qualification, not perpetual full-suite repetition

Keep independent B1–B8 capability reviews and fresh targeted fix verification.
Earlier reviews examine delivered journeys and credible harmful failures; they
do not restart the entire regression/fault/device matrix. Final whole-product
qualification occurs in B8 after the agreed functionality exists:

- **I34:** full executable suite, complete coverage accounting, integrated normal-
  app journeys and deferred infrastructure/recovery/multi-device assertions.
- **I35:** remaining Android/version/tablet/upgrade and image-architecture checks.
- **I36/B8:** complete delivery evidence and D-303 interface-to-mock comparison,
  with independent review, fresh fixes and targeted rechecks.

Run the full suite, diagnose/fix actual failures, and rerun their affected tests.
Final acceptance evidence comprises that full run plus passing affected rechecks
for its repairs, with every required failure resolved or honestly blocked. **A fix
does not automatically require a second full-suite run.** Repeat a broad suite
only if a repair credibly invalidates that broad baseline, not simply because the
candidate changed. Record what ran on which inputs; do not relabel the initial
red run as green or claim a full pass on a binary tested only by affected rechecks.
No global confidence reruns for unchanged candidates, report-only commits or
unaffected documentation. External gates remain unverified without their required
authority/resources.

## Paused application and foundation scheduling

Apply against the **latest saved state**, not the snapshot used to prepare this
branch. Preserve all completed work, evidence, findings, source coverage IDs and
unmet obligations. The supervisor must not restart the old workflow during this
one-time owner-authorized scheduling correction.

1. Pause automatic restart, request graceful stop, and confirm the owned worker
   and controller have exited at a validated committed/pushed boundary. Preserve
   any interrupted handoff and recover it in its owning checkout first. A sibling
   worktree cannot acknowledge that checkout's active run.
2. Merge this documentation change into the idle owning branch, resolving any
   concurrent documentation changes without overwriting newer implementation
   state. This branch intentionally does not edit `implementation-state.json`.
3. If still pending, move **I01f3** (three-instance/cold-boot/repeated scaffold
   qualification) from I01f's B1 children to a standalone **B8** task immediately
   before I34. Retain its ID, status, functional outcome/checks and provenance;
   remove only its old parent edge, change its batch to B8, and set explicit
   prerequisites I01f2 and I31. Make it an explicit I34 prerequisite. Do not
   delete or mark it implemented. Update current I01/I01f outcomes/checks to
   distinguish their one-device foundation from this retained final obligation;
   keep the old wording's provenance through this record and Git history.
   Replace I01f3's obsolete scheduling-only instruction to keep I01f open with
   its new final-B8 ownership; do not reopen a completed foundation parent in B8.
   Reconcile coverage-ledger ownership/check dependencies without changing
   requirement IDs, deleting assertions or turning assigned checks into passes.
   This is an outside-active-unit owner-authorized migration, **not** a permission
   to bypass the driver's prohibition on reassigning tasks inside a unit.
4. Keep **I01f2** focused on one actual normal-APK/Appium startup path and the
   existing affected Android storage instrumentation. Reuse the already qualified
   service/routing lifecycle. Do not add more scaffold qualification children or
   repeat all platform fault cases before implementing account entry. Bring in
   additional devices when actual feature checks need them: the second-device
   account/reset journey at I04, two-player play at I12, third-instance recovery
   at I26/I31, and the remaining final matrices at I34/I35.
5. Close remaining selected foundation parents with the evidence-audit approach,
   not new broad qualification. Then implement I02–I04 and proceed through the
   approved user-facing capabilities. Transfer any other broad repeated foundation
   checks to I34 with explicit source/owner accounting, never an implicit waiver.
6. Fix the evidenced affected harness oracles, if still present, using focused
   regressions. If driver code/prompt changes, run its required deterministic
   runner regressions once for those changed orchestration inputs. Do not launch
   another live model qualification or the product's full suite just for prose,
   state scheduling, or an unrelated checkout becoming dirty.
7. Validate the migrated state's schema, dependencies, selection and retained
   final obligations using the existing driver validators and an offline focused
   scheduling regression. Validate links/whitespace for documentation. Save/push
   only owned changes, then hand control back for an explicit owner resume under
   the original model/grants/budgets and single-writer safeguards.

The supervised run's large time/session ceilings are limits, not permission to
spend them on repeated green checks. Explicit owner pause/resume remains binding.
Measure progress by newly usable capabilities and time to the complete game.

## Preparation validation — not live activation

Prepared from `77e47c8` on `docs/delivery-first-policy`. The one-unit prompt changed,
so `python3 scripts/test-implementation-loop.py` ran once: **91 deterministic
runner regressions passed in 19.3 seconds**, with disposable Git/fake-agent
fixtures, not live model calls. A separate in-memory scheduling probe validated
the documented move with the existing state validator/selector: the remaining
foundation closures and I01f2 lead to I02 while I01f3 stays pending in B8 and is an
I34 prerequisite. The probe did not write state or certify any pending task.
Documentation links/fences and whitespace were checked; unchanged historical
stale anchors were distinguished from new links. No product full suite, device
qualification, runtime/harness repair or live state migration was performed here.
