package org.asyncttwiz.protocol.game

import arcanum.api.CardType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.PreparedDeck
import org.asyncttwiz.protocol.SubmittedDefinition
import arcanum.api.CardDefinition as EngineCard
import org.asyncttwiz.protocol.CardDefinition as GameCard

/**
 * Cards a player owns outside the game that aren't part of their deck or sideboard: dungeon cards (CR 309.2), brought in only by
 * venturing into the dungeon (CR 701.49) or taking the initiative (CR 726). A player's device sends the dungeon cards of its card
 * catalog with its Ready when its deck holds a card that ventures or takes the initiative ([mayVenture]); the server keeps them with
 * the prepared deck ([PreparedDeck.outside]) and the engine game starts with them among the player's cards outside the game.
 */
object OutsideCards {
    /** The most outside cards one player brings. */
    const val MAX = 8

    /** Card data whose rules text mentions venturing or the initiative (a quick look at the text, no parse): its player's device
     * sends its dungeon cards. */
    fun mayVenture(card: GameCard): Boolean = card.faces.any { face ->
        (listOf(face.rulesText) + face.parts.map { it.rulesText }).any { text ->
            text.contains("venture into", ignoreCase = true) || text.contains("the initiative", ignoreCase = true)
        }
    }

    /** What a parsed card needs of the dungeon cards outside the game: a plain venture ([Need.Dungeon]), "venture into Undercity"
     * or the initiative ([Need.Undercity], CR 701.49d, 726.2). */
    sealed interface Need {
        data object Dungeon : Need
        data object Undercity : Need
    }

    fun needs(definition: EngineCard): Set<Need> {
        val found = mutableSetOf<Need>()
        walk(json.encodeToJsonElement(EngineCard.serializer(), definition), found)
        return found
    }

    /** Whether [definition] is a dungeon the engine plays fully (I-D52: nothing left to the players): a dungeon card whose rooms lead
     * down to one bottommost room (CR 309.4, the rooms in order, each but the last with an arrow onward) and whose every room and
     * line the parser read (no `Manual` step, no unread text). A device also leaves out a dungeon its fuller support check
     * (Automatum's) finds any part of by hand. */
    fun isDungeon(definition: EngineCard?): Boolean {
        val face = definition?.faces?.singleOrNull() ?: return false
        if (CardType.Dungeon !in face.types || face.rooms.isEmpty()) return false
        val arrows = face.rooms.withIndex().all { (n, room) ->
            if (n == face.rooms.lastIndex) room.next.isEmpty() else room.next.isNotEmpty() && room.next.all { it in (n + 1)..face.rooms.lastIndex }
        }
        return arrows && !unread(json.encodeToJsonElement(EngineCard.serializer(), definition))
    }

    /** What [dungeons] (parsed) provide of the [Need]s: a dungeon a plain venture may enter, an Undercity. */
    fun provided(dungeons: List<EngineCard>): Set<Need> = buildSet {
        if (dungeons.any { isDungeon(it) && !undercity(it) }) add(Need.Dungeon)
        if (dungeons.any { isDungeon(it) && undercity(it) }) add(Need.Undercity)
    }

    private fun unread(element: JsonElement): Boolean = when (element) {
        is JsonArray -> element.any(::unread)
        is JsonObject -> (element["type"] as? JsonPrimitive)?.content in setOf("manual", "unparsed") || element.values.any(::unread)
        is JsonPrimitive -> false
    }

    /** Whether a parsed dungeon may be entered only by "venture into Undercity" (Undercity's own text, CR 701.49d). */
    fun undercity(definition: EngineCard): Boolean = definition.faces.firstOrNull()?.subtypes.orEmpty().any { it.name == "Undercity" }

    /**
     * A catalog record's dungeon face as card data of its own (a dungeon printed as a double-faced token keeps only its dungeon face;
     * its other face is a reminder card), or null when [card] has no dungeon face that plays as one ([isDungeon]).
     */
    fun dungeon(card: GameCard): SubmittedDefinition? {
        val face = card.faces.firstOrNull { it.typeLine.removePrefix("Token ").startsWith("Dungeon") } ?: return null
        val single = card.copy(name = face.name, faces = listOf(face.copy(otherFaceIds = emptyList())), layout = "normal", token = false)
        if (!DeckValidation.definition(single) || !isDungeon(GameCards.parse(single).definition)) return null
        return SubmittedDefinition(DeckJson.digest(single), single)
    }

    /** The server's check of a player's outside cards: at most [MAX], each its digest's card data and a dungeon ([isDungeon]). */
    fun valid(definitions: List<SubmittedDefinition>): Boolean = definitions.size <= MAX &&
        definitions.map { it.digest }.distinct().size == definitions.size && definitions.all { d ->
            DeckValidation.definition(d.definition) && DeckJson.digest(d.definition) == d.digest && isDungeon(GameCards.parse(d.definition).definition)
        }

    /** Whether [outside] (parsed) has the dungeons a deck that [needs] them asks for. */
    fun provides(needs: Set<Need>, outside: List<EngineCard>): Boolean = provided(outside).containsAll(needs)

    /** The game-local card id of the [index]th outside card of [deck] (the engine plays it by this id). */
    fun cardId(deck: PreparedDeck, index: Int): String = org.asyncttwiz.protocol.CommandIds.uuidV5(deck.selectionRequestId, "outside-card/$index")

    private val json = Json

    private fun walk(element: JsonElement, found: MutableSet<Need>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, found) }
            is JsonObject -> {
                when ((element["type"] as? JsonPrimitive)?.content) {
                    "keyword-action", "action" -> when (((element["action"] as? JsonObject)?.get("type") as? JsonPrimitive)?.content) {
                        "venture" -> found += Need.Dungeon
                        "venture-into" -> found += Need.Undercity
                    }
                    // Taking the initiative (an effect giving it to players), not "if you have the initiative"
                    "designation" -> if (element["players"] != null &&
                        ((element["designation"] as? JsonObject)?.get("type") as? JsonPrimitive)?.content == "initiative")
                        found += Need.Undercity
                }
                element.values.forEach { walk(it, found) }
            }
            is JsonPrimitive -> Unit
        }
    }
}
