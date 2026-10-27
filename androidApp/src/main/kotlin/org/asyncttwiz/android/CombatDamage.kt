package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import java.math.BigInteger

/** Experimental, client-only assignment aid. Replay records only the resulting ordinary edits. */
internal enum class CombatDamageStep { ALL, FIRST_STRIKE, REGULAR }
internal data class CombatDamageAssignment(val actions: List<PlayAction>, val notes: List<String>)

internal object CombatDamage {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private fun number(text: String): BigInteger? = text.takeIf(TableActions::numeric)?.toBigIntegerOrNull()
    private fun face(play: PlayState, card: PlayCard) = play.definitions.find { it.digest == card.definition }
        ?.definition?.faces?.find { it.faceId == card.faceId }

    fun hasKeyword(play: PlayState, card: PlayCard, keyword: String): Boolean {
        val printed = runCatching {
            (face(play, card)?.sourceData?.let { DeckJson.format.parseToJsonElement(it).jsonObject["keywords"] } as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        }.getOrDefault(emptyList())
        return printed.any { it.equals(keyword, ignoreCase = true) } ||
            Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(keyword)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
                .containsMatchIn("${card.note}\n${card.eotNote}")
    }

    fun splitSteps(play: PlayState): Boolean = CombatStackRules.occurrences(play.board.combat).any { occurrence ->
        play.board.cards.find { it.id == occurrence.sourceId }?.let {
            hasKeyword(play, it, "First strike") || hasKeyword(play, it, "Double strike")
        } == true
    }

    fun dealsInStep(play: PlayState, card: PlayCard, step: CombatDamageStep): Boolean = when (step) {
        CombatDamageStep.ALL -> true
        CombatDamageStep.FIRST_STRIKE -> hasKeyword(play, card, "First strike") || hasKeyword(play, card, "Double strike")
        CombatDamageStep.REGULAR -> !hasKeyword(play, card, "First strike") || hasKeyword(play, card, "Double strike")
    }

    /** Selection order never overrides projection display order, including target-lane grouping.
     * All sources read the original P/T; marked damage accumulates within this invocation.
     * Unknown recipient numbers skip the whole dealer rather than publishing a partial assignment. */
    fun assign(play: PlayState, selected: Collection<String>, targetOnly: Boolean = false,
        step: CombatDamageStep = CombatDamageStep.ALL): CombatDamageAssignment {
        val board = play.board
        val cards = board.cards.associateBy { it.id }
        val damage = linkedMapOf<String, BigInteger>()
        val corners = linkedMapOf<String, BigInteger>()
        val life = linkedMapOf<String, BigInteger>()
        val notes = linkedSetOf<String>()
        val pairs = board.combat?.pairs.orEmpty().groupBy { it.attackTarget }.values.flatten()
        for (pair in pairs) for (role in listOf(CombatRole.BLOCKER, CombatRole.ATTACKER)) {
            if (targetOnly && role != CombatRole.ATTACKER) continue
            for (occurrence in pair.side(role)) {
                if (occurrence.id !in selected) continue
                val source = cards[occurrence.sourceId] ?: continue
                if (!dealsInStep(play, source, step)) continue
                fun manual() { notes += "${face(play, source)?.name ?: "Card"}: assign manually" }
                val power = effectiveCorner(source).split('/').takeIf { it.size == 2 }?.first()?.let(::number)
                if (power == null || hasKeyword(play, source, "Infect") || hasKeyword(play, source, "Wither")) {
                    manual(); continue
                }
                if (power <= zero) continue
                val recipients = if (targetOnly) emptyList() else pair.side(
                    if (role == CombatRole.ATTACKER) CombatRole.BLOCKER else CombatRole.ATTACKER)
                val assigned = linkedMapOf<String, BigInteger>()
                var remaining = power
                var valid = true
                for (recipient in recipients) {
                    if (remaining == zero) break
                    val card = cards[recipient.sourceId]
                    val toughness = card?.let { effectiveCorner(it).split('/').takeIf { it.size == 2 }?.get(1)?.let(::number) }
                    val marked = card?.let { number(it.damage.ifEmpty { "0" }) }
                    if (card == null || toughness == null || marked == null) { valid = false; break }
                    val left = (toughness - marked - (damage[card.id] ?: zero) - (assigned[card.id] ?: zero)).max(zero)
                    val lethal = if (left > zero && hasKeyword(play, source, "Deathtouch")) one else left
                    val amount = remaining.min(lethal)
                    if (amount > zero) assigned[card.id] = (assigned[card.id] ?: zero) + amount
                    remaining -= amount
                }
                val toTarget = role == CombatRole.ATTACKER && (recipients.isEmpty() ||
                    hasKeyword(play, source, "Trample"))
                var targetCard: PlayCard? = null
                var targetPlayer: PlayPlayer? = null
                if (valid && remaining > zero) {
                    if (toTarget) {
                        if (pair.attackTarget != null) {
                            targetCard = cards[pair.attackTarget]
                            valid = targetCard?.corner?.let(::number) != null
                        } else {
                            targetPlayer = board.players.find { it.accountId == CombatStackRules.defendingPlayer(board) }
                            valid = targetPlayer?.life?.let(::number) != null
                        }
                    } else if (recipients.isNotEmpty()) {
                        val last = recipients.last().sourceId
                        // Even if earlier recipients absorbed everything lethal, the last gets excess.
                        valid = cards[last]?.damage?.ifEmpty { "0" }?.let(::number) != null
                        assigned[last] = (assigned[last] ?: zero) + remaining
                    }
                }
                val dealt = if (recipients.isEmpty() && !toTarget) zero else power
                val controller = board.containers.find { it.id == source.containerId }?.accountId
                val lifelink = dealt > zero && hasKeyword(play, source, "Lifelink")
                if (lifelink && board.players.find { it.accountId == controller }?.life?.let(::number) == null) valid = false
                if (!valid) { manual(); continue }
                assigned.forEach { (id, amount) -> damage[id] = (damage[id] ?: zero) + amount }
                if (remaining > zero) {
                    targetCard?.let { corners[it.id] = (corners[it.id] ?: zero) - remaining }
                    targetPlayer?.let { life[it.accountId] = (life[it.accountId] ?: zero) - remaining }
                }
                if (lifelink) life[controller!!] = (life[controller] ?: zero) + dealt
            }
        }
        val actions = buildList {
            damage.filterValues { it != zero }.forEach { (id, amount) -> add(PlayAction.AdjustDamage(id, amount.toString())) }
            corners.filterValues { it != zero }.forEach { (id, amount) ->
                add(PlayAction.SetCorner(id, (number(cards.getValue(id).corner)!! + amount).toString())) }
            life.filterValues { it != zero }.forEach { (id, amount) -> add(PlayAction.AdjustLife(id, amount.toString())) }
        }
        return CombatDamageAssignment(actions, notes.toList())
    }
}
