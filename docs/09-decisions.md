# 9. Decisions and open questions

## 9.1 Confirmed owner decisions

| ID | Decision | Consequence |
| --- | --- | --- |
| D-01 | Native Android asynchronous tabletop inspired by Cockatrice; MTG is the initial manual-play use case. | Async/touch are primary; D-19 clarifies the engine is generic. |
| D-02 | Meaningful action groups, individually reviewable/interruption-capable. | Grouping cannot erase intermediate action boundaries. |
| D-03 | Retain a tree but select only one active branch. | Separate stored alternatives from authoritative selection. |
| D-04 | An interruption becomes the active branch without agreement voting. | Atomic branch creation/activation, with visible activity. |
| D-05 | Delete an interruption and return; full undo for mistakes. | Reversible archival, preserved old continuations, broad undo coverage. |
| D-06 | Anyone can manipulate any tabletop state. | Symmetric participant editing rights. |
| D-07 | Everything is accessible, including hidden information. | Hidden zones are visual conventions, not participant access controls. |
| D-08 | Two players first. | No first-release multiplayer coordination/UI. |
| D-09 | Offline read and queued actions. | Durable local work, automatic outbound queue, and reconnect synchronization. |
| D-10 | Full specification before implementation, then spec-driven implementation loops. | Readiness gate and explicit authorization are required. |
| D-11 | Instant synchronization rather than explicit Send. | Completed game actions automatically queue/sync; groups are not publication gates. |
| D-12 | Minimize taps and other user actions. | Direct controls, no routine confirmation/grouping chores, easy undo, explicit journey interaction budgets. |
| D-13 | Branch on conflicts. | Concurrent valid game edits automatically fork and synchronize; no blocking reconciliation dialog or automatic content merge. |
| D-14 | Drag to move with grid/slot snapping; allow diagonal-peek card piles. | Direct board manipulation and explicit pile/zone semantics are required. |
| D-15 | Tap a card to tap it; double tap for all actions; hold for large art/text. | Gesture arbitration cannot emit unintended taps; accessible alternatives remain available. |
| D-16 | Flow should be clear mostly without writing explanations; text/messages still supported. | Present literal operations/pings clearly; retain optional notes, replies, and chat. Do not infer game meaning. |
| D-17 | Pings belong to a specific action. | Source context is explicit and retained in grouped history, not just a fleeting board animation. |
| D-18 | Distinct board areas and special library/graveyard/exile piles. | Define dedicated zone controls and distinguish those from ordinary visual piles. |
| D-19 | Generic board-game engine: record/present moves, pings, and card edits as-is; users infer meaning. | No inferred cast/target/activation/resolution/cost/turn model or rule-aware entities. Area labels and card text are data. D-55/D-56 later add explicit Graveyard/Exile/Library move/reset edits, superseding blanket no-reset behavior for those fields/destinations. |
| D-20 | Review small logical groups of tabletop capabilities step by step, deciding need and touch controls while updating the design docs. | Use `12-touch-action-review.md` to track review; confirm capabilities/controls before returning to detailed grouping/playback. Cockatrice parity is not assumed. |
| D-21 | Dragging moves only one card unless multiple cards have been selected. | A visual stack does not implicitly make all its members move; D-28 specifies dragging a card outside the selected set. |
| D-22 | Dropping onto library, graveyard, or exile inserts on top. | Configure generic ordered-pile drop defaults; no top/bottom prompt for a drop, and no game-rule meaning inferred. |
| D-23 | Dropping onto another ordinary card puts the moved card onto its visual combined stack. | Form/join a visual pile, retaining separate identities, unrelated fields, and links; not a rules attachment/merge. |
| D-24 | Context Move must offer at least top/bottom of library, graveyard, and exile. | Include those six destination placements in the double-tap action sheet; exact picker layout and additional destinations remain open. |
| D-25 | A new selection rectangle replaces the existing selection; individual additions/removals use selection-edit mode. | Rectangle completion replaces rather than unions/toggles the selected set. Selection remains local presentation, not a board edit. |
| D-26 | Any overlap with the selection rectangle selects a card; full enclosure is not required. | Partially intersected cards qualify; D-27 specifies visible-portion matching for stacks. Exact geometry remains open. |
| D-27 | Rectangle selection of a visual stack includes only cards whose visible portions overlap the rectangle. | Evaluate cards separately; no implicit whole-stack selection or selection of fully obscured cards. Visible covered backs qualify without revealing identity. |
| D-28 | Dragging an unselected card clears the existing selection and moves only that card. | Never implicitly add it to or move the previous selected set. Selection clearing is local presentation; D-34 restores the pre-drag selection on cancellation/invalid drop. |
| D-29 | After moving a selected group, keep it selected until explicitly cleared or replaced. | Successful movement does not clear selection. D-31 fixes explicit mode exit; D-34 preserves mode on/off after moves and restores cancelled drags. |
| D-30 | Tapping empty table space clears selection without changing whether multi-select/selection-edit mode is active. | Empty-space tap clears; empty-space drag draws a replacement rectangle. Clearing is local presentation, not a game action or mode-exit step. |
| D-31 | A visible Select toggle enters/exits multi-select/selection-edit mode; switching it off keeps the selection and restores normal tap/untap card taps. | Mode exit and selection clearing are distinct local UI operations. No Send or board action is created by toggling the mode. |
| D-32 | Initially approved individual-card tap scope and explicit Tap selected / Untap selected. Single-tap scope was subsequently corrected by D-35. | Retain explicit uniform-setting commands in selection-edit mode; D-35 is authoritative for normal card taps and rejects the proposed outside-mode command availability. |
| D-33 | Batch straightforward, likely-yes design questions rather than asking for each separately. | Present small related sets with numbered defaults; the owner can approve together or name exceptions. Significant tradeoffs still deserve focused review. |
| D-34 | Grouped defaults 1–4 accepted with the owner's exception to item 5: pinch zoom/two-finger pan; Hold/Actions preserved in selection-edit mode without selection edits; moves preserve mode on/off; cancelled/invalid drags restore placement and pre-drag selection. | Navigation/inspection stay local. Gesture timing and second-finger arbitration remain open. Item 5 is replaced by D-35, not approved. |
| D-35 | Outside selection-edit mode, tapping a selected card retains selection and taps/untaps all selected cards; tapping an unselected card clears selection and taps/untaps only it. Tapping rotates 90 degrees clockwise or back. | Corrects D-32's single-card-only scope. Selection-edit taps still edit membership. D-36 fixes mixed-orientation behavior. |
| D-36 | Selected-set tapping follows the touched card's pre-tap orientation: untapped makes all selected cards tapped; tapped makes all selected cards untapped. | Set a uniform result rather than independently toggling members. Preserve selection and unrelated card fields. |
| D-37 | Turning face down/up and switching printed face are context actions, separate from sideways tapping. Face-down/up supports one card or an explicit selection. | Use clear labels instead of Cover/Uncover; preserve unrelated fields/selection. Switching printed face does not implicitly uncover a card or infer game meaning. |
| D-38 | Inspect automatically shows a face-down card's face; for multi-faced cards show both printed faces/art/text without extra uncover/switch actions. | Existing hold inspector (called hover-inspect in review), local presentation in either mode; no shared face/cover, orientation, placement, or selection changes. Inspection logging remains Q-06. |
| D-39 | Ordinary visual stacks contain at most three actual cards. Library/Graveyard/Exile and multi-selection size are not capped by this rule. Overflow drops show an invalid preview and restore the dragged cards/selection unchanged. | No hidden extra members, automatic splitting, displacement, or partial selected-set moves. Invalid drops create no board action. D-40 subsequently settles compact stack controls. |
| D-40 | No separate ordinary-stack browser or Select stack action; use rectangle selection. Reordering and splitting use ordinary card drags. | Drag an individual member onto its stack to bring it to the top, or to empty space to split it out. Preserve other members' order/unrelated state. Special-container browsers remain a separate decision. |
| D-41 | Tap a special pile to view contents and expose pile context actions together. | No separate double-tap/menu-opening step for pile actions; distinguish pile controls from individual cards. Exact hit regions and combined panel layout remain open. |
| D-42 | Viewing the full library must not show its underlying order. | Use order-independent presentation without changing stored library order; no order indices or top/bottom indicators in that full view. D-45 separately specifies ordered top-N displays. |
| D-43 | Library actions include View top N and Reveal top N; revealed cards have their own context actions and hold inspection. | Support existing top/bottom movement from shown cards. D-46 subsequently confirms Draw N and shared reference displays. |
| D-44 | View is shared/logged too, not local-only. View presents faces to its initiating participant and backs to the other; Reveal presents faces to both. | Record/synchronize both operations, including explicit library contents viewing. Face/back presentation is not opponent access control; ordinary hold-inspection logging remains a narrower open question. |
| D-45 | Viewing/revealing top N shows the cards in actual source order. | Preserve top-to-bottom sequence for faces or backs; full-library viewing remains orderless under D-42. |
| D-46 | View/Reveal trays reference original cards without moving/copying them; Draw N moves top N to configured Hand; shared displays persist until explicitly dismissed. | Context actions edit originals once. Draw N is an explicit preset shortcut, not inferred rules. No timeout/app-close expiry; D-47 fixes source-move/dismissal behavior. Detailed schema, inputs/ordering/validation, and history/undo remain open. |
| D-47 | Displays retain original shown IDs/source order without auto-refilling; moved cards remain with updated locations. Either participant can dismiss; dismissal is logged and closes only the active display. | Preserve cards, completed moves, and historical display records. No automatic reveal of new source top cards. Exact deletion/removal, branching, dismissal UI, and undo behavior remain open. |
| D-48 | Include quick Draw one, View bottom N / Reveal bottom N, and Move top N / Move bottom N to a chosen destination. Shuffle all is the only library shuffle shortcut; no top/bottom shuffle shortcuts. | Reuse shared reference-display and literal movement models. No automatic shuffle after viewing. D-49 fixes N-input/order defaults; bulk boundaries remain open. |
| D-49 | Bottom-N displays retain top-to-bottom source order with the bottommost card marked. N-picker starts at one with +/- and numeric entry; Draw one skips it. Clamp N to available count. Moves from one ordered source preserve relative source order at either destination end. | Replaces the proposed rejection of oversized N. Quantity clamping does not allow skipping explicitly selected IDs. Existing destination members retain relative order. Empty-source behavior, count feedback, stale/offline validation, cross-source order, and bulk boundaries remain open. |
| D-50 | Drag a special pile's top-card surface to move its top card; tapping opens contents/actions. Contents browsers use card-type category columns like Cockatrice and support name search/filtering and hold inspection. | Replaces the proposed source-ordered Graveyard/Exile browser list. Grouping/filtering never reorder piles; full Library hides source order, while explicit top/bottom-N displays stay ordered. D-51 supplies initial column defaults; category mapping, covered opponent grouping, hit regions, and per-keystroke logging remain open. |
| D-51 | Initial-version browser defaults: one main-type column per card, Other for unknown types, alphabetical name order within columns, horizontal column scrolling and vertical scrolling within each column on narrow screens. | Approved as a starting point that may need adjustment, not a permanent layout constraint. Revisit during wireframe review/usability testing. Exact categories/multi-type precedence, duplicate-name ties that do not expose library order, accessibility, gestures, and covered opponent layout remain open. |
| D-52 | Cards may have arbitrary bottom-right corner text (e.g. P/T or loyalty), with quick context inc/dec/set. Separate colored counters carry their own arbitrary text and can be attached to cards or players; numeric counter text supports quick inc/dec/set. Offer a small selectable color palette. | Replaces the unapproved G-07 tracker-control batch. No automatic calculations, effects, or coupling between counters and corner text. D-58 fixes palette, uniqueness, and zero/empty behavior; exact slash-pair actions, numeric recognition/limits, placement, and controls remain open. |
| D-53 | Corner content is one plain text field; offer specific quick actions when its current value matches a pattern. Actions rewrite the same string, not separate P/T, loyalty, or typed numeric fields. | Counter numeric conveniences follow the same text-first principle. No game-specific type or separately authoritative parsed value. Exact patterns, format preservation, and action sets remain open; the proposed left/right/both +/-1 controls are not approved by this clarification alone. |
| D-54 | Counter increment/decrement context actions keep the menu open. Some other actions dismiss it, including successful Move to Graveyard. | Repeated edits queue/sync independently of menu dismissal; no Send gate. Exact lifetime of Set, corner-text shortcuts, and other actions remains open. |
| D-55 | Cards entering configured Graveyard or Exile lose their counters and have corner text reset. D-56 clarifies Library as the third destination and the reset value as the database default. | Supersedes blanket move-preservation for these fields/destinations. Represent declared generic move/reset edits; preserve stable identity and unrelated fields. Remaining defaults, same-container behavior, and detailed undo composition remain Q-29. |
| D-56 | The third reset destination is Library. The card database supplies default corner text; for creatures it is printed P/T. Reset restores that default string. | Apply D-55 counter removal and default-text restoration on entry to Graveyard/Exile/Library. Corner state remains a plain editable string, not typed P/T. Exact defaults for other types/faces/custom/missing data and historical version selection remain open. |
| D-57 | Automatically continue design updates with the next contextualized question/batch; commit and push occasionally at natural checkpoints. | Do not require a separate “next?” prompt or commit every tiny edit. Keep approved decisions distinct from proposals. Checkpoints do not authorize implementation or imply completion of the specification. |
| D-58 | Counter palette: red, blue, green, yellow, purple, gray. At most one counter of each color per target. Numeric zero stays visible; empty text removes the counter. | Rejects multiple same-color counters on one card/player. Uniqueness is per target. Completed empty edits remove; unfinished input is not publication. D-59 fixes trimming and D-60 creation/color behavior; styling and any separate Remove shortcut remain open. |
| D-59 | Always trim leading/trailing whitespace from user-entered text on completed input. Preserve internal whitespace; apply empty-value checks and pattern matching to the trimmed text. | Global, not just counter text: includes labels/names, corner text, notes, messages. Whitespace-only counter input becomes empty and removes it; ` 0 ` becomes visible `0`. Other fields retain their own empty-value rules. Do not publish unfinished input or rewrite existing history/catalog data. |
| D-60 | Choosing an unused counter color creates text `1`; choosing an existing color opens its editing actions without changing its text. No recolor action in the initial release. | Confirms creation/occupied-color proposals while rejecting recoloring as unnecessary. No duplicate, overwrite, merge, or automatic increment from choosing an existing color. Exact menu layout, Add/Set lifetime, and player entry point remain under review. |

Resolved question Q-02: **publish live as actions happen**, by D-11. Connectivity,
pending state, and divergence remain visible; “instant” does not claim zero
latency or guaranteed Android background execution.

Resolved Q-01: the engine is **generic and rules-agnostic**, not just a manual MTG
client that understands declarations but declines to execute their effects.
D-19 rejects the earlier semantic cast/target proposal explicitly.

## 9.2 Proposed defaults to review

| ID | Proposal | Primary contract |
| --- | --- | --- |
| P-03 | Explicit generic atomic operations within editable interaction-based groups; no inferred game-mechanic grouping. | `03-history-and-undo.md` |
| P-04 | Full tree retained; branch delete is reversible archive. | `03-history-and-undo.md` |
| P-06 | Redo repeats stored random results; new randomness is a new action. | `03-history-and-undo.md` |
| P-07 | Private two-player membership around fully accessible in-game information. | `06-sync-and-storage.md` |
| P-08 | Native Kotlin/Compose client, transactional local store, shared service. | `07-architecture-and-operations.md` |
| P-09 | Standard zones appear covered by default despite full access. | `04-game-actions.md` |
| P-10 | Historical undo rewinds a continuation; correcting current state is separate. | `03-history-and-undo.md` |
| P-11 | New conflict branches follow the interruption activation rule: active on creation, serialized by service commit order; descendants do not repeatedly reactivate. | `03-history-and-undo.md`, `06-sync-and-storage.md` |
| P-15 | Generic ping-handle drag associated with the current action; move + pings form one literal expandable group, with no mechanical interpretation. | `11-visual-action-language.md` |
| P-16 | Core selection mapping is now confirmed through D-25–D-36: direct rectangles, two-finger pan/pinch zoom, separate membership-edit mode, and selection-aware card drag/tap. | `10-board-and-controls.md`, G-02/G-03 in `12-touch-action-review.md`; exact geometry, gesture arbitration, cross-area selection, and bulk boundaries remain open. |

Superseded proposals:

- P-18's reference-display model, Draw N shortcut, and explicit-dismissal lifetime
  are confirmed by D-46, with fixed membership, moved-card entries, and shared
  logged dismissal confirmed by D-47. Exact lifecycle/history contracts remain
  open in Q-28.
- P-13's separate ordinary-pile handle proposal is withdrawn in favor of ordinary
  selection/drag controls. D-40 rejects the browser and Select stack action;
  no special whole-stack control is needed for the chosen path.
- P-17 (three-card stack cap and invalid-drop overflow policy) is now confirmed
  by D-39. Browser, badge, and whole-pile handle choices were not approved by it.
- P-02 (explicit Send from autosaved drafts) was rejected in favor of D-11.
  Pending local work remains durable but is not a required pre-send stage.
- P-05 (blocking stale-edit conflict with no automatic branch activation) was
  superseded by D-13. Valid conflicting actions synchronize on retained branches;
  malformed actions and expanded-impact historical control commands still need
  explicit error/recovery behavior.
- P-01's broad “no rules engine” boundary is now confirmed and strengthened by
  D-19: no semantic mechanics model or inferred action explanations either.
- P-12/P-14 (semantic cast declarations, typed rules targets, target handles)
  were rejected by D-19. Keep the labeled Cast area and associated generic pings,
  not cast/target entities, completion states, or inferred summaries. Automatic
  rule-inferred zone resets/incarnation changes from the earlier draft are
  removed. D-55/D-56 later approve the Graveyard/Exile/Library move/reset exception,
  without introducing rules incarnations or other cleanup.

## 9.3 Release-blocking questions

All items here must be resolved before implementation, but they need not all be
asked in one conversation. Start with the first design round.

### Next design round: interaction semantics

| ID | Question | Why it matters |
| --- | --- | --- |
| Q-03 | Which generic interaction/context heuristics form groups automatically, including associated pings and optional manual adjustments? | No game-mechanic inference, delayed sync, or mandatory grouping taps. |
| Q-04 | Which explicit generic bulk operations are atomic versus sequential? | Preserve useful interruption boundaries without a game-mechanics model. |
| Q-05 | Does historical undo rewind dependent play as proposed, with an alternative correction-now tool? | Avoids ambiguous “undo everything” semantics. |
| Q-06 | Should ordinary hold/hover inspection also add shared activity? | D-44 already requires explicit View/Reveal to be logged/shared. The remaining question concerns incidental inspection, not whether View is private. |
| Q-07 | Can an empty interruption branch just rewind/pause the game? | Defines root/fork/head and response-request flows. |

### Product and deployment

| ID | Question | Why it matters |
| --- | --- | --- |
| Q-08 | Hosted service, self-hosted service, or both? Who operates it? | Determines onboarding, cost, backup, and deployment. |
| Q-09 | Accounts or guest/invite identities? Recovery and multiple devices? | Determines authorization and personal-state synchronization. |
| Q-10 | Which generic layout/import presets and fresh-table/rematch conveniences are first-release obligations? | Presets may name/configure areas/values but cannot introduce rule semantics. |
| Q-11 | Which generic pieces/links/faces/trackers are needed to represent complex manual-play scenarios? | Verify MTG coverage without game-specific entities or inferred actions. |
| Q-12 | Required Android versions, device sizes, languages, distribution method? | Sets UI and platform contracts. |
| Q-13 | Notification provider/preferences, read-receipt visibility, response-request behavior? | Defines async coordination without false rules consent. |
| Q-14 | Card catalog/images and deck import/export formats? | Requires verified provider terms and exact data contracts. |

### Detailed correctness and operations

| ID | Question | Why it matters |
| --- | --- | --- |
| Q-15 | Remaining container insertion, placement, copy/removal, and generic unchanged-field contracts, including multi-card order? | D-22/D-24 fix top drops/context destinations; D-49 fixes single-source order. D-55/D-56 add Graveyard/Exile/Library resets; unrelated state and stable identity remain preserved. |
| Q-16 | Exact branch/head control schema, undo/redo scopes, metadata revisions, and API contracts? | Must make every operation implementable and concurrency-safe. |
| Q-17 | Randomness source/algorithm, online/offline policy, and audit detail? | Ensures replay/redo while acknowledging trust-based fairness. |
| Q-18 | Default offline download scope, cache/storage limits, and cross-device draft recovery? | Prevents missing histories and silent loss. |
| Q-19 | Game retention, hard deletion, account erasure, backups, export/import, quotas? | Resolves recoverability versus privacy/cost constraints. |
| Q-20 | Quantified latency/availability/load targets and backup recovery objectives? | Makes non-functional requirements testable. |
| Q-21 | Protocol/client/schema version compatibility and migration support window? | Protects retained games and long-lived offline clients. |
| Q-22 | Threat model, encryption choices, abuse/support procedures, and telemetry policy? | Distinguishes cooperative gameplay from insecure infrastructure. |
| Q-23 | Approve exact automatic-fork activation/continuation rules, including pending descendants and further editing of an inactive line. | Must preserve both lines without branch-selection ping-pong or routine prompts. |
| Q-24 | Exact board layout, gesture timing, snap/grid/drop rules, diagonal fan, card/pile hit targets, selection gestures, and remaining special-pile controls? | D-21–D-24 settle drag scope, visual stacking, special-pile top drops, and minimum context destinations; detailed accessible controls remain open. |
| Q-25 | Exact labeled Cast-area layout, generic move/ping grouping, action association switching, and later linked additions? | One literal log unit with instant sync and retained boundaries, not a semantic cast node. |
| Q-26 | Which generic ping styles/labels, lifecycle, presence privacy/expiry, text/reply flows, and schemas? | Durable visual communication with correct action references and no mechanical effects. |
| Q-27 | Generic engine versus first-release authoring UI scope: configurable presets only, or also a user-facing board/layout editor? | Generic primitives are confirmed; a full editor and non-card piece authoring were not automatically added to scope. |
| Q-28 | Exact View/Reveal records, deletion/removal handling, dismissal controls/history/undo/branching, N-card empty-source/count feedback/stale validation, and bulk boundaries? | D-41–D-49 establish shared logging, faces/backs, ordered subsets, fixed references with location updates, draw/move shortcuts, logged dismissal, N-input defaults, quantity clamping, and single-source move order. Displays do not duplicate or move cards merely to show them. |
| Q-29 | Exact database corner defaults for other card types/faces/custom/missing data, historical version selection, same-container reorder behavior, and move/reset undo composition? | D-55/D-56 approve counter removal and database-default corner restoration on Graveyard/Exile/Library entry; creature defaults are printed P/T strings. The third destination and reset source are resolved; do not introduce a typed P/T field. |

## 9.4 Known specification gaps

This draft is intentionally not implementation-ready. Missing artifacts include:

- Per-action contracts and generic field-preservation/insertion tables.
- Worked complete-game examples, including unusual MTG card structures.
- Exact grouping heuristics and all branch/undo edge-case state machines.
- Annotated screen wireframes and full interaction/error copy.
- Complete board/pile/gesture measurements and literal move/ping flow contracts.
- Persistent schema, formal API, payload/error examples, and migration rules.
- Approved technology/source/hosting choices and verified external terms.
- Quantified operational/performance requirements and full traceability.

Close these explicitly. Do not replace them with “use sensible defaults.”
