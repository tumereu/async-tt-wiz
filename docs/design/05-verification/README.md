# Tier 5 — Verification

[Acceptance scenarios and test coverage](acceptance.md) preserve the existing
T-001 onward scenario IDs and additional model, protocol, fault, compatibility,
end-to-end and operational test requirements.

The [full-stack Android test strategy](e2e-strategy.md) defines the confirmed
real-backend/normal-app E2E boundary, dynamic synthetic data/art, reset between
tests, user/deck-only setup helpers, and initial complete UI journeys (D-243/D-244).

These are specified outcomes, not passing executable application tests. They
inherit each underlying contract's confirmed/proposed status; a scenario does not
approve its own feature.

Agent delivery gates and implementation-loop instructions live separately in
[the delivery workflow](../../agent/delivery-workflow.md).
