package org.asyncttwiz.storage

import org.asyncttwiz.protocol.SetBoosterRecipe

// The card records the MTGJSON import (MtgJsonCatalog) reads and the app's card records index keeps: the catalog build
// reads MTGJSON with them, and the app indexes the server's card catalog into the same records (CatalogInstaller).

/** The installed card records: the MTGJSON build they come from, the card catalog's checksum, how many printings. */
data class CatalogMetadata(val date: String, val version: String, val checksum: String = "", val records: Long = 0)

/** Minimal provider snapshot retains exact display strings, defaults and explicit face identities. */
data class CatalogPrinting(val id: String, val name: String, val set: String, val number: String,
    val language: String, val side: String, val scryfallId: String?, val token: Boolean,
    val otherFaces: List<String>, val json: String, val releaseDate: String = "")

/** Search-only provider data stored beside a printing: lower-case rarity, colour identity mask, legal-format bits
 * (CardSearchRules.FORMATS order, legal or restricted), and its related tokens ([related]: a card's token printings'
 * ids; a token's names of the cards that make it, lower case), which Automatum's token pictures use. [printedText]
 * (TBL-209, owner 2026-11-21): the text the printing prints where it differs from the Oracle text only in reminder text
 * (MTGJSON's `originalText`: a retro frame leaving it out, a core set printing adding it), else empty; the catalog keeps it
 * on the printing, for the rules reminders of what the printing shows. [printedFaceName] (LOB-150): the name a face of a card
 * of several faces prints where it is another than the face's Oracle name and MTGJSON gives it only as `facePrintedName`,
 * without a `faceFlavorName` (Through the Omenpaths' double-faced cards), else empty; the catalog keeps it as the face's
 * printed name. */
data class CatalogPrintingTraits(val rarity: String, val identity: Int?, val legal: Long,
    val related: List<String> = emptyList(), val printedText: String = "", val printedFaceName: String = "")

/** Where [MtgJsonCatalog.import] puts what it reads: the app's card records index (test fixtures), or the catalog build. */
interface MtgJsonCatalogSink {
    /** [traits] parallels [records]; missing entries leave the search traits unknown. */
    fun addBatch(records: List<CatalogPrinting>, traits: List<CatalogPrintingTraits?>)
    fun addSet(code: String, releaseDate: String, checksum: String = "")
    fun setName(code: String, name: String)
    fun setType(code: String, type: String)
    /** Set [code]'s MTGJSON `keyruneCode`: which Keyrune set symbol it shows (DRF-088). */
    fun setKeyrune(code: String, keyrune: String) {}

    /** Whether the import reads each set's booster recipes ([recipes]); the app's index skips them. */
    val readsBoosters: Boolean get() = false

    /** Set [code]'s booster recipes by kind (play, draft, default...), or the reason they couldn't be read. */
    fun recipes(code: String, recipes: Result<Map<String, SetBoosterRecipe>>) {}
}
