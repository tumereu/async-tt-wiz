package org.asyncttwiz.android

import org.asyncttwiz.protocol.DraftPrivatePack
import org.asyncttwiz.protocol.DraftSettings
import kotlin.math.abs

internal enum class PodPickMark { KEEP, BURN }

/** Ordered local intent only; the server still validates and applies the confirmed pick. */
internal data class PodPickMarks(
    val packId: String? = null,
    val keeps: List<String> = emptyList(),
    val burns: List<String> = emptyList(),
) {
    fun forPack(pack: DraftPrivatePack?): PodPickMarks = if (packId == pack?.id) this else PodPickMarks(pack?.id)

    fun markOf(id: String): PodPickMark? = when (id) {
        in keeps -> PodPickMark.KEEP
        in burns -> PodPickMark.BURN
        else -> null
    }

    fun unmark(id: String) = copy(keeps = keeps - id, burns = burns - id)

    fun toggle(id: String, mark: PodPickMark, rules: PodPickRules): PodPickMarks {
        if (id !in rules.ids || (mark == PodPickMark.BURN && rules.manualBurns == 0)) return this
        if (markOf(id) == mark) return unmark(id)
        val cleared = unmark(id)
        return when (mark) {
            PodPickMark.KEEP -> cleared.copy(keeps = (cleared.keeps + id).takeLast(rules.requiredKeeps))
            PodPickMark.BURN -> cleared.copy(burns = (cleared.burns + id).takeLast(rules.manualBurns))
        }
    }
}

internal class PodPickRules(pack: DraftPrivatePack, settings: DraftSettings) {
    val ids = pack.cards.map { it.id }
    val requiredKeeps = minOf(settings.keep, ids.size)
    val automaticBurns = ids.size <= settings.keep.toLong() + settings.burn
    val manualBurns = if (automaticBurns) 0 else settings.burn
    val requiredBurns = if (automaticBurns) ids.size - requiredKeeps else settings.burn

    fun burnIds(marks: PodPickMarks): List<String> = when {
        !automaticBurns -> marks.burns
        marks.keeps.size != requiredKeeps -> emptyList()
        else -> ids.filterNot { it in marks.keeps }
    }

    fun canConfirm(marks: PodPickMarks, busy: Boolean): Boolean {
        val burns = burnIds(marks)
        return !busy && ids.isNotEmpty() && marks.keeps.size == requiredKeeps && burns.size == requiredBurns &&
            (marks.keeps + burns).let { selected -> selected.distinct().size == selected.size && selected.all { it in ids } }
    }
}

internal fun podPickSwipe(offset: Float, width: Int, velocity: Float, flingThreshold: Float): PodPickMark? {
    val direction = when {
        width > 0 && abs(offset) / width >= .3f -> offset
        abs(velocity) >= flingThreshold -> velocity
        else -> return null
    }
    return if (direction > 0) PodPickMark.KEEP else PodPickMark.BURN
}
