package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.asyncttwiz.protocol.DefinitionFace
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Automatum's token pictures (integration spec §4.6, SYS-072): the best catalog token printing for a token an
 * effect makes. Every card and token here is invented. */
class TokenArtTest {
    @TempDir lateinit var temporary: Path
    private val repository by lazy {
        CatalogRepository(temporary.resolve("catalog").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun art(n: Int) = "00000000-0000-4000-9000-${n.toString().padStart(12, '0')}"

    private fun strings(values: List<String>) = JsonArray().apply { values.forEach(::add) }
    private fun card(n: Int, set: String, name: String, tokens: List<Int> = emptyList()) = JsonObject().apply {
        addProperty("uuid", id(n)); addProperty("name", name); addProperty("setCode", set); addProperty("number", "$n")
        addProperty("language", "English"); addProperty("layout", "normal"); addProperty("type", "Artifact — Food")
        addProperty("manaCost", "{1}{W}"); add("colors", strings(listOf("W")))
        addProperty("text", "When this artifact enters, create a 1/1 white Hare creature token.")
        add("identifiers", JsonObject().apply { addProperty("scryfallId", art(n)) })
        if (tokens.isNotEmpty()) add("relatedCards", JsonObject().apply { add("tokens", strings(tokens.map(::id))) })
    }
    private fun token(n: Int, set: String, name: String = "Hare", type: String = "Token Creature — Hare",
        colors: List<String> = listOf("W"), power: String? = "1", toughness: String? = "1", text: String? = null,
        madeBy: List<String> = emptyList(), picture: Boolean = true, side: String? = null) = JsonObject().apply {
        addProperty("uuid", id(n)); addProperty("name", name); addProperty("setCode", set); addProperty("number", "T$n")
        addProperty("language", "English"); addProperty("layout", "token"); addProperty("type", type)
        add("colors", strings(colors)); power?.let { addProperty("power", it) }; toughness?.let { addProperty("toughness", it) }
        text?.let { addProperty("text", it) }
        side?.let { addProperty("side", it) }
        if (picture) add("identifiers", JsonObject().apply { addProperty("scryfallId", art(n)) })
        if (madeBy.isNotEmpty()) add("relatedCards", JsonObject().apply { add("reverseRelated", strings(madeBy)) })
    }

    /** Installs one invented catalog of [sets] (code to release date, cards, tokens). */
    private fun install(vararg sets: Triple<String, String, List<JsonObject>>) {
        val data = JsonObject()
        for ((code, date, records) in sets) data.add(code, JsonObject().apply {
            addProperty("code", code); addProperty("name", "Invented $code"); addProperty("type", "expansion")
            addProperty("releaseDate", date)
            add("cards", JsonArray().apply { records.filter { it.get("layout").asString != "token" }.forEach(::add) })
            add("tokens", JsonArray().apply { records.filter { it.get("layout").asString == "token" }.forEach(::add) })
        })
        val root = JsonObject().apply {
            add("meta", JsonObject().apply { addProperty("date", "2026-11-01"); addProperty("version", "5.3.0") })
            add("data", data)
        }
        repository.stage { index -> MtgJsonCatalog().import(root.toString().byteInputStream(), index, CatalogMetadata("2026-11-01", "5.3.0")) }
    }

    /** A token as Automatum's card data shows it (AtwTokens): a 1/1 white Hare unless said otherwise. */
    private fun hare(name: String = "Hare", typeLine: String = "Token Creature — Hare", colors: String? = "W",
        power: String = "1", toughness: String = "1", text: String = "") =
        DefinitionFace("face", name, typeLine = typeLine, rulesText = text, power = power, toughness = toughness, colorIndicator = colors)

    private fun found(face: DefinitionFace, sourceName: String? = null, sourcePrinting: Int? = null) =
        repository.tokenArt(face, sourceName, sourcePrinting?.let(::id))?.id

    @Test fun aSourceWithOneRelatedTokenShowsThatTokenOverNewerLookalikes() {
        install(Triple("AAA", "2024-01-01", listOf(card(1, "AAA", "Carrot Tart", tokens = listOf(2)), token(2, "AAA"))),
            Triple("NEW", "2026-01-01", listOf(token(3, "NEW"))))
        assertEquals(id(2), found(hare(), "Carrot Tart", 1))
        // A card's own links are by its printing; without it only tokens naming the card relate to it (next test)
        assertEquals(id(3), found(hare(), "Carrot Tart"), "without the source's printing, the newest lookalike")
    }

    @Test fun severalRelatedTokensPreferTheMatchingOneThenTheSourcesSetThenTheNewest() {
        val maker = listOf("Warren Caller")
        install(Triple("OLD", "2020-01-01", listOf(token(10, "OLD", madeBy = maker))),
            Triple("AAA", "2024-01-01", listOf(card(20, "AAA", "Warren Caller"), token(21, "AAA", madeBy = maker),
                token(22, "AAA", name = "Food", type = "Token Artifact — Food", colors = emptyList(), power = null, toughness = null,
                    text = "{2}, {T}, Sacrifice this token: You gain 3 life.", madeBy = maker))),
            Triple("BBB", "2025-01-01", listOf(token(30, "BBB", madeBy = maker))),
            Triple("NEW", "2026-01-01", listOf(token(40, "NEW", colors = listOf("G"), power = "2", toughness = "2", madeBy = maker),
                token(41, "NEW", madeBy = maker, picture = false), token(42, "NEW", madeBy = maker, side = "b"))))
        assertEquals(id(21), found(hare(), "Warren Caller", 20), "the source's set")
        assertEquals(id(30), found(hare(), "Warren Caller"), "the newest with a picture of its front")
        assertEquals(id(40), found(hare(colors = "G", power = "2", toughness = "2"), "Warren Caller", 20), "the matching one")
        assertEquals(id(22), found(hare("Food", "Token Artifact — Food", null, "", "",
            "{2}, {T}, Sacrifice this token: You gain 3 life."), "Warren Caller", 20))
    }

    @Test fun withoutRelatedTokensEveryCharacteristicMustMatch() {
        install(Triple("OLD", "2020-01-01", listOf(card(1, "OLD", "Burrow Herald"), token(2, "OLD"))),
            Triple("AAA", "2024-01-01", listOf(token(3, "AAA"), token(4, "AAA", text = "Vigilance"))),
            Triple("NEW", "2026-01-01", listOf(token(5, "NEW", power = "2"), token(6, "NEW", colors = listOf("W", "G")),
                token(7, "NEW", type = "Token Artifact Creature — Hare"), token(8, "NEW", picture = false))))
        assertEquals(id(3), found(hare(), "Burrow Herald"), "the newest with every characteristic and a picture")
        assertEquals(id(2), found(hare(), "Burrow Herald", 1), "ties go to the source's set")
        assertEquals(id(4), found(hare(text = "Vigilance (Attacking doesn't cause this creature to tap.)")))
        assertEquals(id(6), found(hare(colors = "GW")))
        assertEquals(id(7), found(hare(typeLine = "Token Artifact Creature — Hare")))
        // CR 111.4: the engine's token is named "Hare Token"; the catalog's printing "Hare"
        assertEquals(id(3), found(hare("Hare Token")))
    }

    @Test fun noMatchKeepsThePlainFrame() {
        install(Triple("AAA", "2024-01-01", listOf(card(1, "AAA", "Carrot Tart", tokens = listOf(2)), token(2, "AAA"))))
        assertNull(found(hare("Squirrel", "Token Creature — Squirrel", "G"), "Carrot Tart", 1))
        assertNull(found(hare(colors = "B")), "a lookalike in name only")
        assertNull(CatalogRepository(temporary.resolve("empty").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
            .tokenArt(hare(), "Carrot Tart", id(1)), "no card records")
    }

    @Test fun keywordListsAndReminderTextReadAlike() {
        val a = TokenArtMatch.traits(hare(text = "Flying, haste"))
        val b = TokenArtMatch.traits(hare(text = "Flying\nHaste (This creature can attack and {T} as soon as it comes under your control.)"))
        assertEquals(4, a.agreement(b))
    }
}
