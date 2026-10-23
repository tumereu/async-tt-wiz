# I15a integration pins

Base: `a4b444e192776f5d165fb9188fc6e526f697620e`. This is a rendering adapter,
not another authoritative card/placement model. No I13/I14 acceptance is implied.

- `TableScreen` / `TableSurface` accept `onIntent: (TableIntent) -> Unit` and
  `placements: Map<String, TablePlacement>`. They render the supplied `PlayState`.
  Until I16 supplies the connected request adapter, no shared intent has a fake
  local handler. Camera, scrolling, selection, Actions scope and inspection are UI.
- I13's approved `PlayCard.placement` is absent from this pinned compilation base.
  I16 adapts it: `BoardPosition.x/y / 1024f` → `column/row` (one board unit is
  one 84dp envelope at zoom 1), retained `pileId` → `pileId`; top-first container
  members are assigned descending `pileIndex` so the top draws last. The fallback
  is canonical container order only, never independently persisted placement.
- The base card face is 48×68dp; complete battlefield envelope is 84×84dp at
  common zoom .65–2. This contains the rotated face and two 4dp diagonal offsets.
  Dock/Hand anchors stay fixed, with common-size card drawings and personal overflow.
  Landscape sidebar overflow supports two-finger vertical navigation and explicit
  accessible scrolling; no area-specific shrinking. Screen coordinates are dp.
- `TableIntent.Actions(itemId, scope)` captures the context and ordered real IDs;
  a pile control has no individual-card Actions scope. `Inspect(cardId)` is local
  permitted-side inspection; explicit logged View/Reveal is NOT implemented here.
  `Rotate(scope, tapped)` is uniform, not per-member inversion. `Move` includes
  press-time `sources` (container/placement/Library-top identity), captured IDs and
  a semantic Container / CardPile / Grid destination. Grid column/row map to
  `BoardPosition(column*1024,row*1024)`. I16 maps special destinations to Library
  TOP, Graveyard/Exile UNORDERED and the required Hand/Stack insertion semantics;
  it must not turn unordered display sequence into an ordered move request.
- `PlayerDelta` is one relative release intent. I16 maps life to `AdjustLife`;
  I17 supplies counter actions. No movement/frame/hold opens shared requests.
  `Selection`, `Camera`, `Scroll` are personal. Ping mode consumes its taps and
  emits an intent only when connected/wired; I19/I22 supply actual ping requests.
- Only the scene root handles card/dock/hand pointers. Semantic children have
  no pointer detectors. Captured IDs survive reflow; removed IDs cancel rather
  than substituting the item underneath. Draw/hit/semantic fragments use the same
  clipped scene, including 180ms animated position interpolation and retargeting.
  Personal navigation snaps rather than waiting on a shared animation.

## Evidence and retained obligations

`final-affected.json` passes 28 host tests plus normal debug APK and instrumentation
APK builds. `passing-junit.json` retains exact XML bytes. Earlier failed compile,
tooling and assertion diagnostics remain alongside it; the WSL Gradle wrapper fixes
client/daemon IPv4 connectivity without changing global settings. Host checks test
production geometry/coordinator logic; they are NOT native touch/render acceptance.

I15b retains the entire original I15 native obligation: exercise the normal app
after I16 wiring, real cards/piles and both orientations/zoom/large text, native
Actions/double/hold/drag/select/cancel and explicit alternatives, overflow ends,
remote reflow/lost targets and current animated bounds. Run/extend `TableCanvasTest`
and the normal two-player journey with the complete device/resource wrapper; keep
actual output/captures and repair discrepancies. The instrumentation source here
is compiled, NOT executed. No device was provisioned, reset, installed or borrowed.
I16 remains responsible for connected shared requests/prediction/recovery and real
menu actions. I17/I18/I21 extend values, contents, reveals and projections through
this same scene/coordinator; they must retain common geometry/owner/disclosure.
I32/I34 and independent B4/B8 reviews retain full accessibility, interface/mock,
all inherited cross-capability assertions and final qualification—not this host run.
