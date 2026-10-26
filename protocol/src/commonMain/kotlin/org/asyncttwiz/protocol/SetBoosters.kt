package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SetCardRarity {
    @SerialName("common") COMMON,
    @SerialName("uncommon") UNCOMMON,
    @SerialName("rare") RARE,
    @SerialName("mythic") MYTHIC,
    @SerialName("special") SPECIAL,
    @SerialName("bonus") BONUS,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
data class SetSourceCard(
    val definition: String,
    val uuid: String,
    val rarity: SetCardRarity = SetCardRarity.UNKNOWN,
    val colors: Set<DraftColor> = emptySet(),
)

/** Includes cards from other sets when referenced by the selected recipe. No catalog is needed to draw packs. */
@Serializable
data class SetSourceSnapshot(
    val setCode: String,
    val setName: String,
    val cards: List<SetSourceCard>,
    val recipe: SetBoosterRecipe? = null,
) {
    fun validate(): List<SetSourceIssue> = SetSourceValidation.validate(this)
}

/** MTGJSON BoosterConfig shape. Totals are checked, never trusted as sampling bounds. */
@Serializable
data class SetBoosterRecipe(
    val boosters: List<SetBoosterComposition>,
    val sheets: Map<String, SetBoosterSheet>,
    val boostersTotalWeight: Long = boosters.sumOf { it.weight.toLong() },
    val name: String? = null,
    val sourceSetCodes: List<String> = emptyList(),
)

@Serializable
data class SetBoosterComposition(val contents: Map<String, Int>, val weight: Int)

@Serializable
data class SetBoosterSheet(
    /** Map order is significant for fixed sheets; other sheets are sampled in canonical UUID order. */
    val cards: Map<String, Int>,
    val foil: Boolean = false,
    val balanceColors: Boolean = false,
    val allowDuplicates: Boolean = false,
    val fixed: Boolean = false,
    val totalWeight: Long = cards.values.sumOf { it.toLong() },
)

/** The client parses the set's booster map and chooses before uploading its snapshot. */
fun selectSetBoosterRecipe(booster: Map<String, SetBoosterRecipe>): SetBoosterRecipe? =
    listOf("play", "draft", "default").firstNotNullOfOrNull { booster[it] }

@Serializable enum class SetSourceIssue {
    SET_CODE, SET_NAME, CARDS, CARD, DUPLICATE_UUID, DEFINITION_COLORS,
    RECIPE_SIZE, COMPOSITION, SHEET, WEIGHT, UNKNOWN_CARD, SHEET_CAPACITY,
}

object SetSourceValidation {
    const val MAX_CARDS = 5000
    const val MAX_SHEETS = 100
    const val MAX_COMPOSITIONS = 1000
    const val MAX_PACK_SIZE = 1000

    fun validate(source: SetSourceSnapshot): List<SetSourceIssue> = buildSet {
        fun text(value: String, max: Int) = value.isNotBlank() && value.length <= max && DeckValidation.text(value)
        if (!text(source.setCode, 32)) add(SetSourceIssue.SET_CODE)
        if (!text(source.setName, 256)) add(SetSourceIssue.SET_NAME)
        if (source.cards.size !in 1..MAX_CARDS) {
            add(SetSourceIssue.CARDS)
            return@buildSet
        }
        if (source.cards.any { !DeckValidation.digest(it.definition) || !DeckValidation.uuid(it.uuid) })
            add(SetSourceIssue.CARD)
        val cards = source.cards.associateBy { it.uuid }
        if (cards.size != source.cards.size) add(SetSourceIssue.DUPLICATE_UUID)
        if (source.cards.groupBy { it.definition }.values.any { entries -> entries.any { it.colors != entries.first().colors } })
            add(SetSourceIssue.DEFINITION_COLORS)
        val recipe = source.recipe ?: return@buildSet
        if (recipe.boosters.size !in 1..MAX_COMPOSITIONS || recipe.sheets.size !in 1..MAX_SHEETS ||
            recipe.sourceSetCodes.size > MAX_SHEETS) {
            add(SetSourceIssue.RECIPE_SIZE)
            return@buildSet
        }
        if ((recipe.name != null && !text(recipe.name, 256)) || recipe.sourceSetCodes.any { !text(it, 32) })
            add(SetSourceIssue.RECIPE_SIZE)
        fun weights(values: Collection<Int>, declared: Long): Boolean {
            val sum = values.sumOf { it.toLong() }
            return values.all { it > 0 } && sum in 1..Int.MAX_VALUE.toLong() && declared == sum
        }
        if (!weights(recipe.boosters.map { it.weight }, recipe.boostersTotalWeight)) add(SetSourceIssue.WEIGHT)
        for ((name, sheet) in recipe.sheets) {
            if (!text(name, 100) || sheet.cards.size !in 1..MAX_CARDS) {
                add(SetSourceIssue.SHEET)
                continue
            }
            if (!weights(sheet.cards.values, sheet.totalWeight)) add(SetSourceIssue.WEIGHT)
            if (sheet.cards.keys.any { it !in cards }) add(SetSourceIssue.UNKNOWN_CARD)
        }
        for (booster in recipe.boosters) {
            if (booster.contents.size !in 1..MAX_SHEETS || booster.contents.values.any { it <= 0 } ||
                booster.contents.values.sumOf { it.toLong() } !in 1..MAX_PACK_SIZE.toLong())
                add(SetSourceIssue.COMPOSITION)
            for ((name, count) in booster.contents) {
                val sheet = recipe.sheets[name]
                if (sheet == null) add(SetSourceIssue.SHEET)
                else if (!sheet.allowDuplicates && count > sheet.cards.size) add(SetSourceIssue.SHEET_CAPACITY)
            }
        }
    }.toList()
}

@Serializable enum class SetBoosterKind { APPROXIMATE, RECIPE_BASED }
@Serializable data class SetBoosterPack(val cards: List<DraftCopy>, val kind: SetBoosterKind)
