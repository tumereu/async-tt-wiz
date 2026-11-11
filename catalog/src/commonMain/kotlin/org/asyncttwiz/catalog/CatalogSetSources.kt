package org.asyncttwiz.catalog

import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.DraftColor
import org.asyncttwiz.protocol.SetBoosterRecipe
import org.asyncttwiz.protocol.SetCardRarity
import org.asyncttwiz.protocol.SetSourceCard
import org.asyncttwiz.protocol.SetSourceIssue
import org.asyncttwiz.protocol.SetSourceSnapshot
import org.asyncttwiz.protocol.SetSourceValidation
import org.asyncttwiz.protocol.SubmittedDefinition

/** A draft set as a pod's source, from the card catalog: its snapshot (the cards packs are drawn from, its recipe) and
 * the card data of every card in it. [missing] counts the recipe's printings the catalog doesn't have (left out). */
class CatalogSetSource(val snapshot: SetSourceSnapshot, val definitions: List<SubmittedDefinition>, val missing: Int)

/**
 * Set packs from the card catalog (docs/specs/web-client.md section 3.7): every card of the set, and the cards of other
 * sets its recipe names; a meld result is never a card of its own; printings the catalog doesn't have are left out of the
 * recipe, and a recipe that no longer holds together without them is dropped (approximate packs, DRF-090). Any set the
 * catalog has can be built.
 */
object CatalogSetSources {
    /** Set [code]'s source, or null when the catalog has no usable cards of it. Throws when a card's data isn't valid. */
    fun build(catalog: CardCatalog, code: String): CatalogSetSource? {
        val set = catalog.set[code] ?: return null
        val recipe = catalog.recipe[code]
        val referenced = recipe?.sheets?.values?.flatMap { it.cards.keys }?.toSet().orEmpty()
        val sources = LinkedHashMap<String, CatalogPrintingRecord>()
        catalog.bySet[code].orEmpty().filter { !it.token }.forEach { sources.getOrPut(it.id) { it } }
        recipe?.sourceSetCodes.orEmpty().filter { it != code }.forEach { dependency ->
            catalog.bySet[dependency].orEmpty().filter { !it.token && it.id in referenced }.forEach { sources.getOrPut(it.id) { it } }
        }
        val definitions = LinkedHashMap<String, SubmittedDefinition>()
        val byPrinting = HashMap<String, SubmittedDefinition>()
        val cards = sources.values.mapNotNull { printing ->
            val linked = CatalogCards.linked(catalog, printing)
            if (CatalogCards.meldResult(catalog, printing, linked)) return@mapNotNull null
            val submitted = byPrinting[printing.id] ?: run {
                val sides = CatalogCards.physicalSides(catalog, linked)
                val definition = CatalogCards.definition(catalog, sides)
                check(DeckValidation.definition(definition)) {
                    "Card data of ${definition.name} (${printing.id}, ${printing.set} ${printing.number}) isn't valid"
                }
                SubmittedDefinition(DeckJson.digest(definition), definition).also { entry -> sides.forEach { byPrinting[it.id] = entry } }
            }
            definitions[submitted.digest] = submitted
            SetSourceCard(submitted.digest, printing.id, rarity(printing.rarity),
                catalog.faces[printing.face].colors.mapNotNull { color -> DraftColor.entries.firstOrNull { it.name == color } }.toSet())
        }
        if (cards.isEmpty()) return null
        // Both faces of a card can resolve to one physical definition: give them the same whole-card colours.
        val colors = cards.groupBy { it.definition }.mapValues { (_, entries) -> entries.flatMap { it.colors }.toSet() }
        val normalized = cards.map { it.copy(colors = colors.getValue(it.definition)) }
        val available = cards.map { it.uuid }.toSet()
        val missing = referenced.count { it !in sources }
        var kept: SetBoosterRecipe? = recipe?.let { r ->
            r.copy(sheets = r.sheets.mapValues { (_, sheet) ->
                val cardsKept = sheet.cards.filterKeys { it in available }
                sheet.copy(cards = cardsKept, totalWeight = cardsKept.values.sum())
            })
        }
        if (kept != null && validate(SetSourceSnapshot(code, set.name, normalized, kept)).any {
                it in setOf(SetSourceIssue.SHEET, SetSourceIssue.WEIGHT, SetSourceIssue.SHEET_CAPACITY) }) kept = null
        val snapshot = SetSourceSnapshot(code, set.name, normalized, kept)
        val issues = validate(snapshot)
        check(issues.isEmpty()) { "Set source $code isn't valid: $issues" }
        return CatalogSetSource(snapshot, definitions.values.toList(), missing)
    }

    /** A set source's checks. */
    fun validate(snapshot: SetSourceSnapshot): List<SetSourceIssue> = SetSourceValidation.validate(snapshot)

    private fun rarity(value: String): SetCardRarity = when (value) {
        "common" -> SetCardRarity.COMMON
        "uncommon" -> SetCardRarity.UNCOMMON
        "rare" -> SetCardRarity.RARE
        "mythic" -> SetCardRarity.MYTHIC
        "special" -> SetCardRarity.SPECIAL
        "bonus" -> SetCardRarity.BONUS
        else -> SetCardRarity.UNKNOWN
    }
}
