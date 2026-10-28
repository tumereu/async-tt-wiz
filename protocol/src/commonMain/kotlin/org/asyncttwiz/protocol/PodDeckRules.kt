package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class PodDeckCategory { MAIN, SIDE, TRASH }
@Serializable enum class PodDeckSwipe { LEFT, RIGHT }
@Serializable data class PodDeckCopy(val copyId: String, val category: PodDeckCategory = PodDeckCategory.MAIN)
@Serializable data class PodDeckBasics(
    val plains: Int = 0,
    val island: Int = 0,
    val swamp: Int = 0,
    val mountain: Int = 0,
    val forest: Int = 0,
) {
    val valid: Boolean get() = listOf(plains, island, swamp, mountain, forest).all { it >= 0 }
    val total: Long get() = plains.toLong() + island + swamp + mountain + forest
}
/** [basics] are Main lands, [sideBasics] Side lands; both are supplied, not drafted. */
@Serializable data class PodDeckBuild(val copies: List<PodDeckCopy> = emptyList(), val basics: PodDeckBasics = PodDeckBasics(),
    val sideBasics: PodDeckBasics = PodDeckBasics())
@Serializable enum class PodDeckIssue { MAIN_TOO_SMALL, UNKNOWN_COPY, DUPLICATE_COPY, MISSING_COPY, NEGATIVE_BASICS, SIDEBOARD_LIMIT, DECK_TOO_LARGE }

object PodDeckRules {
    /** New builds: plenty of Side lands when the Side is unlimited, none when every Side card counts. */
    fun initialBuild(sideboardLimit: Int?): PodDeckBuild =
        PodDeckBuild(sideBasics = if (sideboardLimit == null) PodDeckBasics(17, 17, 17, 17, 17) else PodDeckBasics())

    fun swipe(category: PodDeckCategory, direction: PodDeckSwipe): PodDeckCategory = when (category) {
        PodDeckCategory.MAIN -> if (direction == PodDeckSwipe.RIGHT) PodDeckCategory.SIDE else PodDeckCategory.TRASH
        PodDeckCategory.SIDE -> if (direction == PodDeckSwipe.RIGHT) PodDeckCategory.MAIN else PodDeckCategory.TRASH
        PodDeckCategory.TRASH -> if (direction == PodDeckSwipe.RIGHT) PodDeckCategory.MAIN else PodDeckCategory.SIDE
    }

    /** Merge the latest pool without changing existing assignments or basics. Awards always start in Main. */
    fun withAwards(build: PodDeckBuild, pool: List<String>): PodDeckBuild {
        val existing = build.copies.map { it.copyId }.toSet()
        return build.copy(copies = build.copies + pool.distinct().filter { it !in existing }.map { PodDeckCopy(it) })
    }

    /** Synchronize ownership after a Librarian exchange, preserving all surviving assignments. */
    fun withDraftPool(build: PodDeckBuild, pool: List<String>, initialSide: Set<String> = emptySet()): PodDeckBuild {
        val retained = build.copy(copies = build.copies.filter { it.copyId in pool })
        val existing = retained.copies.map { it.copyId }.toSet()
        return retained.copy(copies = retained.copies + pool.filter { it !in existing }.map {
            PodDeckCopy(it, if (it in initialSide) PodDeckCategory.SIDE else PodDeckCategory.MAIN)
        })
    }

    /** All drafted copies, including Trash, must be assigned exactly once. Lifecycle/locking is the server's job. */
    fun validate(build: PodDeckBuild, pool: List<String>, sideboardLimit: Int? = null): List<PodDeckIssue> {
        require(sideboardLimit == null || sideboardLimit >= 0)
        val ids = build.copies.map { it.copyId }
        val assigned = ids.toSet()
        val owned = pool.toSet()
        return buildList {
            if (!build.basics.valid || !build.sideBasics.valid) add(PodDeckIssue.NEGATIVE_BASICS)
            if (build.copies.count { it.category == PodDeckCategory.MAIN }.toLong() + build.basics.total < 40)
                add(PodDeckIssue.MAIN_TOO_SMALL)
            if (build.copies.count { it.category != PodDeckCategory.TRASH }.toLong() + build.basics.total + build.sideBasics.total > DeckValidation.MAX_CARDS)
                add(PodDeckIssue.DECK_TOO_LARGE)
            if (assigned.any { it !in owned }) add(PodDeckIssue.UNKNOWN_COPY)
            if (assigned.size != ids.size) add(PodDeckIssue.DUPLICATE_COPY)
            if (owned.any { it !in assigned }) add(PodDeckIssue.MISSING_COPY)
            if (sideboardLimit != null && build.copies.count { it.category == PodDeckCategory.SIDE } + build.sideBasics.total > sideboardLimit)
                add(PodDeckIssue.SIDEBOARD_LIMIT)
        }
    }
}
