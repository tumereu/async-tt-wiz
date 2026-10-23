# Session handoff — current execution context

Updated 2026-10-23 for D-309 tooling/instruction integration. This is continuation context,
not product requirements or a live-state snapshot. Current contracts and actual
owning-checkout state take precedence. Historical handoffs are lookup-only.

## Migration / fresh-supervisor handoff — read first

The owner stopped the old worker and supervisor, then explicitly requested a **new
fresh-context supervisor after these changes**, parallelizing efficiently until all
feasible R1 implementation/reviews/final tests/fixes are done. Do not restart normal
serial batching. The old supervisor received a durable no-restart coordination message.
Inspect the **owning** checkout's Git, STOP, session/process/lock state and retained
journal before adoption. The fresh supervisor may resume under this explicit request
only after the old handoff is reconciled and the qualified changes are applied.
Further explicit owner pause still overrides continuation; no routine approval stops.

## Latest owner execution direction — D-309

- All discussed flow improvements are approved, with **net shorter instructions**:
  short root guide, compact unit prompt, canonical [loop contract](implementation-loop.md).
  Read history only when a concrete provenance question needs it.
- [Managed orchestration](implementation-orchestrator.md): at most two isolated
  implement workers plus an independent review lane, one integration/state writer,
  explicit path/contract/resource leases and dependency readiness. The existing
  serial CLI is **not** a parallel runner; no competing old loop or automatic daemon
   is authorized by these documentation edits. Enforce dispatch/handoff gates; hold
   and repair an unsafe lane, never silently fall back to the retired serial workflow.
- Preserve exact `openai/gpt-6.1-sol#high`, no fallback, original remaining total across
  all roles, at most five fresh launches before an idle supervision assessment,
  86,400 seconds maximum per session. Check actual delivered journeys/diffs/checks
  for drift, refocus and continue. Drain on stop; retain work and explicit blockers.
- Qualified improvements: iterative O(V+E) graph checks, split-plus-first-child
  `implementationUnits`, evidenced `ancestorClosures`, shared evidence commands,
  disk-backed owned APK artifacts and native UI/phase helpers. Follow the
  [exclusive-resource plan](test-resource-plan.md); partial native phases are not
  complete E2E passes. No archived report rewrites or worker state replacement.

## Last known delivery context — verify, do not replay

The stopped unit saved/pushed `4ffaf58`: I11 Ready/start is implemented; I12 is split
into pending I12a cancellation/server/session and I12b native/full lobby journey.
There are **88 durable serial reports**. B1/B2 reviews remain valid; B3 review is still
required after I12. Reconcile the old `20261023T031700Z-19bd0df8` journal: it may still
say running although its exact-high session succeeded/idle and writer lock is free.
Preserve that record and recover only in `/home/tuomas/projects/async-tt-wiz`.
I01f3 remains final B8 qualification before I34, not an account-entry prerequisite.

Original D-306 budget started after the first four reports: 84 charged serial units
leave 2,916. Conservatively reserve five more for this repair's current supervisor,
two preparation workers, independent review and the requested fresh supervisor:
**at most 2,911 further launches**, shared by all roles/retries/replacements. Record
actual IDs/debits in the private orchestrator journal; do not restore historical budgets.
Assess at the idle migration boundary before the first five managed worker launches.

First dispatch must derive a real readiness/path plan from current sources. I12b
depends on I12a; do not pretend both are independent. A second lane may take a
documented genuinely independent later child (for example pure tabletop semantics
against unchanged accepted play types), with explicit prerequisites/contract owner;
preserve all original outcomes and due group review. No new planning-only agent is
needed just to choose the lanes. Wait/serialize when isolation cannot be demonstrated.

## Preserved obligations and pointers

Entire R1 implementation remains authorized (D-304), not production/public release.
[Plan](implementation-plan.md) owns outcomes; [delivery-first policy](delivery-first-execution.md)
owns affected-check/final-qualification scope; [serial operations](implementation-runner.md)
owns existing CLI/recovery. Independent B1–B8 reviews, fresh fixes/verifiers, final
full-suite failures and complete mock/orientation comparison remain required.
Security/privacy/server authority, scoped grants, honest coverage, release rights/
signing/hardware/operator gates and validate/commit/push boundaries are unchanged.
UI work uses cumulative F01–F88 and current supersessions; same-URL full review
publication still applies. No UI document was changed by this flow preparation.

## History (not per-unit reading)

[Full prior handoff at immutable baseline](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/session-handoff.md)
retains all earlier design/UI/authorization checkpoints. [Decisions](decisions.md)
and [authorization](implementation-authorization.md) preserve owner provenance;
older “next”, paused/not-authorized and test-count statements are historical, not
instructions to undo newer state or rerun qualification.

## Qualification

[Actual tooling/native checks and instruction reduction](flow-improvements-2026-10-23.md).
I01–I36 outcomes and complete final UI gate remain unchanged. Preparation did not
run product full-suite/device acceptance or clear release gates. The requested fresh
supervisor is a separate exact-high session, not a continuation of repair reasoning.
