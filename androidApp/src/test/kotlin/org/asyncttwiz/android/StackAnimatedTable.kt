package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier

/**
 * Alice's table surface of [kit]'s game with the Stack animation wired as [EngineTableScreen] wires it (owner spec
 * 2026-11-14): the plays shown not landed yet off her board, the board drawn told to the animation, its settles running the
 * top entry's arrows, the landed entries appearing in place. [landscape]: her table turned. No board steps (the tests show
 * one board at a time).
 */
@Composable
internal fun StackAnimatedTable(kit: EngineTableKit, modifier: Modifier, landscape: Boolean = false,
    /** Read here: bumped by a test, the table draws Alice's board published since. */
    version: Int = 0,
    runtime: (TableBoard) -> TableRuntimeHooks = { play ->
        TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true,
            waitingLine = kit.routes.waitingLine(play))
    }) {
    val animation = kit.routes.stackAnimation
    val unhidden = version.let { kit.alice.play() }
    val play = animation.board(unhidden, kit.alice.shown?.moments.orEmpty())
    val scope = rememberCoroutineScope()
    SideEffect { animation.drawn(play, instant = false) }
    CompositionLocalProvider(LocalUnhiddenPlay provides unhidden) {
        TableSurface(kit.f.game, play, kit.f.a, landscape, modifier, connected = true, onIntent = kit.toolkit::handle,
            toolkit = kit.toolkit, runtime = runtime(play).copy(
                onSceneSettled = animation.settleHook(scope, play, 1f, kit.routes.viewPause, { "the test" }, { 0 }, {}),
                landedEntries = animation.landed, sceneTimeShare = animation.resolutionShare(play)))
    }
}
