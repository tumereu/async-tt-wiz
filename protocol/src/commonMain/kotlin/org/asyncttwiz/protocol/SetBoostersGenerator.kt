package org.asyncttwiz.protocol

/** Pure, versioned-random pack generation. Supply resets for every pack, not for every draft. */
object SetBoosters {
    const val MAX_PACKS = 10000
    const val MAX_GENERATED_COPIES = 1000000

    fun generate(source: SetSourceSnapshot, numberOfPacks: Int, seed: String): List<SetBoosterPack> {
        require(source.validate().isEmpty()) { "Invalid set source snapshot" }
        require(numberOfPacks in 1..MAX_PACKS) { "Invalid pack count" }
        val recipe = source.recipe
        val maxSize = recipe?.boosters?.maxOf { it.contents.values.sum() } ?: 15
        require(numberOfPacks.toLong() * maxSize <= MAX_GENERATED_COPIES) { "Too many generated copies" }
        val words = RandomV1.Words(seed)
        val cards = source.cards.sortedBy { it.uuid }
        val byUuid = cards.associateBy { it.uuid }
        val buckets = cards.groupBy { it.rarity }

        fun random(bucket: List<SetSourceCard>) = bucket[RandomV1.sample(bucket.size, words::next)]
        fun fallback(rarity: SetCardRarity) = random(buckets[rarity] ?: cards)
        fun approximate(): List<SetSourceCard> = buildList {
            repeat(10) { add(fallback(SetCardRarity.COMMON)) }
            repeat(3) { add(fallback(SetCardRarity.UNCOMMON)) }
            val mythic = SetCardRarity.MYTHIC in buckets && RandomV1.sample(8, words::next) == 0
            add(fallback(if (mythic) SetCardRarity.MYTHIC else SetCardRarity.RARE))
            add(random(cards))
        }

        fun draw(sheet: SetBoosterSheet, count: Int): List<SetSourceCard> {
            if (sheet.fixed) {
                // Fixed order wins over color balancing and weights. Duplicating fixed sheets cycle in that order.
                val ordered = sheet.cards.keys.toList()
                return List(count) { byUuid.getValue(ordered[it % ordered.size]) }
            }
            val available = sheet.cards.entries.sortedBy { it.key }.toMutableList()
            val drawn = mutableListOf<SetSourceCard>()
            fun take(candidates: List<Map.Entry<String, Int>>) {
                val entry = weighted(candidates, { it.value }, words)
                drawn.add(byUuid.getValue(entry.key))
                if (!sheet.allowDuplicates) available.remove(entry)
            }
            if (sheet.balanceColors) {
                // A multicolor card can cover several colors; scarce colors are a heuristic, not a quota.
                for (color in DraftColor.entries) {
                    if (drawn.size == count) break
                    if (drawn.any { color in it.colors }) continue
                    val candidates = available.filter { color in byUuid.getValue(it.key).colors }
                    if (candidates.isNotEmpty()) take(candidates)
                }
            }
            while (drawn.size < count) take(available)
            return drawn
        }

        return List(numberOfPacks) { packIndex ->
            val drawn = if (recipe == null) approximate() else {
                val composition = weighted(recipe.boosters, { it.weight }, words)
                composition.contents.entries.sortedBy { it.key }.flatMap { (name, count) ->
                    draw(recipe.sheets.getValue(name), count)
                }
            }
            SetBoosterPack(drawn.mapIndexed { index, card ->
                DraftCopy("set-pack-$packIndex:copy:$index", card.definition, card.colors.sortedBy { it.ordinal }.toSet())
            }, if (recipe == null) SetBoosterKind.APPROXIMATE else SetBoosterKind.RECIPE_BASED)
        }
    }

    private fun <T> weighted(values: List<T>, weight: (T) -> Int, words: RandomV1.Words): T {
        var ticket = RandomV1.sample(values.sumOf(weight), words::next)
        for (value in values) {
            ticket -= weight(value)
            if (ticket < 0) return value
        }
        error("Validated weights must cover the sample")
    }
}
