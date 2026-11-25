package org.asyncttwiz.catalog

import arcanum.engine.CardSupport
import arcanum.parser.OracleParser
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import org.asyncttwiz.automatum.AutomatumSupport
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardSearchRules
import org.asyncttwiz.protocol.DraftColor
import org.asyncttwiz.protocol.SetBoosterRecipe
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.OutsideCards
import org.asyncttwiz.protocol.selectSetBoosterRecipe
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.CatalogPrintingTraits
import org.asyncttwiz.storage.MtgJsonCatalog
import org.asyncttwiz.storage.MtgJsonCatalogSink

/**
 * Builds the trimmed card catalog (SYS-081) from an MTGJSON AllPrintings file, streamed with
 * the app's own import ([MtgJsonCatalog]): every English printing, its face shared with the card's other printings, the
 * set list, and the booster recipe of each draftable set. The support marks (SYS-082): a card is supported when a
 * printing of it is in one of [supportedSets] or its name is on [supportedCards] (the backlog's "support moves set by
 * set"), and Automatum finds nothing in it left to the players ([AutomatumSupport.handParts], on the card data a device
 * makes of it); a draftable set is supported when every card its packs can hold is. [ratings] (the draft bots' card
 * ratings, [CatalogRatings.table] of each set, by 17Lands' card names) go into their sets' records under the catalog's card
 * names ([CatalogSetRecord.ratings]), and a set [borrowed] names without ratings of its own gets another's. Nothing here reads the clock or the machine: the same file and lists give the same
 * catalog.
 */
class CatalogBuilder(
    private val supportedSets: Set<String>,
    private val supportedCards: Set<String>,
    private val ratings: Map<String, Map<String, Int>> = emptyMap(),
    private val borrowed: Map<String, String> = CatalogRatings.BORROWED,
    private val log: (String) -> Unit = {},
) {
    /** A card the lists name ([sets]: its listed sets; [onList]: on the single-card list) and what Automatum leaves of it
     * to the players ([parts], empty: supported). */
    data class Listed(val name: String, val sets: List<String>, val onList: Boolean, val parts: List<String>)

    /** The catalog and the cards the lists name, for the build's report. [unknownNames]: names on the single-card list
     * no card in the file has; [missingSets]: listed sets the file doesn't have. */
    class Built(val catalog: CardCatalog, val listed: List<Listed>, val unknownNames: List<String>, val missingSets: List<String>)

    private class SetInfo(val code: String) { var name = code; var type = ""; var released = ""; var keyrune = "" }

    private class Collector : MtgJsonCatalogSink {
        val printings = ArrayList<Pair<CatalogPrinting, CatalogPrintingTraits?>>()
        val sets = LinkedHashMap<String, SetInfo>()
        val recipes = HashMap<String, SetBoosterRecipe>()
        val recipeFailures = sortedMapOf<String, String>()
        override fun addBatch(records: List<CatalogPrinting>, traits: List<CatalogPrintingTraits?>) {
            records.forEachIndexed { index, record ->
                if (record.language.isEmpty() || record.language == "English") printings += record to traits.getOrNull(index)
            }
        }
        override fun addSet(code: String, releaseDate: String, checksum: String) { sets.getOrPut(code) { SetInfo(code) }.released = releaseDate }
        override fun setName(code: String, name: String) { sets.getOrPut(code) { SetInfo(code) }.name = name }
        override fun setType(code: String, type: String) { sets.getOrPut(code) { SetInfo(code) }.type = type }
        override fun setKeyrune(code: String, keyrune: String) { sets.getOrPut(code) { SetInfo(code) }.keyrune = keyrune }
        override val readsBoosters: Boolean get() = true
        override fun recipes(code: String, recipes: Result<Map<String, SetBoosterRecipe>>) {
            recipes.onSuccess { found -> selectSetBoosterRecipe(found)?.let { this.recipes[code] = it } }
                .onFailure { recipeFailures[code] = it.message ?: it::class.simpleName.orEmpty() }
        }
    }

    /** Builds the catalog from the AllPrintings file [open] gives (uncompressed JSON; opened twice: its meta, then all). */
    fun build(open: () -> InputStream): Built {
        val importer = MtgJsonCatalog()
        val metadata = open().use(importer::peekMetadata)
        log("MTGJSON ${metadata.version} (${metadata.date}): reading")
        val collected = Collector()
        open().use { importer.import(it, collected, metadata) }
        log("Read ${collected.printings.size} English printings in ${collected.sets.size} sets, " +
            "${collected.recipes.size} booster recipes; recipes not read: ${collected.recipeFailures}")

        // Faces, shared by every printing with the same values (deduplicated by content, numbered as first met).
        val faceIndex = HashMap<CatalogFaceRecord, Int>()
        val faces = ArrayList<CatalogFaceRecord>()
        val printedPerFace = HashSet<String>()
        val read = collected.printings.map { (printing, traits) ->
            val json = JsonParser.parseString(printing.json).asJsonObject
            val face = face(printing, json, traits)
            val index = faceIndex.getOrPut(face) { faces += face.copy(i = faces.size); faces.size - 1 }
            val faceFlavorName = text(json, "faceFlavorName").ifEmpty {
                traits?.printedFaceName.orEmpty().also { if (it.isNotEmpty()) printedPerFace += printing.id }
            }
            CatalogPrintingRecord(printing.id, index, printing.set, printing.number, printing.scryfallId,
                traits?.rarity.orEmpty(), printing.token, printing.otherFaces, strings(json.get("availability")),
                flag(json, "isOnlineOnly"), flag(json, "isPromo"),
                tokens = if (printing.token) emptyList() else traits?.related.orEmpty(),
                makers = if (printing.token) traits?.related.orEmpty() else emptyList(),
                flavorName = text(json, "flavorName"), faceFlavorName = faceFlavorName,
                printedText = traits?.printedText.orEmpty())
        }
        // LOB-150: a card of several faces whose printed names MTGJSON gives only per face (facePrintedName: Through the
        // Omenpaths' double-faced cards) is named by them whole too, as a flavorName names one ("Front // Back").
        val byId = read.associateBy { it.id }
        val printings = read.map { printing ->
            if (printing.id !in printedPerFace || printing.flavorName.isNotEmpty()) return@map printing
            val sides = (listOf(printing) + printing.others.mapNotNull(byId::get)).sortedBy { faces[it.face].side }
            if (sides.any { it.faceFlavorName.isEmpty() }) printing
            else printing.copy(flavorName = sides.joinToString(" // ") { it.faceFlavorName })
        }
        if (printedPerFace.isNotEmpty()) log("Faces printed under another name only per face: ${printedPerFace.size}")
        val draftTypes = setOf("expansion", "core", "masters", "draft_innovation")
        val recipes = collected.recipes.filterKeys { collected.sets[it]?.type in draftTypes }.toSortedMap()
            .map { (code, recipe) -> CatalogRecipeRecord(code, recipe) }
        val setRecords = collected.sets.values.sortedBy { it.code }.map { info ->
            CatalogSetRecord(info.code, info.name, info.type, info.released, info.keyrune, draftable = info.type in draftTypes)
        }
        val header = CatalogHeader(CardCatalogFormat.FORMAT, metadata.version, metadata.date, CardSearchRules.FORMATS,
            OracleParser.VERSION, CardSupport.SUPPORT_VERSION, setRecords.size, faces.size, printings.size, recipes.size)
        val unmarked = CardCatalog(header, setRecords, faces, printings, recipes)
        return mark(unmarked)
    }

    /** The support marks (SYS-082) on the cards and the draftable sets. */
    private fun mark(catalog: CardCatalog): Built {
        val dungeons = CatalogDungeons.provided(CatalogDungeons.definitions(catalog))
        log("Dungeons Automatum plays fully: $dungeons")
        // A reversible printing counts as the card it shows (CatalogCards.cardName).
        val cards = catalog.printings.filter { !it.token }.groupBy { CatalogCards.cardName(catalog.faces[it.face]) }
        val frontNames = HashMap<String, String>()
        cards.forEach { (name, printings) ->
            printings.map { catalog.faces[it.face] }.firstOrNull { it.side == "a" }?.faceName?.takeIf { it.isNotEmpty() }
                ?.let { frontNames[it] = name }
        }
        val known = cards.keys + frontNames.keys
        val listed = ArrayList<Listed>()
        val supported = HashSet<String>()
        for ((name, printings) in cards) {
            val sets = printings.map { it.set }.filter { it in supportedSets }.distinct().sorted()
            val front = printings.map { catalog.faces[it.face] }.firstOrNull { it.side == "a" }?.faceName.orEmpty()
            // A card of several faces is listed by its whole name or its front face's, unless that names a card of its
            // own.
            val onList = name in supportedCards || (front.isNotEmpty() && front in supportedCards && front !in cards)
            if (sets.isEmpty() && !onList) continue
            val ordinary = printings.sortedBy { CatalogCards.reversible(catalog.faces[it.face]) }
            val printing = ordinary.firstOrNull { it.set in supportedSets } ?: ordinary.first()
            val parts = handParts(catalog, printing, dungeons)
            listed += Listed(name, sets, onList, parts)
            if (parts.isEmpty()) supported += name
        }
        log("Listed cards: ${listed.size}, supported ${supported.size}")
        val faces = catalog.faces.map { if (CatalogCards.cardName(it) in supported) it.copy(automatum = true) else it }
        // Packs leave out unsupported bonus sheets from other sets (CatalogPackSheets).
        val recipes = catalog.recipes.map { record ->
            val kept = CatalogPackSheets.kept(catalog, record.set, record.recipe) { CatalogCards.cardName(catalog.faces[it.face]) in supported }
            if (kept !== record.recipe) log("Set ${record.set}: packs leave out sheets ${record.recipe.sheets.keys - kept.sheets.keys}")
            CatalogRecipeRecord(record.set, kept)
        }
        val packed = CardCatalog(catalog.header, catalog.sets, catalog.faces, catalog.printings, recipes)
        val sets = catalog.sets.map { set ->
            if (!set.draftable) return@map set
            val names = packCards(packed, set.code).map { CatalogCards.cardName(catalog.faces[it.face]) }.toSortedSet()
            val unsupported = names.filter { it !in supported }
            set.copy(automatum = names.isNotEmpty() && unsupported.isEmpty(), unsupported = unsupported)
        }
        val marked = CardCatalog(catalog.header, rated(catalog, sets), faces, catalog.printings, recipes)
        return Built(marked, listed.sortedBy { it.name }, supportedCards.filter { it !in known }.sorted(),
            supportedSets.filter { it !in marked.set }.sorted())
    }

    /**
     * [sets] with their card ratings: each rated card under the catalog's name of its card, found (case ignored) first by
     * the name the set's own printings print where it isn't the Oracle name (`flavorName`, a front face's
     * `faceFlavorName`: Through the Omenpaths names Marvel's Spider-Man's cards so, and 17Lands with it), then by any
     * card's whole name or front face's (17Lands names a card of several faces by its front), then, where 17Lands' data lost
     * a character to a "?", by the one card of the set it fits ([lostCharacters]); a name no card has is left out (logged).
     * A set without ratings of its own that [borrowed] names gets its source's ratings of the cards it shares.
     */
    private fun rated(catalog: CardCatalog, sets: List<CatalogSetRecord>): List<CatalogSetRecord> {
        if (ratings.isEmpty()) return sets
        val names = HashMap<String, String>()
        catalog.printings.filter { !it.token }.forEach { printing ->
            val face = catalog.faces[printing.face]
            val name = CatalogCards.cardName(face)
            names.putIfAbsent(name.lowercase(), name)
            if (face.side == "a" && face.faceName.isNotEmpty()) names.putIfAbsent(face.faceName.lowercase(), name)
        }
        fun printed(code: String): Map<String, String> {
            val printed = HashMap<String, String>()
            catalog.bySet[code].orEmpty().filter { !it.token }.forEach { printing ->
                val face = catalog.faces[printing.face]
                val name = CatalogCards.cardName(face)
                if (printing.flavorName.isNotEmpty()) printed.putIfAbsent(printing.flavorName.lowercase(), name)
                if (face.side == "a" && printing.faceFlavorName.isNotEmpty()) printed.putIfAbsent(printing.faceFlavorName.lowercase(), name)
            }
            return printed
        }
        val unknown = ratings.keys - sets.map { it.code }.toSet()
        if (unknown.isNotEmpty()) log("Card ratings of sets the catalog doesn't have: ${unknown.sorted().joinToString(" ")}")
        val own = sets.map { set ->
            val table = ratings[set.code] ?: return@map set
            val setNames = printed(set.code)
            val found = sortedMapOf<String, Int>()
            val missing = ArrayList<String>()
            val guessed = ArrayList<String>()
            table.forEach { (name, rating) ->
                (setNames[name.lowercase()] ?: names[name.lowercase()] ?: lostCharacters(catalog, set.code, setNames, name)
                    ?.also { guessed += "$name = $it" })?.let { found[it] = rating } ?: run { missing += name }
            }
            log("Set ${set.code}: ${found.size} cards rated" +
                (if (guessed.isEmpty()) "" else "; found despite lost characters: ${guessed.sorted().joinToString(" | ")}") +
                if (missing.isEmpty()) "" else "; ${missing.size} ratings name no card: ${missing.sorted().take(12).joinToString(" | ")}")
            set.copy(ratings = found)
        }
        val byCode = own.associateBy { it.code }
        return own.map { set ->
            val source = borrowed[set.code]?.let(byCode::get)?.takeIf { set.ratings.isEmpty() && it.ratings.isNotEmpty() } ?: return@map set
            val cards = catalog.bySet[set.code].orEmpty().filter { !it.token }.map { CatalogCards.cardName(catalog.faces[it.face]) }.toSet()
            val shared = source.ratings.filterKeys { it in cards }.toSortedMap()
            log("Set ${set.code}: ${shared.size} of its ${cards.size} cards rated by ${source.code}'s ratings")
            set.copy(ratings = shared)
        }
    }

    /**
     * The catalog's name of the card of set [code] that the 17Lands name [name] means where its data lost characters, each
     * replaced by a "?" ("Bespoke B?" for a name with an accented letter): each "?" stands for any one character, matched
     * (case ignored) against the set's own cards' names (whole, front face, and the names [printed] by the set); null when
     * the name has no "?" or not exactly one card fits.
     */
    private fun lostCharacters(catalog: CardCatalog, code: String, printed: Map<String, String>, name: String): String? {
        if ('?' !in name) return null
        val pattern = Regex(name.split('?').joinToString(".") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        val candidates = HashMap<String, String>(printed)
        catalog.bySet[code].orEmpty().filter { !it.token }.forEach { printing ->
            val face = catalog.faces[printing.face]
            val card = CatalogCards.cardName(face)
            candidates.putIfAbsent(card.lowercase(), card)
            if (face.side == "a" && face.faceName.isNotEmpty()) candidates.putIfAbsent(face.faceName.lowercase(), card)
        }
        return candidates.filterKeys { pattern.matches(it) }.values.distinct().singleOrNull()
    }

    /** The cards a set's packs can hold: its recipe's printings, or without one every card of the set (DRF-090's
     * approximate packs); never tokens or meld results. */
    private fun packCards(catalog: CardCatalog, code: String): List<CatalogPrintingRecord> {
        val recipe = catalog.recipe[code]
        val printings = if (recipe != null) recipe.sheets.values.flatMap { it.cards.keys }.distinct().mapNotNull { catalog.printing[it] }
            else catalog.bySet[code].orEmpty()
        return printings.filter { !it.token && !CatalogCards.meldResult(catalog, it, CatalogCards.linked(catalog, it)) }
    }

    /** What Automatum leaves to the players of the card [printing] is a face of, as a device works it out
     * (client-storage's CatalogSetSupport: the card data a deck would hold, parsed as a game parses it). */
    private fun handParts(catalog: CardCatalog, printing: CatalogPrintingRecord, dungeons: Set<OutsideCards.Need>): List<String> {
        val definition: CardDefinition = try {
            CatalogCards.definition(catalog, printing)
        } catch (e: RuntimeException) {
            return listOf("unreadable card data: ${e.message ?: e::class.simpleName}")
        }
        return try {
            when (val printed = GameCards.printed(definition)) {
                is GameCards.Printed.Card -> AutomatumSupport.handParts(OracleParser.parseCard(printed.layout, printed.faces), dungeons)
                is GameCards.Printed.Unreadable -> listOf("unreadable: ${printed.problem}")
            }
        } catch (e: RuntimeException) {
            listOf("unreadable: ${e.message ?: e::class.simpleName}") // The parser throws on printed values it can't read.
        }
    }

    private fun face(printing: CatalogPrinting, json: JsonObject, traits: CatalogPrintingTraits?): CatalogFaceRecord {
        fun text(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val identity = traits?.identity ?: 0
        return CatalogFaceRecord(0, printing.name, text("faceName"), text("layout").ifEmpty { "normal" }, printing.side,
            text("type"), text("manaCost"), text("text"), text("power"), text("toughness"), text("loyalty"), text("defense"),
            strings(json.get("colors")),
            DraftColor.entries.filter { identity and (1 shl it.ordinal) != 0 }.map { it.name },
            traits?.legal ?: 0L,
            json.getAsJsonObject("identifiers")?.get("scryfallOracleId")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
    }

    private fun strings(element: JsonElement?): List<String> = element?.takeIf { it.isJsonArray }?.asJsonArray
        ?.filter { it.isJsonPrimitive }?.map { it.asString }.orEmpty()

    private fun text(json: JsonObject, key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun flag(json: JsonObject, key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
}
