package org.asyncttwiz.storage

import org.asyncttwiz.protocol.CardSearchCard
import org.asyncttwiz.protocol.CubeValidation
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.DefinitionFace

/**
 * The card catalog on this device as the app reads it (Android: [CatalogRepository], over the installed card records;
 * docs/specs/web-client.md §1 item 2). Every read blocks: call on a worker, never the UI thread.
 */
interface CardCatalog {
    /** The installed card records' metadata, null without card records. */
    fun metadata(): CatalogMetadata?

    /** The draft sets (DRF-088) with their support marks, newest first. */
    fun draftableSets(): List<DraftableSet>

    /** LOB-115: which of [printingIds] the card catalog doesn't mark as supported; null without card records. */
    fun unsupported(printingIds: Collection<String>): Set<String>?

    /** Which of the pictures [scryfallIds] are of no printing the card catalog marks as supported; null without card
     * records. */
    fun unsupportedPictures(scryfallIds: Collection<String>): Set<String>?

    /** The cards of [printingIds] as saved decks and cubes hold them, each the faces of the card the printing is a face
     * of, front first; printings the records don't have are left out. */
    fun cards(printingIds: Collection<String>): Map<String, List<CatalogPrinting>>

    /** The token printing a token Automatum makes is drawn with, null for the plain frame or without card records. */
    fun tokenArt(face: DefinitionFace, sourceName: String?, sourcePrintingId: String?): CatalogPrinting?

    /** TRN-053: the scryfall id of the picture of the emblem printing whose rules text is [text], or null. */
    fun emblemArt(text: String): String?

    /** Avatar choices from the installed card records. */
    fun avatarChoices(query: String = "", previous: List<CatalogAvatar> = emptyList()): List<CatalogAvatar>

    /** The installed card records' identity (which generation is installed), null without them. */
    fun tokenGeneration(): String?

    /** Scryfall-style card search over the installed card records; continue with [CatalogCardSearch.Page.next]. */
    fun searchCards(query: String, cursor: CatalogCardCursor? = null, limit: Int = 60, budget: Int = 3000): CatalogCardSearch

    /** The given printings' cards as search knows them, by printing id. */
    fun searchData(printings: Collection<CatalogPrinting>): Map<String, CardSearchCard>
}

/** A deck list's text resolved against the card catalog on this device (Android: [DeckImportResolver]). Blocking. */
interface DeckResolver {
    fun resolve(text: String): DeckImportResult
    fun resolve(parsed: ParsedDeck, maxCards: Int = DeckValidation.MAX_CARDS, allowSubstitutions: Boolean = false): DeckImportResult
}

/** A cube list's text resolved against the card catalog on this device (Android: [CubeImportResolver]). Blocking. */
interface CubeResolver {
    fun resolve(text: String): DeckImportResult
    fun resolve(parsed: ParsedDeck): DeckImportResult
}

/** A cube list's text as a card list: also bare card names, one copy per line (Cube Cobra plaintext export); sideboard
 * lines go to Maybe, as do maybeboard sections. */
fun parseCubeList(text: String): ParsedDeck =
    DeckTextParser.parse(text, CubeValidation.MAX_CARDS, DeckSection.MAYBEBOARD, bareNames = true)
