# Async tabletop card client

A generic, rules-agnostic board/card-game engine with a native Android client,
asynchronous play, and cooperative editing. Playing Magic: The Gathering manually,
in the spirit of Cockatrice, is the initial use case—not built-in rules logic.

Each individual mutation synchronizes immediately and extends or branches the
state tree. Automatic groups control playback steps and concurrent animation,
not publication or node boundaries. A player can pause playback and scrub to an
exact mutation on the bottom timeline. Interruptions and conflicting
edits create new continuations in a retained game tree, with one active branch.
Every game edit must be recoverable, including mistaken interruptions and branch
deletion.

The engine records literal moves, rotations, counter edits, and action-linked
pings. The shared **Stack** and temporary combat area also need explicitly
designed card projections, paired combat-slot groups, and contextual quick actions.
These conveniences do not infer casting, targeting, costs, or resolution from
ordinary moves. Their interactions are being specified case by case.

**Project phase: implementation authorized (D-304); foundation in progress.**
The initial normal Android scaffold and shared JVM build exist. Account/gameplay,
real backend and storage capabilities are still pending, not simulated by the
scaffold. See [build commands and qualification limits](docs/agent/foundation-build.md).

Delivery: **1. behavior specification** → **2. adversarial review and
refinement** → **3. UI drafts and decisions** → **4+. iterative implementation (current)**.

Start with [the product principles](docs/design/01-product/overview.md), then
[the tiered design index](docs/README.md). The specification is a
working draft, not a claim that all design decisions have been made. No
extra behavior is authorized by the build foundation. The current owner
[authorization](docs/agent/implementation-authorization.md) preserves all review,
verification and external release gates.

Decision history, worklists and handoffs live separately in
[agent records](docs/agent/README.md). Resuming agents can use the
[session handoff](docs/agent/session-handoff.md).

Magic: The Gathering and Cockatrice are references for game compatibility and
interaction design, not claims of affiliation.
