# Projection links — compiling integration API

Pin: `58fa38eb8bc6a26e80d7b918931fd5efe80919b6`. Custom integration unit;
no authoritative state transitions or application validators. Native production and
server sources compile in `public-api-compile.json`; affected tests follow in this run.

## Shared operation boundary

* `ProjectionEndpoint(occurrenceId)` accepts only a current combat occurrence or
  Stack effect, including a frozen effect. It is **not** a card/player endpoint.
* `PlayAction.LinkProjections(first, second)` and
  `PlayAction.RemoveProjectionLink(linkId)` are ordinary `GameAction.Play` inputs.
  Use the existing session, FIFO and `GameplayRules` prediction. Every constituent
  gets its own request ID. Creation uses `uuidV5(requestId, "projection-link/0")`.
  Undirected pairs are unique; repeated Link and absent explicit unlink are no-ops.
* Accepted tuples are in `GameplayTransition.combatStack.addedLinks/removedLinks`.
  `removedLinks` also captures same-transition incident cleanup, as before.
  No new relationship store or source-ID alias is introduced. Existing
  `BoardState.links` and `PlayAction.Link/RemoveLink` keep card/player semantics.

## Native mount (TableToolkit belongs to the other integration lane)

```kotlin
val links = TokenLinkController(
    current = { currentPredictedPlay },
    submit = { action -> sessionSingle(action) },
    submitInvocation = { actions -> sessionManyWithOneInvocation(actions) }
)
links.openOccurrences(ProjectionEndpoint(contextViewId), selectedViewIds.map(::ProjectionEndpoint))
links.beginLink()
// Consume before normal tap/player-menu routing; no source normalization:
val consumed = links.pick(ProjectionEndpoint(hitOccurrenceId))
links.links()
links.removeProjectionLink(capturedLinkId)
```

`submitInvocation` must allocate one invocation and one request per action and
enqueue all originals immediately, **without** a bulk transaction or acknowledgement
barrier. The callback Boolean is local enqueue status, not accepted success. It
does not roll back a previously queued constituent. For occurrence actions the old
single-only constructor refuses enqueue rather than silently breaking this seam.
Legacy card-only callers remain source-compatible; new mounts should supply the
invocation callback for both domains.

`projectionScope` is separate from source `scope`. `openOccurrences` captures the
exact selection (or unselected context alone), distinct by occurrence, never by
source. `pick(LinkEndpoint)` consumes incompatible hits while occurrence-picking;
the typed occurrence overload similarly consumes incompatible card-picking hits.
Missing captures are skipped without replacement; a vanished picked endpoint
keeps picking/error state and never follows a sibling with the same source.
Play identity change prevents retargeting. Source Clone/Remove cannot act on an
occurrence-only scope. Existing `LinkEndpointPicker` and `LinksListDialog` handle
either separate scope with the existing labels/controls and covered-information
conventions. `LinkActions.projectionEndpoints(play)` and
`connections(play, ProjectionLinkScope)` are available to custom table mounts.

## Required runtime/history-owner hook (not silently certified)

Exact replay runs through `GameplayHistory.replay` on the same reducer, comparing
all recorded cleanup/creation tuples. Cold tree materialization inherits this.
`GameplayHistory.inverseProjectionLinks(board, transition)` handles **only** a
pure projection-link change and refuses every compound family/changed endpoint.
The existing scalar and token inverses also refuse `combatStack` effects. Compound
token/movement/combat/Stack cleanup requires the runtime owner's complete composite
inverse or cold replay from a retained root, never a partial inverse.

For that owner the public hooks are:

* `projectionLinkChanges(transition): List<ProjectionLinkChange>`: exact from/to;
* `projectionLinkReferences(transition): Set<String>`: link IDs and exact occurrence
  endpoints, including incident cleanup, **not** source IDs;
* `projectionLinkWorkUnits(transition): Long`: added + removed tuple count.

The parallel history runtime owner must add the last value to its existing
`HistoryWork.effects` charge **before** replay and compose reference/inverse
families. Current `GameplayTree.replay` charges only fields/movement explicitly;
its serializer already meters all new payload bytes. This file is outside the
lease and is not rewritten here. No claim of completed I23 runtime accounting,
server selective-Undo mounting, independent verification or native acceptance.
