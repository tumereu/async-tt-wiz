# I20 component handoff

## Delivered I20a (proposal, not supervisor acceptance)

- The pinned worker branch started clean at `7641ac350cbadd277652be668680ba3049817393`.
  Context supplies the implement/I20 lease and exact high-model session metadata.
  The separate supervisor lease journal is outside this session's directory grant;
  it was not modified. `handoff-inputs.json` checks branch, pinned ancestry, worktree
  and byte-identical authoritative state. Only isolated host resources were used.
- `GameplayRules` remains the sole processor. Twelve fixed positions, relative
  Next/End and direct selection emit relevant field tuples. No draw, untap, combat
  closure, priority, extra first-strike step or rules inference occurs on tracking
  changes. Direct selection preserves player/number without adding replay dependencies;
  next-turn changes record both. Main phases have no additional step.
- Die/coin original input is sampled once at the connected native invocation via
  `RandomV1.sample` over SecureRandom words. Shared processing validates bounds and
  literal input, recording it without server/replay rerolls. Equal fresh results
  remain distinct records. Die sides are decimal-string operands.
- Life keeps `AdjustLife`. No generic scalar action exists in this early I13 base;
  explicit player-counter actions extend the same processor. Both reuse exact
  signed-decimal arithmetic and pinned Unicode White_Space trimming, without a
  fixed-width gameplay clamp. Existing text-size limits remain in force.
- Real reusable Compose components: phase indicator/picker, Table actions and full
  constituent menus, life/counter delta affordance, and a three-position pannable
  rail plus reserved tap-Next/hold-End square. End fill is supplied explicitly as
  ending-player color. Native semantic actions and keyboard alternatives remain.
  Assigned holds outrank name-only tooltips; field preview sends no edit until
  nonzero release. Targets retain original game/play/player/color identities.
- `TableActionRoutes` requires typed Library, combat, token, ending, Stack and player
  component callbacks, with no default/no-op handlers. Full paths include standalone
  Untap/Draw/Next/End, Library/mulligans, declaration, independent Tap/Untap, combat
  creation/closure and captured Stack actions. I21 still owns combat/Stack execution;
  this unit does not guess those reducers or introduce a second queue.

## Executed evidence (not assigned-ID coverage)

`components-04.json` and exact XML retained in `junit.json`: 25 tests, zero failures,
errors or skips (2 existing gameplay, 9 table reducer, 6 quick/gesture, 8 controller).
The same foreground command assembled the debug APK. Assertions cover:

- Every next-step edge, all 144 picker source/destination pairs, End from every
  position/either player, unrelated-state preservation, live arithmetic, literal
  replay mismatch, incarnation/access checks, random bounds/codec/fidelity,
  tracking/random grouping extensions and arbitrary-precision field arithmetic.
- Every catalog position, turn-one-only mulligans/defaults, declaration selection
  gating, explicit Stack kinds, phase-before-Stack ordering, no automatic target,
  overflowing contextual inventory, full-route inventory, pending-result routing,
  pure tap/hold/tooltip/cancel arbitration and reversible delta preview.
- Typed callback delivery/captured identities, region rather than original ownership,
  turn-change non-retargeting, top-card/container capture, empty/rejected constituent
  advancement, once-only sampling, no offline/new-incarnation invocation, and one
  relative release following a concurrent life update.

`components-01.json` retains the known IPv4 Gradle daemon failure.
`components-02.json` passed shared tests/compiled controls but failed four test list
type-inference sites. IPv4 invocation and explicit list types repaired those.
`native-03.json` is intermediate green output, superseded by affected changes and
`components-04.json`, not additional current coverage.

## Retained I20b and final owners

This base's normal `TableScreen`/`GameSession` do not yet provide I14/I16's gameplay
sender/prediction surface, and I13 movement remains an early stub. These components
are compiled, **not wired into the production table** in this lease. I20b depends
on I20a/I13/I14/I16/I17/I18/I19/I21 and preserves every original I20 check. It must
wire required routes to real corresponding components, union overlapping action/
field variants with other workers and exercise actual server acceptance, individual
partial outcomes, original-request retry/reconnect/restart and rebase. No fake
callback or replacement queue is acceptable. Draw's reducer is not certified here.

I20b owns resource-wrapped native pointer/hold/drag/cancel, accessibility, overflow
pan, ending-color and real routing checks in both orientations, including connected,
read-only review and explicitly armed continuation contexts. Host arbitration and
APK compilation are not a device journey or visual acceptance.

I22/I23/I24/I26 retain full grouping, revealing-random selective-Undo exclusion,
branched history and complete recovery assertions; this unit only extends the early
tuple/replay/grouping foundation. I27 retains actual result suggestion/acceptance/
lobby-return implementation and its typed Table-action route integration. B5 group
review, I34/I36 final full qualification and B8's full interface/mock comparison
remain required. No schema, device, full-suite, UI-design documentation, authoritative
state or release-gate change was made here.
