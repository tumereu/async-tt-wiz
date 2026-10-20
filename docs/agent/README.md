# Agent records — separate from the product design

Read the [tiered design](../README.md) to understand the product. These files
support continuity and traceability; they are not another competing specification.

- [Decisions](decisions.md): owner decisions, superseded proposals and open-question IDs.
- [Session handoff](session-handoff.md): current work state, continuation context and constraints.
- [Touch-action review](touch-action-review.md): capability inventory and review worklist.
- [Specification audit](specification-audit.md): known wording/status drift and interpretation risks.
- [Adversarial review — 2026-10-19](adversarial-review-2026-10-19.md): source-linked findings, counterexamples and a documentation-fix session plan; not new requirements.
- [Adversarial review — 2026-10-20](adversarial-review-2026-10-20.md): follow-up review of cross-feature contradictions, generic operation gaps, UX consistency and residual drift; detailed handoff for a new fix session, not approved changes.
- [October 20 follow-up review](adversarial-review-2026-10-20-follow-up.md): F-01 withdrawn under D-254; D-257 resolves F-05 with singleton ending groups; D-258 resolves F-02 with deliberate-switch Redo clearing. D-259 clarifies server/UI authority; other derived repairs remain.
- [Current review after D-259](adversarial-review-2026-10-20-current.md): G-01–G-07 cover historical bulk handoff, ordered recovery, late exposure, mixed-token lowering, randomness agreement, leading-ping wording and active specification drift. Review findings, not selected new behavior.
- [Delivery workflow](delivery-workflow.md): readiness gates and the later implementation loop.

Use decision history to establish approval/provenance, then reflect that result
in the appropriate design tier. An unapproved proposal, old handoff, or worklist
must not silently override current confirmed behavior. Record contradictions for
resolution instead of guessing.

Follow D-178 and [the project instructions](../../AGENTS.md): validate, commit and
push after every completed edit batch using `./scripts/save.sh <edited paths...>`
and its fixed message. This replaces D-57's occasional-checkpoint cadence.
Distinguish approved decisions from proposals. The normal next-question convention
is subject to the latest owner direction: do not manufacture tiny follow-up decisions instead of
maintaining a simple, consistent design. File reorganization is not new behavior
approval or permission to implement.
