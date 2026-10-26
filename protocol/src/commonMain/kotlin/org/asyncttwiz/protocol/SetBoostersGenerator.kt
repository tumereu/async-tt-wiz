package org.asyncttwiz.protocol

/** Pure, versioned-random pack generation. Supply resets for every pack, not for every draft. */
object SetBoosters {
    const val MAX_PACKS = 10000
    const val MAX_GENERATED_COPIES = 1000000

    fun generate(source: SetSourceSnapshot, numberOfPacks: Int, seed: String, cardsPerPack: Int = 15): List<SetBoosterPack> {
        require(source.validate().isEmpty()) { "Invalid set source snapshot" }
        require(numberOfPacks in 1..MAX_PACKS) { "Invalid pack count" }
        val recipe = source.recipe
        if (recipe == null) require(cardsPerPack in 1..SetSourceValidation.MAX_PACK_SIZE) { "Invalid pack size" }
        val maxSize = recipe?.boosters?.maxOf { it.contents.values.sum() } ?: cardsPerPack
        require(numberOfPacks.toLong() * maxSize <= MAX_GENERATED_COPIES) { "Too many generated copies" }
        val words = RandomV1.Words(seed)
        val cards = source.cards.sortedBy { it.uuid }
        val byUuid = cards.associateBy { it.uuid }
        val buckets = cards.groupBy { it.rarity }

        fun random(bucket: List<SetSourceCard>) = bucket[RandomV1.sample(bucket.size, words::next)]
        fun fallback(rarity: SetCardRarity) = random(buckets[rarity] ?: cards)
        fun approximate(): List<SetSourceCard> = buildList {
            // Reserve a rare, then a wildcard if size permits; floor(size / 5) uncommons,
            // with all remaining slots common. Size 15 retains the 10C/3U/1R+M/1-any layout.
            val wildcard = if (cardsPerPack >= 2) 1 else 0
            val uncommons = minOf(cardsPerPack / 5, cardsPerPack - 1 - wildcard)
            repeat(cardsPerPack - 1 - wildcard - uncommons) { add(fallback(SetCardRarity.COMMON)) }
            repeat(uncommons) { add(fallback(SetCardRarity.UNCOMMON)) }
            val mythic = SetCardRarity.MYTHIC in buckets && RandomV1.sample(8, words::next) == 0
            add(fallback(if (mythic) SetCardRarity.MYTHIC else SetCardRarity.RARE))
            if (wildcard == 1) add(random(cards))
        }

        fun draw(sheet: SetBoosterSheet, count: Int): List<SetSourceCard> {
            if (sheet.fixed) {
                // Multiplicities are guaranteed, even without allowDuplicates. Expand only the
                // requested prefix so a large multiplicity cannot allocate an unbounded list.
                return buildList {
                    for ((uuid, multiplicity) in sheet.cards) {
                        repeat(minOf(multiplicity, (count - size).toLong()).toInt()) { add(byUuid.getValue(uuid)) }
                        if (size == count) break
                    }
                }
            }
            val available = sheet.cards.entries.sortedBy { it.key }.toMutableList()
            val drawn = mutableListOf<SetSourceCard>()
            fun take(candidates: List<Map.Entry<String, Long>>) {
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

    private fun <T> weighted(values: List<T>, weight: (T) -> Long, words: RandomV1.Words): T {
        var ticket = RandomV1.sample(values.sumOf(weight), words::next)
        for (value in values) {
            ticket -= weight(value)
            if (ticket < 0) return value
        }
        error("Validated weights must cover the sample")
    }
}
