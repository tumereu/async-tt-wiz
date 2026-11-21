package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** POST /v1/pods/{id}/challenge; the server supplies the basic lands from its catalog (LOB-052). Without [gameName] the server
 * names the game "<pod name> #N" (DRF-304), N counted per pod. */
@Serializable data class ChallengeFromPod(val requestId: String, val opponentId: String, val gameName: String? = null)

/** DRF-304 (owner, 2026-11-21): a pod challenge's game is named after its pod and numbered per pod, "Cube night #3": the
 * server counts the pod's games when it creates one, so both players see the same name; a declined or withdrawn challenge
 * keeps its number. */
object PodGameNames {
    fun name(podName: String, number: Int): String = "$podName #$number"
}
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
    companion object {
        /** The basic land a card named [cardName] is (exactly one of the five names), or null. */
        fun named(cardName: String): BasicLand? = entries.firstOrNull { it.cardName == cardName }
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
