package org.asyncttwiz.android

import org.asyncttwiz.protocol.DraftCopy
import org.asyncttwiz.protocol.DraftPrivatePack
import org.asyncttwiz.protocol.DraftSettings

internal enum class PodPickMark { KEEP, BURN }

/** Ordered local intent only; the server still validates and applies the confirmed pick. */
internal data class PodPickMarks(
    val packId: String? = null,
    val keeps: List<String> = emptyList(),
    val burns: List<String> = emptyList(),
    val librarians: List<String> = emptyList(),
) {
    fun forPack(pack: DraftPrivatePack?): PodPickMarks = if (packId == pack?.id) this else PodPickMarks(pack?.id)

    fun markOf(id: String): PodPickMark? = when (id) {
        in keeps -> PodPickMark.KEEP
        in burns -> PodPickMark.BURN
        else -> null
    }

    fun unmark(id: String) = copy(keeps = keeps - id, burns = burns - id)

    /** A spent Librarian joins the pack as a card for this pick and adds one keep slot. */
    fun spendLibrarian(available: List<String>, pack: DraftPrivatePack, settings: DraftSettings): PodPickMarks {
        val next = available.firstOrNull { it !in librarians } ?: return this
        return copy(librarians = librarians + next).trim(pack, settings)
    }

    /** Takes the Librarian back out of the pack, with any keep or burn mark on it. */
    fun refundLibrarian(id: String, pack: DraftPrivatePack, settings: DraftSettings) =
        copy(librarians = librarians - id, keeps = keeps - id, burns = burns - id).trim(pack, settings)

    private fun trim(pack: DraftPrivatePack, settings: DraftSettings): PodPickMarks {
        val rules = PodPickRules(pack, settings, librarians)
        return copy(keeps = keeps.takeLast(rules.requiredKeeps), burns = burns.takeLast(rules.manualBurns))
    }

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

/** The pack as shown for this pick: its cards followed by the spent [librarians], which are ordinary cards here. */
internal class PodPickRules(pack: DraftPrivatePack, settings: DraftSettings, val librarians: List<String> = emptyList()) {
    val ids = pack.cards.map { it.id } + librarians
    val spentLibrarians get() = librarians.size
    val requiredKeeps = minOf(settings.keep + librarians.size, ids.size)
    val automaticBurns = ids.size <= requiredKeeps.toLong() + settings.burn
    val manualBurns = if (automaticBurns) 0 else settings.burn
    val requiredBurns = if (automaticBurns) ids.size - requiredKeeps else settings.burn

    fun burnIds(marks: PodPickMarks): List<String> = when {
        !automaticBurns -> marks.burns
        marks.keeps.size != requiredKeeps -> emptyList()
        else -> ids.filterNot { it in marks.keeps }
    }

    fun canConfirm(marks: PodPickMarks, busy: Boolean): Boolean {
        val burns = burnIds(marks)
        return !busy && ids.isNotEmpty() && marks.librarians == librarians && marks.keeps.size == requiredKeeps && burns.size == requiredBurns &&
            (marks.keeps + burns).let { selected -> selected.distinct().size == selected.size && selected.all { it in ids } }
    }
}

/** [this] pack with the spent Librarians (in spend order) added as cards, as the picker sees it before confirming. */
internal fun DraftPrivatePack.withSpentLibrarians(spent: List<String>, owned: List<DraftCopy>): DraftPrivatePack {
    if (spent.isEmpty()) return this
    val copies = owned.associateBy { it.id }
    return copy(cards = cards + spent.mapNotNull(copies::get))
}
