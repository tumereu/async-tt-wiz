# Tier 4 — Technical design proposals

- [Synchronization and storage](sync-and-storage.md): local durability, queues,
  server authority/replicas versus UI state, publication, branching, replay,
  proposed revision-checksum chaining/correction points, recovery, access and retention.
- [Architecture and operations](architecture-and-operations.md): proposed system
  boundaries, technology/provider choices, APIs, security, deployment and reliability.

These are proposals unless specifically confirmed. Native Android and dynamic
game/card data and artwork (D-243) are selected; other technology, hosting and
provider choices are not automatically approved. The
[E2E strategy](../05-verification/e2e-strategy.md) defines the confirmed normal-app,
real-backend test boundary and synthetic data/art requirement (D-244).
Mechanisms must implement the [behavior contracts](../03-contracts/README.md),
not replace missing behavior decisions with library defaults.

Tier numbers are reading/detail levels, not delivery stages. Technical work is
prepared for the relevant implementation slice; the entire technical design is
not a prerequisite for finishing behavior specification.
