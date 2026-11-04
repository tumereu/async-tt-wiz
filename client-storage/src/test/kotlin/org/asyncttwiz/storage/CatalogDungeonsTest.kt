package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** LOB-117: the dungeon cards of the installed catalog (token records in card data), each once, as card data of their own. Invented
 * dungeons only. */
class CatalogDungeonsTest {
    @TempDir lateinit var temporary: Path
    private fun repo() = CatalogRepository(temporary.resolve("catalog").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun record(n: Int, set: String, name: String, type: String, text: String, side: String = "", others: List<String> = emptyList(),
        layout: String = "token") = CatalogPrinting(id(n), name, set, "$n", "English", side, id(n + 1000), true, others,
        JsonObject().apply { addProperty("faceName", name); addProperty("type", type); addProperty("text", text); addProperty("layout", layout) }.toString())

    private val crypt = """
        Crypt Door — You gain 1 life. (Leads to: Bone Hall, Dark Well)
        Bone Hall — Each opponent loses 1 life. (Leads to: Final Vault)
        Dark Well — You gain 2 life. (Leads to: Final Vault)
        Final Vault — You gain 3 life.
    """.trimIndent()

    @Test fun theCatalogsDungeonsEachOnce() {
        val repository = repo()
        assertEquals(emptyList<Any>(), repository.dungeonDefinitions(), "no catalog installed: none")
        repository.stage { index ->
            index.addSet("TDG", "2021-07-23"); index.addSet("OTDG", "2021-07-23"); index.addSet("TUC", "2022-06-10")
            index.addBatch(listOf(
                record(1, "TDG", "Lantern Crypt", "Dungeon", crypt),
                // The same dungeon in an oversized set
                record(2, "OTDG", "Lantern Crypt", "Dungeon", crypt),
                // An Undercity printed as a double-faced token with a reminder back
                record(3, "TUC", "Sunken City", "Dungeon — Undercity", """
                    You can't enter this dungeon unless you "venture into Undercity."
                    Flooded Stair — You gain 4 life. (Leads to: Abyss Throne)
                    Abyss Throne — You gain 5 life.
                """.trimIndent(), side = "a", others = listOf(id(4)), layout = "double_faced_token"),
                record(4, "TUC", "The Claim", "Card", "(You can take the initiative.)", side = "b", others = listOf(id(3)), layout = "double_faced_token"),
                // A "dungeon" whose rooms lead nowhere isn't one the engine can play
                record(5, "TUC", "Open Wilds", "Dungeon", "Camp — Create a Treasure token.\nGrove — Draw a card."),
                record(6, "TDG", "Spirit", "Token Creature — Spirit", "Flying"),
            ))
            CatalogMetadata("2026-11-04", "5")
        }
        val dungeons = repository.dungeonDefinitions()
        assertEquals(listOf("Lantern Crypt", "Sunken City"), dungeons.map { it.definition.name })
        assertTrue(dungeons.all { it.definition.faces.size == 1 && it.definition.layout == "normal" && !it.definition.token })
        assertEquals("Dungeon — Undercity", dungeons[1].definition.faces.single().typeLine)
        assertTrue(org.asyncttwiz.protocol.game.OutsideCards.valid(dungeons))
    }
}
