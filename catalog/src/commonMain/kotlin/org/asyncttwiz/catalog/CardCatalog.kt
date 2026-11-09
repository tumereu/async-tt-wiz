package org.asyncttwiz.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.SetBoosterRecipe

/**
 * The trimmed card catalog (docs/specs/web-client.md section 3): what the clients and the server read of the pinned
 * MTGJSON file, built at release. The file is JSON lines (one [CatalogRecord] per line), gzip-compressed: a
 * [CatalogHeader] first, then the sets, the card faces, the printings and the booster recipes, each group in a fixed
 * order, so the same input always gives the same bytes. Its version is the SHA-256 of the uncompressed lines.
 */
@Serializable
sealed interface CatalogRecord

/** The first line: the format, the MTGJSON build it was made from, what the support marks were worked out with, and how
 * many records of each kind follow. [formats] names the bits of [CatalogFaceRecord.legal], lowest first. */
@Serializable @SerialName("catalog")
data class CatalogHeader(
    val format: Int,
    val mtgjsonVersion: String,
    val mtgjsonDate: String,
    val formats: List<String>,
    /** The parser's and the rules engine's support versions the marks ([CatalogFaceRecord.automatum]) were made with. */
    val parserVersion: Int,
    val engineVersion: Int,
    val sets: Int,
    val faces: Int,
    val printings: Int,
    val recipes: Int,
) : CatalogRecord

/** A set (DRF-088's set list). [draftable]: a kind of set the Sets tab lists (expansion, core, masters, draft
 * innovation). [automatum] (section 3.8): a draftable set Automatum can play every card of its packs; [unsupported] the
 * names of its pack cards it can't, sorted (DRF-161's hold). */
@Serializable @SerialName("set")
data class CatalogSetRecord(
    val code: String,
    val name: String,
    val type: String = "",
    val released: String = "",
    val draftable: Boolean = false,
    val automatum: Boolean = false,
    val unsupported: List<String> = emptyList(),
) : CatalogRecord

/**
 * One card face, shared by every printing that has it ([i] is what printings name it by). [name] is the whole card's
 * name; [faceName] this face's own, for a card of several faces. [legal]: legal or restricted formats as bits of
 * [CatalogHeader.formats]; [identity] the card's colour identity; [oracle] its oracle id. [automatum] (section 3.8): the
 * card is supported, by the "support moves set by set" rule (a printing in a listed set, or on the single-card list) and
 * with nothing Automatum leaves to the players.
 */
@Serializable @SerialName("face")
data class CatalogFaceRecord(
    val i: Int,
    val name: String,
    val faceName: String = "",
    val layout: String = "normal",
    val side: String = "",
    val type: String = "",
    val manaCost: String = "",
    val text: String = "",
    val power: String = "",
    val toughness: String = "",
    val loyalty: String = "",
    val defense: String = "",
    val colors: List<String> = emptyList(),
    val identity: List<String> = emptyList(),
    val legal: Long = 0,
    val oracle: String = "",
    val automatum: Boolean = false,
) : CatalogRecord

/** One face of one English printing: its MTGJSON id, its [face], set and collector number, the Scryfall id for its
 * picture, rarity, the ids of the printing's other faces ([others]), and for tokens (LOB-125): a card's tokens' printing
 * ids ([tokens]), a token's lower-case names of the cards that make it ([makers]). */
@Serializable @SerialName("printing")
data class CatalogPrintingRecord(
    val id: String,
    val face: Int,
    val set: String,
    val number: String,
    val scryfall: String? = null,
    val rarity: String = "",
    val token: Boolean = false,
    val others: List<String> = emptyList(),
    val availability: List<String> = emptyList(),
    val onlineOnly: Boolean = false,
    val promo: Boolean = false,
    val tokens: List<String> = emptyList(),
    val makers: List<String> = emptyList(),
) : CatalogRecord

/** The booster recipe a draftable set's packs follow (DRF-090: Play, else Draft, else default), with the printings it names. */
@Serializable @SerialName("recipe")
data class CatalogRecipeRecord(val set: String, val recipe: SetBoosterRecipe) : CatalogRecord

/** A whole catalog in memory, as [CardCatalogFormat.read] gives it. */
class CardCatalog(
    val header: CatalogHeader,
    val sets: List<CatalogSetRecord>,
    val faces: List<CatalogFaceRecord>,
    val printings: List<CatalogPrintingRecord>,
    val recipes: List<CatalogRecipeRecord>,
) {
    val set: Map<String, CatalogSetRecord> by lazy { sets.associateBy { it.code } }
    val printing: Map<String, CatalogPrintingRecord> by lazy { printings.associateBy { it.id } }
    val recipe: Map<String, SetBoosterRecipe> by lazy { recipes.associate { it.set to it.recipe } }
    val bySet: Map<String, List<CatalogPrintingRecord>> by lazy { printings.groupBy { it.set } }

    /** The records of this catalog, in file order. */
    fun records(): Sequence<CatalogRecord> = sequenceOf<CatalogRecord>(header) + sets.asSequence() + faces.asSequence() +
        printings.asSequence() + recipes.asSequence()
}

/** Reading and writing catalog lines. */
object CardCatalogFormat {
    /** The format of [CatalogHeader.format]; a change makes clients download the catalog again (with a new version). */
    const val FORMAT = 1

    val json = Json {
        classDiscriminator = "t"
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(record: CatalogRecord): String = json.encodeToString(CatalogRecord.serializer(), record)

    fun decode(line: String): CatalogRecord = json.decodeFromString(CatalogRecord.serializer(), line)

    /** A catalog from its lines (uncompressed); fails on a file that isn't one whole catalog of this [FORMAT]. */
    fun read(lines: Sequence<String>): CardCatalog {
        var header: CatalogHeader? = null
        val sets = ArrayList<CatalogSetRecord>()
        val faces = ArrayList<CatalogFaceRecord>()
        val printings = ArrayList<CatalogPrintingRecord>()
        val recipes = ArrayList<CatalogRecipeRecord>()
        for (line in lines) {
            if (line.isEmpty()) continue
            when (val record = decode(line)) {
                is CatalogHeader -> {
                    check(header == null) { "Two catalog headers" }
                    check(record.format == FORMAT) { "Catalog format ${record.format}, expected $FORMAT" }
                    header = record
                }
                is CatalogSetRecord -> sets += record
                is CatalogFaceRecord -> { check(record.i == faces.size) { "Face ${record.i} out of order" }; faces += record }
                is CatalogPrintingRecord -> printings += record
                is CatalogRecipeRecord -> recipes += record
            }
        }
        val read = checkNotNull(header) { "No catalog header" }
        check(read.sets == sets.size && read.faces == faces.size && read.printings == printings.size && read.recipes == recipes.size) {
            "Catalog incomplete: header says ${read.sets}/${read.faces}/${read.printings}/${read.recipes}, " +
                "read ${sets.size}/${faces.size}/${printings.size}/${recipes.size}"
        }
        check(printings.all { it.face in faces.indices }) { "A printing names a face the catalog doesn't have" }
        return CardCatalog(read, sets, faces, printings, recipes)
    }
}

/** What the server says of its current catalog (GET /v1/catalog): its version ([checksum], the SHA-256 of the
 * uncompressed lines), the gzip file's [size] in bytes, the MTGJSON build it was made from, and its record counts. The
 * file itself is GET /v1/catalog/{checksum}, cached for good. */
@Serializable
data class CatalogVersion(
    val checksum: String,
    val size: Long,
    val mtgjsonVersion: String,
    val mtgjsonDate: String,
    val sets: Int,
    val faces: Int,
    val printings: Int,
)
