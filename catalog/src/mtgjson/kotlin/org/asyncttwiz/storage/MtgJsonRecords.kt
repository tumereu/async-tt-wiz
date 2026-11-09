package org.asyncttwiz.storage

import org.asyncttwiz.protocol.SetBoosterRecipe

// What the MTGJSON import (MtgJsonCatalog) reads, moved here from client-storage with it (docs/specs/web-client.md step 1a);
// the package stays until step 1b removes the app's own MTGJSON download.

data class CatalogMetadata(val date: String, val version: String, val checksum: String = "", val records: Long = 0)

data class CatalogSet(val code: String, val releaseDate: String, val installedAt: Long = 0, val checksum: String = "",
    val name: String? = null, val type: String? = null)

/** Minimal provider snapshot retains exact display strings, defaults and explicit face identities. */
data class CatalogPrinting(val id: String, val name: String, val set: String, val number: String,
    val language: String, val side: String, val scryfallId: String?, val token: Boolean,
    val otherFaces: List<String>, val json: String, val releaseDate: String = "")

/** Search-only provider data stored beside a printing: lower-case rarity, colour identity mask, legal-format bits
 * (CardSearchRules.FORMATS order, legal or restricted), and its related tokens ([related]: a card's token printings'
 * ids; a token's names of the cards that make it, lower case), which Automatum's token pictures use. */
data class CatalogPrintingTraits(val rarity: String, val identity: Int?, val legal: Long,
    val related: List<String> = emptyList())

data class CatalogSetList(val metadata: CatalogMetadata, val sets: List<CatalogSet>)

/** Where [MtgJsonCatalog.import] puts what it reads: the app's catalog index, or the catalog build. */
interface MtgJsonCatalogSink {
    /** [traits] parallels [records]; missing entries leave the search traits unknown. */
    fun addBatch(records: List<CatalogPrinting>, traits: List<CatalogPrintingTraits?>)
    fun addSet(code: String, releaseDate: String, checksum: String = "")
    fun setName(code: String, name: String)
    fun setType(code: String, type: String)

    /** Whether the import reads each set's booster recipes ([recipes]); the app's import skips them. */
    val readsBoosters: Boolean get() = false

    /** Set [code]'s booster recipes by kind (play, draft, default...), or the reason they couldn't be read. */
    fun recipes(code: String, recipes: Result<Map<String, SetBoosterRecipe>>) {}
}

/** MTGJSON escapes Windows reserved filenames (CON is the Conflux set). Never accept a path. */
fun catalogSetFilename(code: String): String {
    require(Regex("[A-Z0-9]{1,16}").matches(code))
    return if (code in setOf("CON", "PRN", "AUX", "NUL") || Regex("(?:COM|LPT)[1-9]").matches(code)) "${code}_" else code
}
