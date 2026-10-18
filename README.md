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
pings. An area named “Cast” is just an area; players interpret what moves and
pings mean. It never infers casting, targeting, costs, or resolution.

**Project phase: specification only. There is no application implementation.**

Start with [the specification index](docs/README.md). The specification is a
working draft, not a claim that all design decisions have been made. No
implementation begins until its readiness gates are satisfied and the project
owner approves it.

Magic: The Gathering and Cockatrice are references for game compatibility and
interaction design, not claims of affiliation.
