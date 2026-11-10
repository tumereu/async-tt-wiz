package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable data class ChallengeFromPod(val requestId: String, val opponentId: String, val gameName: String,
    val basicDefinitions: List<SubmittedDefinition>)
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
    fun basicDefinitions(definitions: List<SubmittedDefinition>): Map<BasicLand, String>? {
        if (definitions.size != 5) return null
        val result = mutableMapOf<BasicLand, String>()
        for (submitted in definitions) {
            val card = submitted.definition
            val land = BasicLand.entries.find { it.cardName == card.name } ?: return null
            val face = card.faces.singleOrNull() ?: return null
            val mana = when (land) {
                BasicLand.PLAINS -> "W"; BasicLand.ISLAND -> "U"; BasicLand.SWAMP -> "B"
                BasicLand.MOUNTAIN -> "R"; BasicLand.FOREST -> "G"
            }
            if (!DeckValidation.definition(card) || DeckJson.digest(card) != submitted.digest || card.token ||
                face.name != land.cardName || face.typeLine != "Basic Land — ${land.cardName}" ||
                face.power.isNotEmpty() || face.toughness.isNotEmpty() || face.loyalty.isNotEmpty() || face.defense.isNotEmpty() ||
                (face.rulesText.isNotEmpty() && face.rulesText != "({T}: Add {$mana}.)") ||
                result.put(land, submitted.digest) != null) return null
        }
        return result
    }

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
