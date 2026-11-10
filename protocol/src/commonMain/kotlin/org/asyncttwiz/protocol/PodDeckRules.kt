package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** A Build section (DRF-092). Maybe holds cards set aside: in a pod game they are outside the game with Side, never in the
 * library. */
@Serializable enum class PodDeckCategory { MAIN, SIDE, MAYBE }
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

    /** Where a swipe moves a copy in Build and its review (DRF-099): the shared section rule ([SectionSwipe]). */
    fun swipe(category: PodDeckCategory, direction: PodDeckSwipe): PodDeckCategory =
        SectionSwipe.target(PodDeckCategory.entries, category, direction == PodDeckSwipe.RIGHT)

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

    /** DRF-166: what a draft deck's cover is chosen from: the definitions of Main's copies, then Side's, each once. */
    fun coverChoices(build: PodDeckBuild, definitionOf: (String) -> String?): List<String> =
        listOf(PodDeckCategory.MAIN, PodDeckCategory.SIDE).flatMap { category ->
            build.copies.filter { it.category == category }.mapNotNull { definitionOf(it.copyId) }
        }.distinct()

    /** All drafted copies, including Maybe, must be assigned exactly once. Every copy and basic goes to the pod's games (Maybe
     * outside the game), so all of them count toward the deck size bound; the sideboard limit counts Side only (DRF-025).
     * Lifecycle/locking is the server's job. */
    fun validate(build: PodDeckBuild, pool: List<String>, sideboardLimit: Int? = null): List<PodDeckIssue> {
        require(sideboardLimit == null || sideboardLimit >= 0)
        val ids = build.copies.map { it.copyId }
        val assigned = ids.toSet()
        val owned = pool.toSet()
        return buildList {
            if (!build.basics.valid || !build.sideBasics.valid) add(PodDeckIssue.NEGATIVE_BASICS)
            if (build.copies.count { it.category == PodDeckCategory.MAIN }.toLong() + build.basics.total < 40)
                add(PodDeckIssue.MAIN_TOO_SMALL)
            if (build.copies.size.toLong() + build.basics.total + build.sideBasics.total > DeckValidation.MAX_CARDS)
                add(PodDeckIssue.DECK_TOO_LARGE)
            if (assigned.any { it !in owned }) add(PodDeckIssue.UNKNOWN_COPY)
            if (assigned.size != ids.size) add(PodDeckIssue.DUPLICATE_COPY)
            if (owned.any { it !in assigned }) add(PodDeckIssue.MISSING_COPY)
            if (sideboardLimit != null && build.copies.count { it.category == PodDeckCategory.SIDE } + build.sideBasics.total > sideboardLimit)
                add(PodDeckIssue.SIDEBOARD_LIMIT)
        }
    }
}
