package org.asyncttwiz.automatum

import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.View

/**
 * TBL-104 (owner, 2026-11-07): how long an effect object lasts, in concrete words for Inspect, read from the effect's
 * own data: the named player ("until the end of Bob's next turn"), step ("until your next end step"), source ("until
 * Ember Herald leaves the battlefield"), card ("until Ember Herald is cast") or condition ("as long as you control it":
 * a suspended creature's haste, CR 702.62a). [viewer] reads "you"; [names] names the other players by account. A card
 * [viewer] may not see is "a card" (I-D27). [about] is the card being inspected: it reads "it".
 */
internal class DurationText(
    private val state: GameState,
    private val view: View,
    private val viewer: String,
    private val names: Map<String, String> = emptyMap(),
) {
    /** [effect]'s duration as a phrase ("until end of turn"); null when it lasts indefinitely. */
    fun of(effect: EffectObject, about: ObjectId? = null): String? = of(effect.duration, effect, about)

    /** [duration] of [effect] (null when not known: the generic words) as a phrase; null when it lasts indefinitely. */
    fun of(duration: Duration, effect: EffectObject?, about: ObjectId? = null): String? = when (duration) {
        Duration.EndOfTurn -> "until end of turn"
        Duration.EndOfCombat -> "until end of combat"
        Duration.UntilYourNextTurn -> "until ${possessive(effect?.controller, "its controller's")} next turn"
        Duration.UntilEndOfYourNextTurn -> "until the end of ${possessive(effect?.controller, "its controller's")} next turn"
        is Duration.UntilStep -> "until ${stepOwner(duration.whose, effect)} next ${stepName(duration.step)}"
        is Duration.ThroughNextStep ->
            "until after ${possessive(effect?.let { players(duration.whose, it).singleOrNull() }, "its controller's")} next ${stepName(duration.step)}"
        is Duration.AsLongAs -> "as long as " + (effect?.let { condition(duration.condition, it, about) }
            ?: effect?.source?.let(::name)?.let { "$it's condition holds" } ?: "its condition holds")
        is Duration.UntilLeaves -> {
            val what = effect?.let { subject(duration.objects, it, about) }
            if (what == null) "until the card that made it leaves the battlefield"
            else "until ${what.first} ${if (what.second) "leave" else "leaves"} the battlefield"
        }
        is Duration.UntilCast -> {
            val what = effect?.let { subject(duration.objects, it, about) }
            if (what == null) "until the exiled card is cast" else "until ${what.first} ${if (what.second) "are" else "is"} cast"
        }
        is Duration.UntilEndOfNextTurnOf ->
            "until the end of ${possessive(effect?.let { players(duration.players, it).singleOrNull() }, "that player's")} next turn"
        Duration.Indefinitely -> null
    }

    /** The condition of a "for as long as" duration, or null when it isn't one these words cover. */
    private fun condition(condition: Condition, effect: EffectObject, about: ObjectId?): String? = when (condition) {
        is Condition.ObjectMatches -> subject(condition.objects, effect, about)?.let { (who, plural) -> filter(condition.filter, who, plural, effect) }
        is Condition.And -> condition.all.map { condition(it, effect, about) ?: return null }.joinToString(" and ")
        else -> null
    }

    /** "[who] [filter]" as a clause ("you control it", "it has a time counter on it"), or null when not covered. */
    private fun filter(filter: ObjectFilter, who: String, plural: Boolean, effect: EffectObject): String? = when (filter) {
        is ObjectFilter.ControlledBy -> playerFilter(filter.players, effect)?.let { p ->
            "${p.first} ${if (p.second) "control" else "controls"} $who"
        }
        is ObjectFilter.HasCounter -> {
            // any kind when none is named
            val kind = filter.kind?.let { ComputedBoard.counterName(EngineCodec.counterName(it)).removeSuffix(" counter") + " " }.orEmpty()
            val has = if (plural) "have" else "has"
            val on = if (plural) "them" else "it"
            if (filter.atLeast <= 1) "$who $has ${article(kind + "counter")} ${kind}counter on $on"
            else "$who $has ${filter.atLeast} or more ${kind}counters on $on"
        }
        is ObjectFilter.InZone -> {
            val stays = if (plural) "remain" else "remains"
            when (filter.zone) {
                Zone.Exile -> "$who $stays exiled"
                Zone.Battlefield -> "$who $stays on the battlefield"
                Zone.Stack -> "$who ${if (plural) "are" else "is"} on the Stack"
                Zone.Graveyard -> "$who $stays in the graveyard"
                Zone.Hand -> "$who $stays in hand"
                Zone.Library -> "$who $stays in the library"
                else -> null
            }
        }
        is ObjectFilter.Is -> (filter.objects as? Objects.Remembered)?.let {
            "$who ${if (plural) "are" else "is"} the last card exiled with ${effect.source?.let(::name) ?: "its source"}"
        }
        is ObjectFilter.And -> filter.all.map { filter(it, who, plural, effect) ?: return null }.joinToString(" and ")
        else -> null
    }

    /** A player filter as the subject of a clause, with whether it takes the plural verb ("you", "Bob"). */
    private fun playerFilter(filter: PlayerFilter, effect: EffectObject): Pair<String, Boolean>? = when (filter) {
        PlayerFilter.You -> player(effect.controller).let { it to (it == "you") }
        PlayerFilter.Opponent -> opponents(effect.controller).singleOrNull()?.let { player(it) to false } ?: ("an opponent" to false)
        is PlayerFilter.Is -> players(filter.players, effect).singleOrNull()?.let { player(it).let { p -> p to (p == "you") } }
        else -> null
    }

    /** Whose step an "until" names: "your", "Bob's", "the" (anyone's). */
    private fun stepOwner(filter: PlayerFilter, effect: EffectObject?): String = when (filter) {
        PlayerFilter.Any -> "the"
        PlayerFilter.You -> possessive(effect?.controller, "its controller's")
        PlayerFilter.Opponent -> possessive(effect?.let { opponents(it.controller).singleOrNull() }, "an opponent's")
        is PlayerFilter.Is -> possessive(effect?.let { players(filter.players, it).singleOrNull() }, "the")
        else -> "the"
    }

    /** [objects] of [effect] as a subject ("it", "Ember Herald", "Ember Herald and Hill Ogre") with whether it is
     * plural; null when they can't be named. */
    private fun subject(objects: Objects, effect: EffectObject, about: ObjectId?): Pair<String, Boolean>? {
        val ids = objects(objects, effect).ifEmpty { return null }
        val unique = ids.distinctBy { (it as? ObjectId.Card)?.card ?: it }
        val words = unique.map { id -> if (about != null && sameCard(id, about)) "it" else name(id) ?: return null }.distinct()
        return words.joinToString(" and ") to (words.size > 1)
    }

    private fun objects(objects: Objects, effect: EffectObject): List<ObjectId> = when (objects) {
        Objects.Self -> listOfNotNull(effect.source)
        is Objects.Bound -> bound(effect, objects.name)
        is Objects.Target -> bound(effect, objects.slot)
        is Objects.Union -> objects.parts.flatMap { objects(it, effect) }
        else -> emptyList()
    }

    private fun bound(effect: EffectObject, name: String): List<ObjectId> = when (val v = effect.names[name]) {
        is NamedValue.Objects -> v.ids
        is NamedValue.Targets -> v.refs.filterIsInstance<TargetRef.Object>().map { it.id }
        else -> emptyList()
    }

    private fun players(players: Players, effect: EffectObject): List<PlayerId> = when (players) {
        Players.You -> listOf(effect.controller)
        is Players.Bound -> boundPlayers(effect, players.name)
        is Players.Target -> boundPlayers(effect, players.slot)
        is Players.OwnerOf -> objects(players.objects, effect).mapNotNull { latest(it)?.owner }.distinct()
        is Players.ControllerOf -> objects(players.objects, effect).mapNotNull { latest(it)?.let { o -> o.controller ?: o.owner } }.distinct()
        else -> emptyList()
    }

    private fun boundPlayers(effect: EffectObject, name: String): List<PlayerId> = when (val v = effect.names[name]) {
        is NamedValue.Players -> v.ids
        is NamedValue.Targets -> v.refs.filterIsInstance<TargetRef.Player>().map { it.id }
        else -> emptyList()
    }

    private fun opponents(of: PlayerId): List<PlayerId> = state.players.map { it.id }.filter { it != of }

    /** "you", the player's name or "the opponent". */
    private fun player(id: PlayerId): String = if (id.value == viewer) "you" else names[id.value] ?: "the opponent"

    /** "your", "Bob's", or [unknown] when there is no one player. */
    private fun possessive(id: PlayerId?, unknown: String): String = when {
        id == null -> unknown
        id.value == viewer -> "your"
        else -> "${names[id.value] ?: "the opponent"}'s"
    }

    /** The object or its card's latest object. */
    private fun latest(id: ObjectId) = state.obj(id) ?: (id as? ObjectId.Card)?.let { c ->
        state.objects.values.lastOrNull { (it.id as? ObjectId.Card)?.card == c.card } }

    /** [id]'s name as [viewer] may see it: "a card" when hidden. */
    fun name(id: ObjectId): String? {
        val obj = latest(id) ?: return null
        if (obj.faceDown || obj.zone !in PUBLIC) return "a card"
        return view[obj.id]?.name ?: obj.definition?.faces?.getOrNull(obj.face)?.name
    }

    private fun sameCard(a: ObjectId, b: ObjectId) = a == b || ((a as? ObjectId.Card)?.card != null && (a as? ObjectId.Card)?.card == (b as? ObjectId.Card)?.card)

    private fun article(word: String) = if (word.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an" else "a"

    companion object {
        private val PUBLIC = setOf(Zone.Battlefield, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)

        /** A step as players name it ("end step", "upkeep"). */
        fun stepName(step: TurnStep): String = when (step) {
            TurnStep.Untap -> "untap step"
            TurnStep.Upkeep -> "upkeep"
            TurnStep.Draw -> "draw step"
            TurnStep.PrecombatMain -> "precombat main phase"
            TurnStep.BeginningOfCombat -> "beginning of combat"
            TurnStep.DeclareAttackers -> "declare attackers step"
            TurnStep.DeclareBlockers -> "declare blockers step"
            TurnStep.CombatDamage -> "combat damage step"
            TurnStep.EndOfCombat -> "end of combat step"
            TurnStep.PostcombatMain -> "postcombat main phase"
            TurnStep.End -> "end step"
            TurnStep.Cleanup -> "cleanup step"
        }
    }
}
