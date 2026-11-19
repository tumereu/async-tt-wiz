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
/** [basics] are Main lands, [sideBasics] Side lands, supplied as many as wanted. [landed]: the drafted copies of the five basic
 * lands, which went into [basics] (one each) instead of becoming copies (DRF-093); they stay in the pool. */
@Serializable data class PodDeckBuild(val copies: List<PodDeckCopy> = emptyList(), val basics: PodDeckBasics = PodDeckBasics(),
    val sideBasics: PodDeckBasics = PodDeckBasics(), val landed: List<String> = emptyList())
@Serializable enum class PodDeckIssue { MAIN_TOO_SMALL, UNKNOWN_COPY, DUPLICATE_COPY, MISSING_COPY, NEGATIVE_BASICS, SIDEBOARD_LIMIT, DECK_TOO_LARGE }

object PodDeckRules {
    /** New builds: plenty of Side lands when the Side is unlimited, none when every Side card counts. */
    fun initialBuild(sideboardLimit: Int?): PodDeckBuild =
        PodDeckBuild(sideBasics = if (sideboardLimit == null) PodDeckBasics(17, 17, 17, 17, 17) else PodDeckBasics())

    /** Where a swipe moves a copy in Build and its review (DRF-099): the shared section rule ([SectionSwipe]). */
    fun swipe(category: PodDeckCategory, direction: PodDeckSwipe): PodDeckCategory =
        SectionSwipe.target(PodDeckCategory.entries, category, direction == PodDeckSwipe.RIGHT)

    /** Merge the latest pool without changing existing assignments. Awards start in Main; an award [basicOf] names as one of the
     * five basic lands adds one to Main's count of that land instead (DRF-093). */
    fun withAwards(build: PodDeckBuild, pool: List<String>, basicOf: (String) -> BasicLand? = { null }): PodDeckBuild =
        awarded(build, pool.distinct(), emptySet(), basicOf)

    /** Synchronize ownership after a Librarian exchange, preserving all surviving assignments; a drafted basic land that left
     * the pool takes its one back from Main's count. New copies as [withAwards] adds them ([initialSide] ones in Side). */
    fun withDraftPool(build: PodDeckBuild, pool: List<String>, initialSide: Set<String> = emptySet(),
        basicOf: (String) -> BasicLand? = { null }): PodDeckBuild {
        val owned = pool.toSet()
        var basics = build.basics
        for (gone in build.landed.filter { it !in owned }) basicOf(gone)?.let { land ->
            basics = land.withCount(basics, (land.count(basics) - 1).coerceAtLeast(0))
        }
        val retained = build.copy(copies = build.copies.filter { it.copyId in owned }, basics = basics,
            landed = build.landed.filter { it in owned })
        return awarded(retained, pool, initialSide, basicOf)
    }

    private fun awarded(build: PodDeckBuild, pool: List<String>, initialSide: Set<String>, basicOf: (String) -> BasicLand?): PodDeckBuild {
        val existing = (build.copies.map { it.copyId } + build.landed).toSet()
        var basics = build.basics
        val copies = build.copies.toMutableList(); val landed = build.landed.toMutableList()
        for (id in pool.filter { it !in existing }) {
            val land = basicOf(id)
            if (land != null && id !in initialSide) { basics = land.withCount(basics, land.count(basics) + 1); landed += id }
            else copies += PodDeckCopy(id, if (id in initialSide) PodDeckCategory.SIDE else PodDeckCategory.MAIN)
        }
        return build.copy(copies = copies, basics = basics, landed = landed)
    }

    /** DRF-166: what a draft deck's cover is chosen from: the definitions of Main's copies, then Side's, each once. */
    fun coverChoices(build: PodDeckBuild, definitionOf: (String) -> String?): List<String> =
        listOf(PodDeckCategory.MAIN, PodDeckCategory.SIDE).flatMap { category ->
            build.copies.filter { it.category == category }.mapNotNull { definitionOf(it.copyId) }
        }.distinct()

    /** All drafted copies, including Maybe, must be assigned exactly once (a drafted basic land by being [PodDeckBuild.landed]). Every copy and basic goes to the pod's games (Maybe
     * outside the game), so all of them count toward the deck size bound; the sideboard limit counts Side only (DRF-025).
     * Lifecycle/locking is the server's job. */
    fun validate(build: PodDeckBuild, pool: List<String>, sideboardLimit: Int? = null): List<PodDeckIssue> {
        require(sideboardLimit == null || sideboardLimit >= 0)
        val ids = build.copies.map { it.copyId } + build.landed
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
