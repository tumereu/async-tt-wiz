# Tier 4 — Technical design proposals

- [Synchronization and storage](sync-and-storage.md): local durability, queues,
  publication, branching, replay, offline recovery, access and retention.
- [Architecture and operations](architecture-and-operations.md): proposed system
  boundaries, technology/provider choices, APIs, security, deployment and reliability.

These are proposals unless specifically confirmed. Native Android is selected;
other technology, hosting and provider choices are not automatically approved.
Mechanisms must implement the [behavior contracts](../03-contracts/README.md),
not replace missing behavior decisions with library defaults.

Tier numbers are reading/detail levels, not delivery stages. Technical work is
prepared for the relevant implementation slice; the entire technical design is
not a prerequisite for finishing behavior specification.
