# Tier 5 — Verification

[Acceptance scenarios and test coverage](acceptance.md) preserve the existing
T-001 onward scenario IDs and additional model, protocol, fault, compatibility,
end-to-end and operational test requirements.

The [full-stack Android test strategy](e2e-strategy.md) defines the confirmed
real-backend/normal-app E2E boundary, dynamic synthetic data/art, reset between
tests, user/deck-only setup helpers, and initial complete UI journeys (D-243/D-244).

The [grouping matrix](grouping-matrix.md) covers category transitions, longer
prefix-sensitive traces, ping/mode outcomes and planned E2E/playtest variants
(D-251), rather than defining separate rules for every individual button.

These are specified outcomes, not passing executable application tests. They
inherit each underlying contract's confirmed/proposed status; a scenario does not
approve its own feature.

The remaining-point completion adds T-647–T-663, revises superseded crash-durable
client/exposure oracles, and supplies [literal random/UUID v1 vectors](random-v1-vectors.md).
Runtime/device/performance budgets are specified targets, not measurements.

The architecture-review discussion (D-285–D-288) corrects speculative-disclosure,
client-sequence and restart/upgrade oracles directly, and adds T-664–T-670 for the
agreed FIFO/reconciliation, auth/HTTP, guard/resource, invitation and token cases.

Agent delivery gates and implementation-loop instructions live separately in
[the delivery workflow](../../agent/delivery-workflow.md).
