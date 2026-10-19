# Async tabletop card client

A generic, rules-agnostic board/card-game engine with a native Android client,
asynchronous play, and cooperative editing. Playing Magic: The Gathering manually,
in the spirit of Cockatrice, is the initial use case—not built-in rules logic.

Actions synchronize immediately and form meaningful groups automatically, with
minimal taps and no gameplay Send step. An opponent reviews those actions one at
a time and can interrupt at any action boundary. Interruptions and conflicting
edits create new continuations in a retained game tree, with one active branch.
Every game edit must be recoverable, including mistaken interruptions and branch
deletion.

The engine records literal moves, rotations, counter edits, and action-linked
pings. The shared **Stack** and temporary combat area also need explicitly
designed card projections, paired combat-slot groups, and contextual quick actions.
These conveniences do not infer casting, targeting, costs, or resolution from
ordinary moves. Their interactions are being specified case by case.

**Project phase: specification only. There is no application implementation.**

Delivery: **1. behavior specification (current)** → **2. adversarial review and
refinement** → **3. UI drafts and decisions** → **4+. iterative implementation**.

Start with [the product principles](docs/design/01-product/overview.md), then
[the tiered design index](docs/README.md). The specification is a
working draft, not a claim that all design decisions have been made. No
implementation begins until its readiness gates are satisfied and the project
owner approves it.

Decision history, worklists and handoffs live separately in
[agent records](docs/agent/README.md). Resuming agents can use the
[session handoff](docs/agent/session-handoff.md).

Magic: The Gathering and Cockatrice are references for game compatibility and
interaction design, not claims of affiliation.
