package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId

/**
 * LOB-089 (problem report KX8GYS): the app log's plain record of what a game shows this player, so that a report can be read
 * without decoding its bundle: a line for what each board changed (cards into and out of the hand, drawn or played, the
 * battlefields, graveyards, exile and the Stack, life) and the whole board when the game opens and at each new turn. Only what
 * the player sees: of the other player's hand and of libraries, only how many cards.
 */
internal class GameLog(gameId: String, private val me: PlayerId) {
    private val game = "Game ${gameId.take(8)} seat ${me.value.take(8)}"
    private var last: Map<String, List<String>>? = null
    private var lastTurn: Int? = null
    private var logged: GameState? = null

    /** The lines to log for [state] now being shown: none when it is the board logged last or nothing listed changed. */
    fun shown(state: GameState): List<String> {
        if (state === logged) return emptyList()
        logged = state
        val zones = zones(state)
        val before = last
        val newTurn = lastTurn != state.turn.number
        last = zones
        lastTurn = state.turn.number
        if (before == null || newTurn) return listOf(board(state))
        val changed = zones.mapNotNull { (zone, cards) ->
            diff(before[zone].orEmpty(), cards).takeIf { it.isNotEmpty() }?.let { "$zone ${it.joinToString(", ")}" }
        }
        return if (changed.isEmpty()) emptyList() else listOf("$game ${at(state)}: ${changed.joinToString("; ")}")
    }

    /** The whole board of [state]; with [options] (what the player may do now, [Engine.options]), those too. */
    fun board(state: GameState, options: List<Input>? = null): String = buildString {
        append("$game ${at(state)}, active ${state.turn.active.value.take(8)}, priority ${state.priority.holder?.value?.take(8)}")
        state.pending?.let { append(", waiting for ${it.decision.player.value.take(8)}") }
        zones(state).forEach { (zone, cards) -> append("; $zone [${cards.joinToString(", ")}]") }
        options?.let { append("; can do [${it.mapNotNull { input -> describe(state, input) }.joinToString(", ")}]") }
    }

    private fun at(s: GameState) = "turn ${s.turn.number} ${s.turn.step}"

    private fun zones(s: GameState): Map<String, List<String>> {
        val mine = s.player(me)
        val other = s.players.firstOrNull { it.id != me }
        return buildMap {
            put("life", listOf(mine.life.toString()))
            put("hand", mine.hand.map { name(s, it) })
            put("library", listOf("${mine.library.size} cards"))
            put("battlefield", s.battlefield.filter { s.obj(it)?.controller == me }.map { onTable(s, it) })
            put("graveyard", mine.graveyard.map { name(s, it) })
            other?.let { o ->
                put("their life", listOf(o.life.toString()))
                put("their hand", listOf("${o.hand.size} cards"))
                put("their library", listOf("${o.library.size} cards"))
                put("their battlefield", s.battlefield.filter { s.obj(it)?.controller == o.id }.map { onTable(s, it) })
                put("their graveyard", o.graveyard.map { name(s, it) })
            }
            put("exile", s.exile.map { name(s, it) })
            put("stack", s.stack.map { id -> s.stackItems[id]?.takeIf { s.obj(id) == null }?.let { item -> "ability of ${item.source?.let { source -> name(s, source) } ?: "?"}" } ?: name(s, id) })
        }
    }

    private fun onTable(s: GameState, id: ObjectId) = name(s, id) + if (s.obj(id)?.tapped == true) " (tapped)" else ""

    private fun name(s: GameState, id: ObjectId): String {
        val o: GameObject = s.obj(id) ?: return "?"
        if (o.faceDown && o.controller != me) return "face-down card"
        val name = o.definition?.faces?.getOrNull(o.face)?.name ?: return "hidden card"
        return if (o.faceDown) "$name (face down)" else name
    }

    private fun describe(s: GameState, input: Input): String? = when (input) {
        is Input.Cast -> "cast ${name(s, input.card)}"
        is Input.PlayLand -> "play ${name(s, input.card)}"
        is Input.Activate -> "activate ${name(s, input.source)}"
        is Input.PassPriority -> "pass"
        else -> null
    }

    private fun diff(old: List<String>, new: List<String>): List<String> {
        val gone = old.toMutableList()
        val came = new.filterNot { gone.remove(it) }
        return came.map { "+$it" } + gone.map { "-$it" }
    }

    companion object {
        /** What [Engine.options] lists now, for the report's last board; none when it can't be worked out. */
        fun options(state: GameState, me: PlayerId): List<Input> = runCatching { Engine.options(state, me) }.getOrDefault(emptyList())
    }
}
