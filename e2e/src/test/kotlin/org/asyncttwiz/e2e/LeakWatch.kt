package org.asyncttwiz.e2e

import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.InformationRule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The interim simulation's leak oracle (I-D56 §5) on a real phone: on every event the player's device shows or sends,
 * while their line is tentative (an event of the main line past the other player's local head, or one of their own
 * pending ones, is an assumption of theirs), nothing shown may reveal a card they couldn't see before. Installed on the
 * device's session (engine thread), again whenever the game is loaded again.
 */
internal class LeakWatch(private val player: E2ePlayer) {
    val leaks = CopyOnWriteArrayList<String>()
    /** Every event the device showed (applied to the board it shows: the server's, played back), in order. */
    val shown = CopyOnWriteArrayList<GameEvent>()
    @Volatile var checked = 0
        private set
    private var watched: Any? = null

    /** Watches the player's current device session (a no-op when already watched). */
    fun install() {
        if (player.game?.shown == null) return
        player.engine {
            val session = device.session
            if (session !== watched) {
                watched = session
                session.beforeShow = { before, event -> this@LeakWatch.shown += event; check(this, before, event) }
                session.onSent = { before, event -> check(this, before, event) }
            }
        }
    }

    private fun check(game: EngineGame, before: GameState, event: GameEvent) {
        checked++
        val me = PlayerId(game.me)
        val device = game.device
        val other = device.localHeads.entries.firstOrNull { it.key != me }?.value ?: return
        val line = device.session.events
        val after = line.indexOfFirst { it.id == other }.let { if (it < 0) line else line.drop(it + 1) }
        val tentative = (after + device.session.pending).any { it.assumed?.assumedBy == me && it.id != event.id } ||
            event.assumed?.assumedBy == me
        if (!tentative) return
        val shown = InformationRule.revealing(before, event.engine?.changes.orEmpty(), me)
        if (shown.isNotEmpty()) leaks += "${player.name} shown $shown by ${event.id}"
    }
}
