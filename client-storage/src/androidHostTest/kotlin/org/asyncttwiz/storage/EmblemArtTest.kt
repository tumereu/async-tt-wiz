package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** TRN-053 (owner gallery review 2026-11-06): an emblem's picture is the emblem printing with its rules text. Every
 * record here is invented. */
class EmblemArtTest {
    @TempDir lateinit var temporary: Path
    private val repository by lazy {
        CatalogRepository(temporary.resolve("catalog").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun art(n: Int) = "00000000-0000-4000-9000-${n.toString().padStart(12, '0')}"

    private fun emblem(n: Int, set: String, text: String, layout: String = "emblem") = JsonObject().apply {
        addProperty("uuid", id(n)); addProperty("name", "Vessa, Tide Caller Emblem"); addProperty("setCode", set)
        addProperty("number", "E$n"); addProperty("language", "English"); addProperty("layout", layout)
        addProperty("type", "Emblem — Vessa"); addProperty("text", text)
        add("identifiers", JsonObject().apply { addProperty("scryfallId", art(n)) })
    }

    private fun install(vararg sets: Triple<String, String, List<JsonObject>>) {
        val data = JsonObject()
        for ((code, date, tokens) in sets) data.add(code, JsonObject().apply {
            addProperty("code", code); addProperty("name", "Invented $code"); addProperty("type", "expansion")
            addProperty("releaseDate", date)
            add("cards", JsonArray())
            add("tokens", JsonArray().apply { tokens.forEach(::add) })
        })
        val root = JsonObject().apply {
            add("meta", JsonObject().apply { addProperty("date", "2026-11-01"); addProperty("version", "5.3.0") })
            add("data", data)
        }
        repository.stage { index -> MtgJsonCatalog().import(root.toString().byteInputStream(), index, CatalogMetadata("2026-11-01", "5.3.0")) }
    }

    // TRN-053: the newest emblem printing with the same rules text, case, punctuation and reminder text aside
    @Test fun theEmblemWithTheSameRulesTextGivesThePicture() {
        install(Triple("OLD", "2020-01-01", listOf(emblem(1, "OLD", "Creatures you control get +1/+1."),
                emblem(2, "OLD", "Whenever you gain life, target opponent loses that much life."))),
            Triple("NEW", "2025-01-01", listOf(emblem(3, "NEW", "Creatures you control get +1/+1."),
                emblem(4, "NEW", "At the beginning of your end step, draw a card.", layout = "token"))))
        assertEquals(art(3), repository.emblemArt("Creatures you control get +1/+1."))
        assertEquals(art(2), repository.emblemArt("whenever you gain life, target opponent loses that much life"))
        // A token's text isn't an emblem's; text no emblem has finds nothing
        assertNull(repository.emblemArt("At the beginning of your end step, draw a card."))
        assertNull(repository.emblemArt("Spells you cast cost {1} less."))
    }

    @Test fun withoutCardRecordsThereIsNoPicture() {
        assertNull(repository.emblemArt("Creatures you control get +1/+1."))
    }
}
