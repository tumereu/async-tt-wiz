package org.asyncttwiz.catalog

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * The invented test cards (tests/fixtures/card-catalog) with one more invented set, `OMN` "Test Omenpaths", shaped as MTGJSON
 * gives Through the Omenpaths: sold only on Arena (an `arena` booster recipe beside a `collector` one), a modal double-faced
 * mythic whose faces print other names than their Oracle names only as `facePrintedName` (no `faceFlavorName`), and cards
 * whose names hold a letter 17Lands' data turns into "?" ("Lantern of Kōra"; "Mote of Kōra" and "Mote of Kira" both fit
 * "Mote of K?ra").
 */
object TestOmenpaths {
    const val FRONT = "f9ed428b-1043-4e16-aec2-27734d3b1caf"
    const val BACK = "c56701d9-a0ab-43ad-a1c1-7001ab7be634"
    const val LANTERN = "dd4eed00-ae5b-4f1f-8ab4-9ee83dc32dd2"
    private const val MOTE = "dba23ddb-531f-4b41-aaa4-e87c099c0b67"
    private const val MOTE_OTHER = "d950bd51-6360-460a-831b-a17be52fc286"

    /** The fixture's AllPrintings with `OMN` added, as bytes to build from. */
    fun allPrintings(fixtures: File): ByteArray {
        val root = JsonParser.parseString(File(fixtures, "AllPrintings.json").readText()).asJsonObject
        root.getAsJsonObject("data").add("OMN", set())
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun set(): JsonObject = JsonParser.parseString("""
        {"code": "OMN", "name": "Test Omenpaths", "type": "expansion", "releaseDate": "2026-01-01", "keyruneCode": "OMN",
         "booster": {
          "collector": {"boosters": [{"contents": {"rare": 1}, "weight": 1}], "boostersTotalWeight": 1,
            "sheets": {"rare": {"cards": {"$FRONT": 1}, "totalWeight": 1}}, "sourceSetCodes": ["OMN"]},
          "arena": {"name": "Test Omenpaths Arena Booster",
            "boosters": [{"contents": {"common": 2, "rareMythic": 1}, "weight": 1}], "boostersTotalWeight": 1,
            "sheets": {"common": {"cards": {"$LANTERN": 1, "$MOTE": 1, "$MOTE_OTHER": 1}, "totalWeight": 3},
              "rareMythic": {"cards": {"$FRONT": 1}, "totalWeight": 1}}, "sourceSetCodes": ["OMN"]}},
         "cards": []}""").asJsonObject.also { set ->
        val cards = set.getAsJsonArray("cards")
        cards.add(card(FRONT, "Lena Marsh // Crimson Wisp", "1", "mythic", "Creature — Human", "{1}{R}", "2", "2",
            face = "Lena Marsh", side = "a", other = BACK, printed = "Veyra, Thread Weaver"))
        cards.add(card(BACK, "Lena Marsh // Crimson Wisp", "1", "mythic", "Creature — Spirit", "{3}{R}", "4", "4",
            face = "Crimson Wisp", side = "b", other = FRONT, printed = "Veyra, Loom Ascendant"))
        cards.add(card(LANTERN, "Lantern of Kōra", "2", "common", "Artifact", "{2}"))
        cards.add(card(MOTE, "Mote of Kōra", "3", "common", "Artifact", "{1}"))
        cards.add(card(MOTE_OTHER, "Mote of Kira", "4", "common", "Artifact", "{1}"))
    }

    private fun card(uuid: String, name: String, number: String, rarity: String, type: String, cost: String,
        power: String? = null, toughness: String? = null, face: String? = null, side: String? = null, other: String? = null,
        printed: String? = null) = JsonObject().apply {
        addProperty("uuid", uuid)
        addProperty("name", name)
        addProperty("setCode", "OMN")
        addProperty("number", number)
        addProperty("language", "English")
        addProperty("layout", if (face == null) "normal" else "modal_dfc")
        addProperty("type", type)
        add("colors", JsonArray().apply { if ("{R}" in cost) add("R") })
        add("colorIdentity", JsonArray().apply { if (face != null) add("R") })
        addProperty("rarity", rarity)
        add("availability", JsonArray().apply { add("arena") })
        addProperty("isOnlineOnly", true)
        add("legalities", JsonObject())
        add("identifiers", JsonObject().apply { addProperty("scryfallId", uuid) })
        addProperty("manaCost", cost)
        power?.let { addProperty("power", it) }
        toughness?.let { addProperty("toughness", it) }
        face?.let { addProperty("faceName", it) }
        side?.let { addProperty("side", it) }
        other?.let { add("otherFaceIds", JsonArray().apply { add(it) }) }
        // MTGJSON gives Through the Omenpaths' double-faced cards' printed names so: printedName and facePrintedName both the
        // face's, no flavorName or faceFlavorName
        printed?.let { addProperty("printedName", it); addProperty("facePrintedName", it) }
    }
}
