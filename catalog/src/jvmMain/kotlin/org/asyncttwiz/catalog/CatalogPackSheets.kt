package org.asyncttwiz.catalog

import org.asyncttwiz.protocol.SetBoosterRecipe

/**
 * Bonus sheets (choice made 2026-11-10, waiting for the owner): most sets' booster recipes hold a bonus or special-guest
 * sheet of cards from other sets (The List, special guests). A set's packs leave out each sheet that draws from outside
 * the set's own cards and holds a card Automatum doesn't support, and fill its slots from the booster's main sheet (the
 * kept sheet it draws most cards from: the commons in practice), so such a sheet alone never makes a set undraftable.
 * Worked out at catalog build: the catalog's recipe is the one packs follow.
 */
object CatalogPackSheets {
    /** Set [code]'s [recipe] without its unsupported foreign sheets, or [recipe] itself when none is left out or the
     * main sheet couldn't hold the slots. [supported]: whether a printing's card is supported. */
    fun kept(catalog: CardCatalog, code: String, recipe: SetBoosterRecipe, supported: (CatalogPrintingRecord) -> Boolean): SetBoosterRecipe {
        val dropped = recipe.sheets.filterValues { sheet ->
            val printings = sheet.cards.keys.mapNotNull { catalog.printing[it] }
            printings.any { it.set != code } && printings.any { !it.token && !supported(it) }
        }.keys
        if (dropped.isEmpty() || dropped.size == recipe.sheets.size) return recipe
        val sheets = recipe.sheets.filterKeys { it !in dropped }
        val overall = recipe.boosters.flatMap { it.contents.entries }.filter { it.key !in dropped }
            .groupBy({ it.key }, { it.value.toLong() }).maxByOrNull { it.value.sum() }?.key ?: return recipe
        val boosters = recipe.boosters.map { booster ->
            val removed = booster.contents.filterKeys { it in dropped }.values.sum()
            if (removed == 0) return@map booster
            val contents = booster.contents.filterKeys { it !in dropped }.toMutableMap()
            val main = contents.maxByOrNull { it.value }?.key ?: overall
            contents[main] = (contents[main] ?: 0) + removed
            val sheet = sheets.getValue(main)
            val count = contents.getValue(main)
            if (if (sheet.fixed) count > sheet.totalWeight else !sheet.allowDuplicates && count > sheet.cards.size) return recipe
            booster.copy(contents = contents)
        }
        return recipe.copy(boosters = boosters, sheets = sheets)
    }
}
