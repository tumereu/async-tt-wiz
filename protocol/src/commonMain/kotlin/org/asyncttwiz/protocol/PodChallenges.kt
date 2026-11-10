package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** POST /v1/pods/{id}/challenge; the server supplies the basic lands from its catalog (LOB-052). */
@Serializable data class ChallengeFromPod(val requestId: String, val opponentId: String, val gameName: String)
@Serializable enum class BasicLand(val cardName: String) {
    PLAINS("Plains"), ISLAND("Island"), SWAMP("Swamp"), MOUNTAIN("Mountain"), FOREST("Forest");
    fun count(basics: PodDeckBasics): Int = when (this) {
        PLAINS -> basics.plains; ISLAND -> basics.island; SWAMP -> basics.swamp
        MOUNTAIN -> basics.mountain; FOREST -> basics.forest
    }
    fun withCount(basics: PodDeckBasics, count: Int): PodDeckBasics = when (this) {
        PLAINS -> basics.copy(plains = count); ISLAND -> basics.copy(island = count); SWAMP -> basics.copy(swamp = count)
        MOUNTAIN -> basics.copy(mountain = count); FOREST -> basics.copy(forest = count)
    }
}
@Serializable data class PodGameRules(val sideboardLimit: Int? = null)

/** Game-local snapshots: no rule here consults a live pod, pool, or saved deck. */
object PodGamePreparation {
    fun basics(deck: PreparedDeck, section: SavedDeckSection = SavedDeckSection.MAINBOARD) = BasicLand.entries.fold(PodDeckBasics()) { counts, land ->
        land.withCount(counts, deck.entries.filter { it.suppliedBasic == land && it.section == section }.sumOf { it.quantity })
    }

    fun readyProblem(state: LobbyPreparation?, actor: String): String? {
        val rules = state?.podRules ?: return null
        val deck = state.players.find { it.accountId == actor }?.deck ?: return "deckRequired"
        return when {
            deck.count(SavedDeckSection.MAINBOARD) < 40 -> "mainTooSmall"
            rules.sideboardLimit != null && deck.count(SavedDeckSection.SIDEBOARD) > rules.sideboardLimit -> "sideboardLimit"
            else -> null
        }
    }
}
