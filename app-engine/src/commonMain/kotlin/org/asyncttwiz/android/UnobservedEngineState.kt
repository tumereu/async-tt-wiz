package org.asyncttwiz.android

/**
 * Marks what an [EngineGame] publishes as plain fields ([EngineGame.shown] and what is read off it): no Compose snapshot
 * observes them, so composable code that reads them (or keys a `LaunchedEffect`, `remember` or `derivedStateOf` on them)
 * is not composed or restarted when a new board is published once the table skips recompositions. That stalled games in
 * 0.3.1 (43efa2ba8) until something unrelated redrew the table.
 *
 * The UI's code (composables, `derivedStateOf`/`snapshotFlow` blocks, and the routes, callbacks and effects beside them on
 * the main thread) reads app-ui's `observedShown`, `shownVersion` and `historyLoading` instead, which read snapshot state.
 * Code no composition reads (the engine side, the server stream's handling) opts in where it reads the plain value, with
 * `@OptIn(UnobservedEngineState::class)`, which says so in the code. app-engine opts in as a whole compilation (it has no
 * composition), and so do androidApp's tests; app-ui's `UnobservedEngineReadsTest` keeps the UI's opt-ins (app-ui, webApp)
 * to a listed few files with no composable or snapshot-observing code.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "A plain field no Compose snapshot observes: the UI reads " +
    "observedShown / shownVersion (app-ui, snapshot state) instead, so composition follows every board published; only code " +
    "no composition reads opts in, with @OptIn(UnobservedEngineState::class) (app-ui: UnobservedEngineReadsTest's list).")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
annotation class UnobservedEngineState
