# 1. Product contract

## 1.1 Intent

Build a generic, rules-agnostic board/card-game engine with a native Android
client. Make it practical for two cooperative people to play without being online
at the same time. Manual Magic: The Gathering is the initial use case, not the
engine's domain model. Preserve the freedom of a shared tabletop while making
asynchronous play understandable and mistakes easy to repair.

Players interpret card text, pings, area labels, and the sequence of physical
actions. The software records those facts literally. It must not infer that a
move into an area named “Cast” casts a spell, that a ping declares a legal target,
or that a later move resolves anything. This excludes semantic inference, not
merely automatic rules enforcement.

## 1.2 Confirmed requirements

| ID | Requirement |
| --- | --- |
| P-001 | The gameplay client is native Android. |
| P-002 | Asynchronous play is a primary workflow, not an added turn timer. |
| P-003 | Actions form sensible groups that an opponent can progress through. |
| P-004 | An opponent can interrupt at an action boundary and react. |
| P-005 | History is a tree, with exactly one active branch in each game. |
| P-006 | An interruption makes its new branch active; no approval vote is required. |
| P-007 | Players can delete the interruption and return to the earlier continuation. |
| P-008 | Game edits have undo/recovery support; submitted game-wide chat is non-undoable (D-94). D-89–D-91 remove ordinary undos from the visible tree but retain paths through their last information gain. D-95 event-comment nodes are also retained checkpoints on ordinary tree paths. Internal recovery is distinct from visible history. |
| P-009 | Either participant can manipulate any participant's tabletop state. |
| P-010 | Hidden information is accessible to everyone; cooperation, not secrecy enforcement, is the trust model. |
| P-011 | The first release fully targets two-player games. |
| P-012 | Downloaded games can be read and local actions queued offline, with synchronization later. |
| P-013 | Produce and approve a full specification before implementing software. |
| P-014 | Cover the manual actions needed to play MTG, rather than a small subset limited to a demonstration game. |
| P-015 | Completed game actions synchronize immediately and automatically; there is no gameplay Send step. |
| P-016 | Minimize taps and other user actions throughout the UX. |
| P-017 | Conflicting game edits automatically create branches rather than requiring manual reconciliation before synchronization. |
| P-018 | Drag cards to move them; drops snap to grid locations or slots. |
| P-019 | Cards can form piles with underlying cards peeking out diagonally. |
| P-020 | Single tap taps a card; double tap opens all available actions; hold shows large art/text. |
| P-021 | The board has distinct areas, including special library, graveyard, and exile piles. |
| P-022 | Game flow is understandable mostly without writing explanations; optional text input and messages remain supported. |
| P-023 | Pings are associated with a specific action rather than floating independently of the game flow. |
| P-024 | The engine is generic and rules-agnostic; MTG is a manual-play use case, not hard-coded mechanics. |
| P-025 | Present literal actions and pings as recorded; players infer their game meaning. |
| P-026 | Area names, card metadata, and pings must not imply game-specific state transitions or automatic side effects. |
| P-027 | Turns and MTG phases/steps have first-class engine tracking, not only arbitrary markers, notes, or tokens. D-105 removes priority tracking and its UI from this release, superseding that part of D-96 and all of D-104. Players judge priority/interruption legality; the engine does not gate edits or branches on it. |

## 1.3 Proposed first-release boundaries

Included:

- Private two-player tables, card/deck import, editable setup, manual play,
  participant-recorded completion, and fresh tables retaining earlier history.
- Configurable areas and ordered piles, card movement/orientation/faces, custom
  objects, named counters/values, generic links/markers, and free-form correction.
- Individual action playback, suggested editable groups, branching, undo/redo,
  shared history, and contextual communication.
- Persistent games, local offline storage, reconnection, invitations, and
  notifications.
- Layout/data presets for manual MTG play, including areas named Library, Hand,
  Graveyard, Exile, Cast, and optional Command/extra areas. Labels/configuration
  do not install a rules engine or mechanical meaning.
- D-55/D-56 add a requested move/reset convenience on entry to configured
  Graveyard/Exile/Library: remove card counters and restore database-default
  corner text (printed P/T for creatures). This is a declared composition of
  generic edits. D-64/D-65 condition other metadata on source availability and
  retain per-game/per-face defaults, with blank editable fallback for missing
  data; provider choice remains open.
- D-83/D-84 make Clone create token instances even from ordinary cards. Tokens
  moved into configured Library/Graveyard/Exile disappear from current state
  across move controls (D-85), with synchronized history/undo recovery. This is
  another declared destination
  convenience, not inferred card-text/type/label behavior or historical erasure.
- D-96, as narrowed by D-105, adds first-class turn and phase/step tracking.
  This supersedes the earlier marker-only scope, without authorizing inferred
  card rules. Remaining progression details and touch controls need review.
- D-97 allows hard-coded MTG phases for this tracking; no configurable phase
  system or phase editor is required. This explicit scope choice does not add
  card-script processing or inferred effects.
- D-98 includes individual MTG steps. D-99 requires phase/step-dependent quick
  actions. D-100 includes standalone Untap all/Draw one and one-press variants
  that also advance tracking. D-101 advances to the next step: Untap → Upkeep;
  Draw → Precombat main at the phase boundary. Other shortcuts/progression rules
  still need review.
- D-102 adds standalone Next step without card edits and tap-indicator direct
  phase/step choice for skips/corrections without intermediate card operations.
- D-103 tracks/displays turn player and number. Next step from Cleanup starts
  the other player's Untap and increments the number, without untapping/drawing.
- D-106 adds quick End turn from any phase/step using that same next-turn
  transition, without skipped card operations. No manual turn-number editor.
  D-107 keeps it visible beside Next step, one tap/no routine confirmation, with
  Undo for mistakes.
- D-108 chooses the starting player during setup and begins turn 1 at
  Beginning/Untap without automatic card operations.
- D-105 removes engine priority state, indicators, Pass controls, and priority-
  legality validation. Players queue actions and use interruption/branching to
  respond; legal timing is their responsibility. Turn/phase/step controls remain.

Not included in the proposed first release:

- Game-specific semantic actions, inferred casting/targeting/activation/combat,
  inferred rule-based resets beyond declared conveniences, trigger detection,
  legal-target validation, or card-script execution. D-96 separately requires
  explicit turn/phase/step tracking; remaining progression details are open.
  D-105 excludes priority tracking, UI, and legality validation from this release.
- Matchmaking, rankings, tournaments, clocks, wagering, public spectators,
  public chat, AI opponents, or competitive anti-cheat.
- Multiplayer UI and coordination, even if identifiers are not hard-coded to
  “player one” and “player two.”
- Browser, iOS, or desktop gameplay clients.
- Simultaneous hidden-choice cryptographic protocols.
- Automated arbitrary historical sequence replay after a branch changes.

Detailed first-release coverage still needs approval. The generic/no-inference
boundary is confirmed. “All MTG actions” means the generic primitives let players
represent their own play, not that the engine recognizes those mechanics.
[The action catalog](04-game-actions.md) defines that distinction.

## 1.4 Product principles

1. **Freedom with visibility.** Allow corrections; show who changed what.
2. **Recovery over punishment.** A mistaken tap never silently destroys a line.
3. **No false consent.** Reviewing an action is not a rules approval or a binding
   promise not to interrupt later.
4. **Groups explain; actions determine state.** Group boundaries do not restrict
   valid interruption points.
5. **No secret security claims.** A face-down card is a display convention.
6. **Branch, do not overwrite.** Preserve conflicting continuations automatically
   and make the divergence and active selection visible, without a routine
   conflict-resolution dialog.
7. **The board is understandable without the chat.** Actions carry sufficient
   structured details to explain state changes.
8. **Mobile-first interaction.** No required right-click, hover, keyboard, or
   precision-only gesture.
9. **Act, sync, undo.** A completed action applies locally and synchronizes
   automatically. Prefer direct manipulation and easy undo over routine dialogs,
   explicit batching, or repeated confirmation. Irreversible deletion and
   historical rollback with expanded impact still need explicit choices.
10. **Show facts, not inferred intent.** Present moves, pings, rotations, and value
    changes clearly enough that players usually need no narration. Do not turn
    those facts into an inferred spell/target/effect summary. Text remains optional.

## 1.5 Terminology

| Term | Meaning |
| --- | --- |
| Participant | One of the two people in a game; may edit either side. |
| Action | One user-meaningful, atomic game-state transition. |
| Group | A named contiguous sequence of actions presented together. |
| Area | A labeled board region with explicit generic layout/container settings, not a rules zone inferred from its name. |
| Ping | An action-linked visual pointer/marker. It changes no card or game mechanic. |
| Boundary | A state before the first action or after an atomic action. |
| Review cursor | The boundary a particular participant is currently viewing. |
| Branch | A named continuation from an existing boundary. |
| Active branch | The single authoritative continuation selected for the game. |
| Head | The last published action of a branch; the projected current board. |
| Draft | Recoverable local work not yet shared: pending actions, failed work, or incomplete input. Not a required pre-send phase. |
| Interrupt | Start a new continuation at a reviewed boundary and activate it. |
| Publish | Automatically synchronize a completed local action to shared history. A protocol operation, not a required button. |
| Conflict branch | A continuation automatically created at the intended base of an edit that collided with shared changes. |
| Archive/delete | Reversibly hide a branch, without erasing its contents. |
| Undo | Restore a prior logical result without destroying recoverable history. |

## 1.6 Success criteria

A pair can complete a game without external board-state tools. Either player
can catch up after hours away, stop before any later recorded action,
respond, and later undo that response without losing the original line. The
same workflow survives app termination, network interruption, and concurrent
activity without unreported lost work.
