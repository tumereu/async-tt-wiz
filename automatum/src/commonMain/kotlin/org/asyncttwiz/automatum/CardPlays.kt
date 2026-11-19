package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.game.EnginePreferences
import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CostPart
import arcanum.api.Zone
import arcanum.api.isManaAbility
import arcanum.engine.CastScreen
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Functioning
import arcanum.engine.view.View
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One thing the engine offers for a card (integration spec §3 I4, I-D28): casting it one way, playing it (a land),
 * activating one of its activated abilities (a mana ability too) or taking a special action it offers (turning a
 * face-down card face up, unlocking a door). [input] is what the engine takes now; null when it doesn't allow it now,
 * with [refusal] saying why. Serializable: it crosses the engine boundary ([org.asyncttwiz.automatum.boundary.EngineAnswer]).
 */
@Serializable
data class CardPlay(
    val kind: Kind,
    /** What the menu shows: the way to cast ("Flashback"), or the ability's printed text ("{T}: Add {G}."). */
    val text: String,
    val input: Input?,
    val refusal: Refusal? = null,
) {
    val legal: Boolean get() = input != null

    @Serializable
    sealed interface Kind {
        @Serializable @SerialName("cast") data object Cast : Kind
        @Serializable @SerialName("play") data object Play : Kind
        @Serializable @SerialName("ability") data object Ability : Kind
        @Serializable @SerialName("mana") data object Mana : Kind
        @Serializable @SerialName("special") data object Special : Kind
        /** SYS-207 (owner playtest 2026-11-19, N24): a prepared permanent's prepare spell, offered on the permanent like one of
         * its abilities: casting the spell's copy in exile (CR 723.3c), [input] that copy's cast. */
        @Serializable @SerialName("prepared") data object Prepared : Kind
    }

    /** Why the engine doesn't allow it now. */
    @Serializable
    sealed interface Refusal {
        /** Another player holds priority ([holder]; null: nobody does, as while attackers are awaited). */
        @Serializable @SerialName("priority") data class Priority(val holder: String?) : Refusal
        /** Only as a sorcery: in its controller's main phase with an empty Stack (CR 307.1, 602.5d). */
        @Serializable @SerialName("timing") data object Timing : Refusal
        /** A loyalty ability: once each turn, as a sorcery (CR 606.3). */
        @Serializable @SerialName("loyalty") data object Loyalty : Refusal
        /** It was used as often as it may be this turn (or this game). */
        @Serializable @SerialName("used") data object Used : Refusal
        /** The card belongs to (or is controlled by) another player: only they may play it. */
        @Serializable @SerialName("not-yours") data object NotYours : Refusal
        /** Anything else: a cost that can't be paid, no legal target, a condition not met. [detail]: the engine's own refusal,
         * or null when it would begin it and the engine's options still leave it out (a target or a cost it can't have), for
         * the report a catch-all reason makes (LOB-090, TBL-123). */
        @Serializable @SerialName("other") data class Other(val detail: String? = null) : Refusal
    }
}

/**
 * What a player may do with each card through the engine now (§3 I4): every card they can see in the hand, on the
 * battlefield, in a graveyard or in exile, with the engine's casts, land plays, activated abilities and special actions
 * ([Engine.options]: exactly what it accepts), and the abilities it doesn't allow now greyed with the reason. Cards of
 * other players list their abilities greyed ([CardPlay.Refusal.NotYours]); cards hidden from the player list nothing
 * (I-D27: a menu never tells what a hidden card can do).
 */
class CardPlays private constructor(
    /** By ATW card id, in the order shown: casts and land plays, then abilities in printed order. */
    val byCard: Map<String, List<CardPlay>>,
) {
    fun of(cardId: String): List<CardPlay> = byCard[cardId].orEmpty()

    companion object {
        val NONE = CardPlays(emptyMap())

        /** These plays as the engine boundary carried them ([org.asyncttwiz.automatum.boundary.CardPlaysSerializer]). */
        internal fun restored(byCard: Map<String, List<CardPlay>>): CardPlays = CardPlays(byCard)

        /**
         * [of] for a game on engine state (I-D56; engine-responsiveness R3, R4a): [state] is its own state (full
         * information, not changed here; the card ids are the game's), [view] its view and [visible] what [viewer] may
         * see of it ([Host.visible]), which the caller already has (worked out once on the engine thread, nothing
         * copied).
         */
        fun of(state: GameState, viewer: String, view: View, visible: Set<ObjectId>): CardPlays =
            of(state, CardIds(), viewer, view, visible)

        private fun of(state: GameState, ids: CardIds, viewer: String, view: View, visible: Set<ObjectId>): CardPlays {
            val me = PlayerId(viewer)
            val options = Engine.options(state, me, view, EnginePreferences.of(state))
            val byCard = LinkedHashMap<String, MutableList<CardPlay>>()
            fun add(id: ObjectId.Card, play: CardPlay) { byCard.getOrPut(ids.atw(id.card)) { mutableListOf() } += play }

            for (input in options) when (input) {
                is Input.Cast -> (input.card as? ObjectId.Card)?.let { card ->
                    add(card, CardPlay(CardPlay.Kind.Cast, CastFlow.wayLabel(state.obj(card)?.definition, input) ?: "Cast", input))
                }
                is Input.PlayLand -> (input.card as? ObjectId.Card)?.let { card ->
                    add(card, CardPlay(CardPlay.Kind.Play, HandCasts.wayName(state.obj(card)?.definition, input), input))
                }
                else -> Unit
            }
            val activations = options.filterIsInstance<Input.Activate>()
            for (o in state.objects.values) {
                val id = o.id
                if (id !in visible || o.phasedOut) continue
                if (o.zone !in ZONES) continue
                val shown = view[id] ?: continue
                val characteristics = shown.characteristics ?: continue
                val mine = (shown.controller ?: o.owner) == me
                // The viewer's own hand card no cast or play is offered for: greyed, with the engine's reason
                if (o.zone == Zone.Hand && o.owner == me && byCard[ids.atw(id.card)].isNullOrEmpty()) {
                    val land = CardType.Land in characteristics.types
                    val input = if (land) Input.PlayLand(me, id) else Input.Cast(me, id)
                    add(id, CardPlay(if (land) CardPlay.Kind.Play else CardPlay.Kind.Cast, if (land) "Play" else "Cast", null, refusal(state, view, input)))
                }
                val instantOrSorcery = CardType.Instant in characteristics.types || CardType.Sorcery in characteristics.types
                val listed = mutableSetOf<AbilityOrigin>()
                for (instance in characteristics.abilities) {
                    val ability = instance.ability
                    if (ability !is Ability.Activated && ability !is Ability.SpecialAction) continue
                    if (o.zone !in Functioning.zones(ability, instantOrSorcery)) continue
                    listed += instance.origin
                    val legal = activations.firstOrNull { it.source == id && it.ability == instance.origin }
                    val refusal = when {
                        legal != null -> null
                        !mine -> CardPlay.Refusal.NotYours
                        else -> refusal(state, view, Input.Activate(me, id, instance.origin), ability)
                    }
                    add(id, CardPlay(kind(ability), text(o.definition, instance, characteristics.name.orEmpty(), ids.atw(id.card)), legal, refusal))
                }
                // Special actions the characteristics don't show (a face-down card's way to turn face up, a door)
                for (input in activations) if (input.source == id && input.ability !in listed) {
                    add(id, CardPlay(CardPlay.Kind.Special, CastFlow.abilityText(o.definition, input.ability)
                    ?: Faults.placeholder("CardPlays", "special action without text", "Special action", mapOf("card" to ids.atw(id.card), "ability" to input.ability.toString())), input))
                }
            }
            preparedSpells(state, ids, me, view, visible, byCard)
            return CardPlays(byCard)
        }

        /**
         * SYS-207 (owner playtest 2026-11-19, N24): each prepared permanent's prepare spell (its copy in exile, CR 723.3c) is
         * offered on the permanent as [CardPlay.Kind.Prepared], "{G}: Cast Mending", after its abilities: the copy's casts the
         * engine offers, or greyed with why. The copy keeps its own casts (its exile pile's menu).
         */
        private fun preparedSpells(state: GameState, ids: CardIds, me: PlayerId, view: View, visible: Set<ObjectId>,
            byCard: LinkedHashMap<String, MutableList<CardPlay>>) {
            for (o in state.objects.values) {
                if (o.zone != Zone.Exile) continue
                val copy = o.id as? ObjectId.Card ?: continue
                val of = o.statuses.filterIsInstance<arcanum.engine.state.ObjectStatus.PrepareSpellOf>().firstOrNull()?.permanent ?: continue
                val permanent = state.obj(of)?.takeIf { it.zone == Zone.Battlefield } ?: continue
                val host = permanent.id as? ObjectId.Card ?: continue
                if (copy !in visible || host !in visible || permanent.phasedOut) continue
                val face = o.definition.faces.firstOrNull()
                val name = view[copy]?.characteristics?.name ?: face?.name
                    ?: Faults.placeholder("CardPlays", "prepare spell without its name", "the prepared spell", mapOf("copy" to ids.atw(copy.card)))
                val text = (face?.manaCost?.toString()?.takeIf { it.isNotEmpty() }?.let { "$it: " } ?: "") + "Cast $name"
                val casts = byCard[ids.atw(copy.card)].orEmpty().filter { it.kind == CardPlay.Kind.Cast && it.legal }
                val plays = if (casts.isNotEmpty()) casts.map { cast ->
                    CardPlay(CardPlay.Kind.Prepared, if (casts.size == 1 || cast.text == "Cast") text else "$text · ${cast.text}", cast.input)
                } else listOf(CardPlay(CardPlay.Kind.Prepared, text, null,
                    if (o.owner != me) CardPlay.Refusal.NotYours else refusal(state, view, Input.Cast(me, copy))))
                byCard.getOrPut(ids.atw(host.card)) { mutableListOf() } += plays
            }
        }

        private val ZONES = setOf(Zone.Hand, Zone.Battlefield, Zone.Graveyard, Zone.Exile)

        private fun kind(ability: Ability): CardPlay.Kind = when {
            ability is Ability.SpecialAction -> CardPlay.Kind.Special
            ability.isManaAbility() -> CardPlay.Kind.Mana
            else -> CardPlay.Kind.Ability
        }

        /** The ability's printed text, or its keyword's ("Equip {1}"), or the words worked out from it (TBL-124: a mana
         * ability's mana in symbols, [AbilityText]). */
        private fun text(definition: CardDefinition?, instance: AbilityInstance, name: String, card: String): String =
            CastFlow.text(instance.ability) ?: CastFlow.abilityText(definition, instance.origin) ?: AbilityText.of(instance.ability, "CardPlays",
                // Which card and where the ability came from (owner fault 2PTQG5: a token's ability without text)
                mapOf("card" to "$name ($card)", "origin" to instance.origin.toString(), "printed faces" to definition?.faces?.joinToString(" // ") { it.name }.orEmpty()))

        /** Why the engine refuses [input] now: the refusal [Engine.perform] gives before anything is done, asked without
         * performing it ([CastScreen.refusal], engine-responsiveness R3, on [view], [state]'s view); one it may still begin
         * (refused later, if at all: a target or a cost it can't have) is [CardPlay.Refusal.Other]. */
        private fun refusal(state: GameState, view: View, input: Input, ability: Ability? = null): CardPlay.Refusal {
            val player = when (input) { is Input.Activate -> input.player; is Input.Cast -> input.player; is Input.PlayLand -> input.player; else -> null }
            // Every cast, land play, activation and special action needs priority (CR 117.1, 116.2)
            if (state.priority.holder != player) return CardPlay.Refusal.Priority(state.priority.holder?.value)
            val message = CastScreen.refusal(state, input, view) ?: return CardPlay.Refusal.Other()
            val loyalty = (ability as? Ability.Activated)?.cost?.parts?.any { it is CostPart.Loyalty } == true
            return when {
                loyalty && "loyalty ability" in message -> CardPlay.Refusal.Loyalty
                "as a sorcery" in message || "main phase" in message || "sorcery" in message -> CardPlay.Refusal.Timing
                "time(s) each turn" in message || "only once" in message || "no land plays left" in message -> CardPlay.Refusal.Used
                else -> CardPlay.Refusal.Other(message.take(300))
            }
        }
    }
}
