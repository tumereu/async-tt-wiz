# Narrow post-packaging Android correction

`stale-revision-diagnostic.json` reproduces a real old-incarnation revision crossing
the cache observer before the replica's accepted fence. It could invalidate the
newer cache and initiate an obsolete recovery. Original failing XML is retained.

GameSession now checks accepted revisions **before** observePlay/cache invalidation.
Duplicate accepted deliveries may correlate exact originals but do not rewrite
storage or start an obsolete recovery. The new stale/duplicate regression plus
original-control recovery and head-before-result chat cases pass in
`stale-revision-fix.json`; instrumentation Kotlin also compiles. This author does
not independently verify the repair.

Only `GameSession.kt` and its production-session test changed after source pin
`27f3402`. The first configured APK pair is retained as superseded artifact evidence;
the final APK pair is regenerated from the next saved Android source. Server,
protocol, core and migration inputs are unchanged, so the already-built immutable
server image and its truthful `27f3402` source/distribution/schema manifest are
reused without an unnecessary second image build. Final artifact evidence identifies
the two source commits separately. A fresh coherent verifier must include this exact
Android delta, even if it already inspected the early kernel or `27f3402`.
