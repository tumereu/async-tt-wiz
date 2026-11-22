package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier

/**
 * Alice's table surface of [kit]'s game with the Stack animation wired as [EngineTableScreen] wires it (owner spec
 * 2026-11-14): the plays shown not landed yet off her board, the board drawn told to the animation, its settles running the
 * top entry's arrows, the landed entries appearing in place. [landscape]: her table turned. No board steps (the tests show
 * one board at a time), unless [changes] is given: then the board's steps play as [EngineTableScreen] plays them (its motion
 * queue, the cards of the special actions shown held as they were until their plays land, HIS-238) and the shown events
 * flash on [changes] (a special action's card once its play lands in it, HIS-237, HIS-031).
 */
@Composable
internal fun StackAnimatedTable(kit: EngineTableKit, modifier: Modifier, landscape: Boolean = false,
    /** Read here: bumped by a test, the table draws Alice's board published since. */
    version: Int = 0,
    /** The table's flashes and card motion (on the test clock, given by the test); null: no board steps. */
    changes: TableChanges? = null,
    runtime: (TableBoard) -> TableRuntimeHooks = { play ->
        TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() }, controlsConnected = true,
            waitingLine = kit.routes.waitingLine(play))
    }) {
    val animation = kit.routes.stackAnimation
    val live = version.let { kit.alice.play() }
    val shown = version.let { kit.alice.shown }
    val hidden = animation.hidden(kit.alice.shown?.moments.orEmpty())
    val motion = remember { EngineMotionQueue() }
    val heldCards = if (changes == null) emptySet() else animation.heldCards(shown?.moments.orEmpty())
    val unhidden = if (changes == null || shown == null) live
        else motion.board(shown.version, shown.transition, live, hold = animation.busy, plays = hidden, cards = heldCards)
    val play = animation.board(unhidden, hidden)
    val scope = rememberCoroutineScope()
    val applied = remember { longArrayOf(-1L) }
    val heldFlashes = remember { HashMap<String, List<TableChange>>() }
    SideEffect {
        if (changes != null) {
            motion.stopEntries = hidden
            motion.stopCards = heldCards
            animation.specialLanded = { show ->
                val card = show.card?.value
                if (card != null) changes.flash(heldFlashes.remove(card).orEmpty().filter { it.key.kind != TableChangeKind.UNLOCK } +
                    TableChange(TableChangeKey(card, TableChangeKind.SPECIAL), owner = show.by.value))
            }
            heldFlashes.keys.retainAll(heldCards)
            shown?.transition?.takeIf { it.serial != applied[0] }?.let { t ->
                applied[0] = t.serial
                val kept = if (t.jump) emptyList() else t.changes.filter { it.key.target in heldCards }
                for ((card, held) in kept.groupBy { it.key.target }) heldFlashes[card] = heldFlashes[card].orEmpty() + held
                changes.applyEngine(t.cursor, t.jump, t.changes - kept.toSet(), t.shuffled)
            }
        }
        animation.drawn(play, instant = false, unhidden = unhidden, hidden = hidden)
    }
    // With the board's steps, a board held as it was (HIS-238) is the same one as before: the test's new board composes the table
    // anew so it reads the device's shown state (the play shown) as the engine table does
    CompositionLocalProvider(LocalUnhiddenPlay provides unhidden, LocalLivePlay provides live) { androidx.compose.runtime.key(if (changes == null) 0 else version) {
        val hooks = runtime(play)
        TableSurface(kit.f.game, play, kit.f.a, landscape, modifier, connected = true, onIntent = kit.toolkit::handle,
            toolkit = kit.toolkit, runtime = hooks.copy(
                onSceneSettled = if (changes == null) animation.settleHook(scope, play, 1f, kit.routes.viewPause, { "the test" }, { 0 }, {})
                    else animation.settleHook(scope, play, 1f, kit.routes.viewPause, { "the test" }, motion::key, motion::settled),
                sceneVersion = if (changes == null) hooks.sceneVersion else motion.key(), changes = changes ?: hooks.changes,
                // As the engine table passes it: the device's shown state, a new one composes the table again (its board may
                // stay the same one while held, HIS-238)
                shownVersion = if (changes == null) hooks.shownVersion else shown?.version,
                landedEntries = animation.landed, sceneTimeShare = animation.resolutionShare(play, unhidden)))
    } }
}
