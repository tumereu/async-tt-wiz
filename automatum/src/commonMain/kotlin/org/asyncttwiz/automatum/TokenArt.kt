package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.DefinitionFace

/**
 * The picture a token Automatum makes is drawn with (integration spec §4.6, tokens): the host's best matching token
 * printing, looked up while the batch is planned. Its Scryfall id goes into the token's ATW card data in the
 * `CreateToken` itself, so both players, undo, redo and branches all show the same picture; the engine's token
 * definition (in the token's ledger) is unchanged. [None] keeps the plain token frame.
 */
fun interface TokenArt {
    /** The Scryfall id of the token printing [token] is drawn with, or null for the plain token frame. */
    fun find(token: TokenArtQuery): String?

    companion object {
        val None = TokenArt { null }
    }
}

/**
 * A token to find a picture for: its one face as ATW shows it ([face]: name, type line, rules text, P/T, colour
 * indicator) and the card whose effect made it, by its card name ([sourceName]) and its catalog printing
 * ([sourcePrintingId], when the game's card data names one).
 */
@kotlinx.serialization.Serializable
data class TokenArtQuery(val face: DefinitionFace, val sourceName: String? = null, val sourcePrintingId: String? = null)
