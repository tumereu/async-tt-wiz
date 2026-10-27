package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Resolve once on the pod worker, never on the UI thread. Freeze catalog artwork and card data. */
internal fun podLibrarianDefinition(repository: CatalogRepository): SubmittedDefinition {
    val printing = repository.find("Cogwork Librarian", limit = 1000)
        .filter { it.name == "Cogwork Librarian" && !it.token && it.language in listOf("English", "en") }
        .minWithOrNull(compareBy<CatalogPrinting> { if (it.set.equals("CNS", true)) 0 else 1 }.thenBy { it.id })
        ?: throw PodFailure("librarian_definition_unavailable")
    val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), emptyList()).definition(null)
    return SubmittedDefinition(DeckJson.digest(definition), definition)
}
