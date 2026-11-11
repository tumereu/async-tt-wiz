package org.asyncttwiz.storage

/**
 * TRN-053 (owner gallery review 2026-11-06): how an emblem Automatum makes finds its picture in the card records. An
 * emblem knows only its rules text (not the card that made it), so the emblem printing (a token record with layout
 * "emblem") with the same rules text gives the picture ([CatalogRepository.emblemArt]); none, and the emblem shows an
 * emblem icon.
 */
object EmblemArt {
    /** [text] compared without case, reminder text, punctuation or spacing differences. */
    fun normalized(text: String): String = text.lowercase()
        .replace(Regex("""\([^)]*\)"""), " ")
        .replace(Regex("""[^\p{L}\p{N}+/−-]+"""), " ")
        .trim()
}
