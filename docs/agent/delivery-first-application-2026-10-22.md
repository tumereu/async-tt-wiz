# Delivery-first policy application — 2026-10-22

Owner explicitly confirmed implementation stopped and requested applying the
prepared changes. Authority: [D-307](delivery-first-execution.md), following
preparation commit `2d0f79c` on `docs/delivery-first-policy`.

## Safe paused boundary

The owning checkout was clean on `master` at `f0a968f`, equal to its upstream.
The worker/controller processes were absent and the writer lock was free. The
last unit, I01f1b (`20261022T034912Z-d4685909`), had saved its state/report/checks
and pushed, but the external active-run journal still said `running`.

`python3 scripts/implementation-loop.py recover` in the owning checkout checked
session inactivity, the clean/pushed boundary, required check exits and state
validity, retained the recovery provenance and cleared that stale fence. It did
not discard work, fabricate a successful driver result, launch an agent or resume
the loop. The existing **STOP flag remains set**. Preparation was merged without
overwriting the newer I01f1b implementation or its reports.

## Applied scope and scheduling changes

- I01f3 remains **pending**, with the same three functional checks. Its batch is
  now B8 and it is a standalone task immediately before I34, depending on I01f2
  and I31. I34 explicitly depends on I01f3. Its old instruction to leave I01f
  open is replaced by final-B8 ownership, not a reduced functional outcome.
- I01f now has I01f1/I01f2 as its foundation children. I01/I01f's current scope is
  one-device setup plus affected actual Android storage tests; broad repeated
  clean/reproducibility/multi-device qualification is retained under I34/I01f3.
- Pending I01f1 closure audits exact existing child records and missing affected
  integration, instead of repeating their complete build/device/fault matrix.
  I01f2 retains the actual one-device Kotlin/Appium scenario and all 14 existing
  Android storage instrumentation tests, not another platform certification unit.
- Completed task statuses/records, all 33 historical run paths, findings, model,
  authorization, permissions, external release gates and review obligations are
  preserved. **No pending task is marked implemented by this migration.**
- Coverage-ledger source pins and I01/I34 check scopes are reconciled with the
  updated plan. All 875 P/T/F definitions/assertions remain accounted for; no
  assigned/pending scenario becomes an executed pass. Existing parent-owned
  requirement portions remain under I01, with final completion still owned by I34.

## Affected harness-oracle repair

Four device qualification scripts no longer assert that ambient Windows process/
listener or Docker inventories remain frozen. They retain before/after snapshots
and explicit added/removed deltas as diagnostic evidence, without claiming that
independent host activity proves controller-caused damage or resource preservation.

**Hard ownership/safety checks remain:** foreign serial/mapping/PID refusal,
exact process creation identity checks before signalling, owned native/ADB/Appium/
watchdog/launcher/relay shutdown, owned ports disappearing and removal of owned
readiness/routes. Controller cleanup and Windows signalling behavior are unchanged;
the added helper only computes an inventory delta. Actual interference evidence
still requires repair. No unrelated process, listener, container or network is
stopped/modified to satisfy the check.

## Validation scope and resume handoff

Affected executable checks passed:

| Command | Result |
| --- | --- |
| `PYTHONPATH=tests python3 -m unittest test_e2e_devices -v` | 36 passed, including new inventory-delta cases and existing foreign-resource/cleanup refusals. |
| `python3 scripts/test-implementation-loop.py SelectionTests` | 16 passed, including three new deferred-matrix scheduling regressions. |
| `PYTHONPATH=tests python3 -m unittest test_foundation_data.CoverageLedgerTest -v` | 14 passed, preserving all 875 IDs and rejecting false execution/missing provenance. |
| `python3 scripts/coverage-ledger.py --write` | Source-pinned scopes/rows reconciled; 875 definitions retained, no product pass claimed. |

An offline state/ledger audit confirmed the preservation and selection assertions
below, and changed-script syntax plus documentation links/whitespace were checked.
The prepared prompt's 91 deterministic runner regressions already passed at
`2d0f79c`; controller code and that prompt are byte-identical to those validated
inputs. They were not rerun for confidence. This application does not claim fresh
actual-device qualification or completion of any pending product test. No full
product/device suite, live model session or application build was run here.

Before saving, compare the migrated state with `f0a968f`: preserve statuses,
history, findings and external gates; validate dependency selection and final
obligations. The next selected unit is **I01f1**, an evidence-reconciliation closure,
then **I01f2** actual one-device tests, followed by short I01f/I01 evidence closures
and **I02** account implementation. I01f3 must not block this progression or B1
review; it must still precede final I34 qualification.

After save/push, the supervisor may resume **only on owner instruction**, clearing
the stop flag through the existing command and starting one owned driver with the
original selected budgets/model. It must read this handoff and D-307, not recreate
the old repeated ancestor qualification workflow.
