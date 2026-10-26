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
@Serializable data class PodDeckBuild(val copies: List<PodDeckCopy> = emptyList(), val basics: PodDeckBasics = PodDeckBasics())
@Serializable enum class PodDeckIssue { MAIN_TOO_SMALL, UNKNOWN_COPY, DUPLICATE_COPY, MISSING_COPY, NEGATIVE_BASICS, SIDEBOARD_LIMIT, DECK_TOO_LARGE }

object PodDeckRules {
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

    /** All drafted copies, including Trash, must be assigned exactly once. Lifecycle/locking is the server's job. */
    fun validate(build: PodDeckBuild, pool: List<String>, sideboardLimit: Int? = null): List<PodDeckIssue> {
        require(sideboardLimit == null || sideboardLimit >= 0)
        val ids = build.copies.map { it.copyId }
        val assigned = ids.toSet()
        val owned = pool.toSet()
        return buildList {
            if (!build.basics.valid) add(PodDeckIssue.NEGATIVE_BASICS)
            if (build.copies.count { it.category == PodDeckCategory.MAIN }.toLong() + build.basics.total < 40)
                add(PodDeckIssue.MAIN_TOO_SMALL)
            if (build.copies.count { it.category != PodDeckCategory.TRASH }.toLong() + build.basics.total > DeckValidation.MAX_CARDS)
                add(PodDeckIssue.DECK_TOO_LARGE)
            if (assigned.any { it !in owned }) add(PodDeckIssue.UNKNOWN_COPY)
            if (assigned.size != ids.size) add(PodDeckIssue.DUPLICATE_COPY)
            if (owned.any { it !in assigned }) add(PodDeckIssue.MISSING_COPY)
            if (sideboardLimit != null && build.copies.count { it.category == PodDeckCategory.SIDE } > sideboardLimit)
                add(PodDeckIssue.SIDEBOARD_LIMIT)
        }
    }
}
