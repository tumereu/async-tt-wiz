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

    fun setBasics(game: GameSummary, current: LobbyPreparation, actor: String, scope: GameScope,
        action: GameAction.SetPodGameBasics): PreparationChange {
        fun reject(reason: String) = PreparationChange(reason = reason)
        if (game.lifecycle != GameLifecycle.LOBBY) return reject("lifecycleEnded")
        if (scope.gameId != game.id || scope.preparationId != game.preparationRound || scope.playId != null ||
            current.gameId != game.id || current.roundId != game.preparationRound) return reject("inapplicable")
        if (actor != game.inviter.accountId && (actor != game.recipient.accountId || game.invitationState != InvitationState.ACCEPTED))
            return reject("accessDenied")
        if (current.podRules == null) return reject("notPodGame")
        val deck = current.players.find { it.accountId == actor }?.deck ?: return reject("preparedDeckChanged")
        if (deck.id != action.deckId) return reject("preparedDeckChanged")
        if (basicDefinitions(action.basicDefinitions) != deck.basicDefinitions) return reject("invalidBasicDefinitions")
        val fixed = deck.entries.filter { it.suppliedBasic == null || it.section != action.section }
        if (!action.basics.valid || fixed.sumOf { it.quantity.toLong() } + action.basics.total > DeckValidation.MAX_CARDS)
            return reject("invalidBasics")
        if (basics(deck, action.section) == action.basics) return PreparationChange(current)
        var index = maxOf(GameWire.ordinal(deck.nextCardIndex), deck.entries.maxOfOrNull {
            GameWire.ordinal(it.firstIndex) + it.quantity } ?: 0)
        val entries = fixed + BasicLand.entries.mapNotNull { land ->
            land.count(action.basics).takeIf { it > 0 }?.let { count ->
                PreparedEntry(deck.basicDefinitions.getValue(land), action.section, index.toString(), count, land)
                    .also { index += count }
            }
        }
        return PreparationChange(current.copy(players = current.players.map {
            if (it.accountId == actor) it.copy(ready = false, deck = deck.copy(entries = entries, nextCardIndex = index.toString())) else it
        }))
    }
}
