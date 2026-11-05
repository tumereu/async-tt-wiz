package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.SubmittedDefinition
import java.util.UUID

/**
 * Invented cards for the e2e games (AGENTS.md: our own names and costs, wording from real oracle templates), as a
 * device submits them in a deck: the card data the server and both devices parse. The interim app tests' cards
 * (EngineGameFixture) have the same names and texts.
 */
object TestCards {
    private fun card(name: String, typeLine: String, manaCost: String = "", rulesText: String = "", power: String = "",
        toughness: String = "", loyalty: String = ""): SubmittedDefinition {
        val face = DefinitionFace(UUID.nameUUIDFromBytes("e2e/$name".encodeToByteArray()).toString(), name, typeLine = typeLine,
            manaCost = manaCost, rulesText = rulesText, power = power, toughness = toughness, loyalty = loyalty)
        val definition = CardDefinition(name, listOf(face))
        return SubmittedDefinition(DeckJson.digest(definition), definition)
    }

    val mountain = card("Mountain", "Basic Land — Mountain")
    val spark = card("Storm Spark", "Instant", "{R}", "Storm Spark deals 3 damage to any target.")
    val cub = card("Ember Cub", "Creature — Elemental Cat", "{R}", power = "2", toughness = "1")
    val herald = card("Ember Herald", "Creature — Elemental", "{R}", "When this creature enters, create a 1/1 red Elemental creature token.",
        power = "1", toughness = "1")
    val hound = card("Cinder Hound", "Creature — Elemental Dog", "{R}", "{R}: This creature gets +1/+0 until end of turn.", "2", "1")
    val stride = card("Ember Stride", "Sorcery", "{R}", "Take an extra turn after this one.")
}

/** A deck: [entries] of cards, in this order (the library's order before the opening shuffle). */
data class TestDeck(val name: String, val entries: List<Pair<SubmittedDefinition, Int>>) {
    val size get() = entries.sumOf { it.second }

    companion object {
        /** 40 cards, a third each of Mountains, [creature]s and Storm Sparks (as the interim fixture's decks). */
        fun thirds(creature: SubmittedDefinition = TestCards.cub) =
            TestDeck("Thirds", listOf(TestCards.mountain to 14, creature to 13, TestCards.spark to 13))

        /** 40 cards: Mountains and Storm Sparks. */
        fun sparks() = TestDeck("Sparks", listOf(TestCards.mountain to 20, TestCards.spark to 20))
    }
}
