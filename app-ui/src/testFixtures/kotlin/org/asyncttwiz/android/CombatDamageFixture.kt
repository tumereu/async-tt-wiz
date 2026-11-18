package org.asyncttwiz.android

import org.asyncttwiz.protocol.*



/** A combat area at Combat damage: [attackers] of the actor in one pair, blocked by the defender's [blockers]. */
internal class CombatDamageFixture(
    attackers: List<String> = listOf("5/5"), blockers: List<String> = emptyList(),
    keyword: String = "", note: String = "", eotNote: String = "", eotCorner: String = "",
    marked: String = "", targetCorner: String? = null,
) {
    val id = TableToolFixture::id
    val actor = id(7)
    val defender = id(8)
    val attackerIds = attackers.indices.map { id(20 + it) }
    val blockerIds = blockers.indices.map { id(40 + it) }
    val attackerViews = attackers.indices.map { id(120 + it) }
    val blockerViews = blockers.indices.map { id(140 + it) }
    val allViews = attackerViews + blockerViews
    val target = id(60)
    val play: PlayState
    init {
        val definition = CardDefinition("Combatant", listOf(DefinitionFace(id(25), "Combatant",
            typeLine = "Creature", rulesText = keyword)))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val targetDefinition = CardDefinition("Walker", listOf(DefinitionFace(id(26), "Walker", typeLine = "Planeswalker", loyalty = "4")))
        val walker = SubmittedDefinition(DeckJson.digest(targetDefinition), targetDefinition)
        val cards = attackers.mapIndexed { i, pt ->
            PlayCard(attackerIds[i], submitted.digest, actor, id(10), id(25), covered = false,
                corner = pt, note = note, eotNote = eotNote, eotCorner = eotCorner,
                placement = PlayPlacement(BoardPosition(i, 0), id(200 + i)))
        } + blockers.mapIndexed { i, pt ->
            PlayCard(blockerIds[i], submitted.digest, defender, id(13), id(25), covered = false,
                corner = pt, damage = marked, placement = PlayPlacement(BoardPosition(i, 0), id(220 + i)))
        } + listOfNotNull(targetCorner?.let {
            PlayCard(target, walker.digest, defender, id(13), id(26), covered = false, corner = it,
                placement = PlayPlacement(BoardPosition(10, 0), id(260)))
        })
        val initial = TableToolFixture.play(TurnPosition.COMBAT_DAMAGE)
        play = initial.copy(definitions = listOf(submitted, walker), board = initial.board.copy(cards = cards,
            containers = initial.board.containers.map { container -> container.copy(cards = cards.filter { it.containerId == container.id }.map { it.id }) } +
                PlayContainer(id(16), PlayZone.STACK),
            combat = CombatArea(id(90), listOf(CombatPair(id(91),
                attackerViews.zip(attackerIds).map { (view, source) -> CombatOccurrence(view, source) },
                blockerViews.zip(blockerIds).map { (view, source) -> CombatOccurrence(view, source) },
                target.takeIf { targetCorner != null }, blocked = blockers.isNotEmpty())))))
    }
}
