package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

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
            typeLine = "Creature", sourceData = """{"keywords":["$keyword"]}""")))
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
    fun assign(selected: List<String> = attackerViews, targetOnly: Boolean = false,
        step: CombatDamageStep = CombatDamageStep.ALL, state: PlayState = play) = CombatDamage.assign(state, selected, targetOnly, step)
    fun damage(result: CombatDamageAssignment, index: Int = 0) = result.actions.filterIsInstance<PlayAction.AdjustDamage>()
        .find { it.cardId == blockerIds[index] }?.delta
    fun life(result: CombatDamageAssignment, account: String = defender) = result.actions.filterIsInstance<PlayAction.AdjustLife>()
        .find { it.accountId == account }?.delta
}

class CombatDamageTest {
    @Test fun unblockedAndTargetOnlyDealFullPowerAndIgnoreBlockersAsDealers() {
        val unblocked = CombatDamageFixture()
        assertEquals(listOf(PlayAction.AdjustLife(unblocked.defender, "-5")), unblocked.assign().actions)
        val blocked = CombatDamageFixture(blockers = listOf("2/2"))
        assertEquals(listOf(PlayAction.AdjustLife(blocked.defender, "-5")), blocked.assign(blocked.allViews, targetOnly = true).actions)
    }

    @Test fun singleBlockGetsExcessAndBothSidesDealEvenIfLethalDamageIsAssigned() {
        val f = CombatDamageFixture(blockers = listOf("2/2"))
        val result = f.assign(f.allViews)
        assertEquals("5", f.damage(result))
        assertEquals("2", result.actions.filterIsInstance<PlayAction.AdjustDamage>().single { it.cardId == f.attackerIds.single() }.delta)
        assertNull(f.life(result))
    }

    @Test fun multipleBlockersGetLethalFirstAndLastGetsAllNontrampleExcess() {
        val f = CombatDamageFixture(attackers = listOf("9/9"), blockers = listOf("1/2", "1/3", "1/2"))
        val result = f.assign()
        assertEquals(listOf("2", "3", "4"), f.blockerIds.indices.map { f.damage(result, it) })
    }

    @Test fun trampleRemainderGoesToPlayerAndDeathtouchLethalIsOne() {
        for ((keywords, expected, player) in listOf(
            Triple("Trample", listOf("2", "2"), "-1"),
            Triple("Deathtouch", listOf("1", "4"), null),
            Triple("Deathtouch, Trample", listOf("1", "1"), "-3"))) {
            val f = CombatDamageFixture(blockers = listOf("2/2", "2/2"), note = keywords)
            val result = f.assign()
            assertEquals(expected, f.blockerIds.indices.map { f.damage(result, it) }, keywords)
            assertEquals(player, f.life(result), keywords)
        }
    }

    @Test fun blockersAssignToAttackersInOrderButNeverTrampleToPlayer() {
        val f = CombatDamageFixture(attackers = listOf("1/2", "1/3"), blockers = listOf("8/8"), keyword = "Trample")
        val result = f.assign(f.blockerViews)
        assertEquals(listOf("2", "6"), result.actions.filterIsInstance<PlayAction.AdjustDamage>().map { it.delta })
        assertEquals(f.attackerIds, result.actions.filterIsInstance<PlayAction.AdjustDamage>().map { it.cardId })
        assertNull(f.life(result))
    }

    @Test fun markedDamageAndAssignmentsEarlierInTheSamePressReduceLethal() {
        val f = CombatDamageFixture(attackers = listOf("2/2", "5/5"), blockers = listOf("1/4", "1/4"), marked = "1", note = "Trample")
        val result = f.assign(f.attackerViews.reversed())
        assertEquals(listOf("3", "3"), f.blockerIds.indices.map { f.damage(result, it) })
        assertEquals("-1", f.life(result))
    }

    @Test fun alreadyLethalBlockersNeedNoAdditionalDamageEvenWithDeathtouch() {
        val f = CombatDamageFixture(blockers = listOf("2/2"), marked = "2", note = "Deathtouch Trample")
        assertEquals(listOf(PlayAction.AdjustLife(f.defender, "-5")), f.assign().actions)
    }

    @Test fun effectivePowerAndToughnessIncludeEotModifiers() {
        val f = CombatDamageFixture(attackers = listOf("3/3"), blockers = listOf("1/2"), eotCorner = "2/1", note = "Trample")
        val state = f.play.copy(board = f.play.board.copy(cards = f.play.board.cards.map {
            if (it.id in f.blockerIds) it.copy(eotCorner = "0/2") else it
        }))
        val result = f.assign(state = state)
        assertEquals("4", f.damage(result))
        assertEquals("-1", f.life(result))
    }

    @Test fun displayOrderNotSelectionOrSourceIdOrderDeterminesRecipients() {
        val f = CombatDamageFixture(attackers = listOf("3/3"), blockers = listOf("1/2", "1/2"))
        val area = f.play.board.combat!!
        val reversed = f.play.copy(board = f.play.board.copy(combat = area.copy(pairs = area.pairs.map { it.copy(blockers = it.blockers.reversed()) })))
        assertEquals(listOf("2", "1"), f.assign().actions.filterIsInstance<PlayAction.AdjustDamage>().map { it.delta })
        val result = f.assign(state = reversed)
        assertEquals(listOf(f.blockerIds[1], f.blockerIds[0]), result.actions.filterIsInstance<PlayAction.AdjustDamage>().map { it.cardId })
        assertEquals(listOf("2", "1"), result.actions.filterIsInstance<PlayAction.AdjustDamage>().map { it.delta })
    }

    @Test fun dealersAlsoRunInDisplayOrderSoLaterAssignmentsUseEarlierMarks() {
        val f = CombatDamageFixture(attackers = listOf("2/2", "5/5"), blockers = listOf("1/3", "1/3"))
        val state = f.play.copy(board = f.play.board.copy(cards = f.play.board.cards.map { card ->
            when (card.id) {
                f.attackerIds[0] -> card.copy(note = "Deathtouch")
                f.attackerIds[1] -> card.copy(note = "Trample")
                else -> card
            }
        }))
        val result = f.assign(f.attackerViews.reversed(), state = state)
        assertEquals(listOf("1", "1"), f.blockerIds.indices.map { f.damage(result, it) })
        assertEquals("-5", f.life(result))
        val area = state.board.combat!!
        val reversed = state.copy(board = state.board.copy(combat = area.copy(pairs = area.pairs.map { it.copy(attackers = it.attackers.reversed()) })))
        val changed = f.assign(state = reversed)
        assertEquals(listOf("3", "4"), f.blockerIds.indices.map { f.damage(changed, it) })
        assertNull(f.life(changed))
    }

    @Test fun firstAndDoubleStrikeFilterBothSidesUsingCurrentNotesAndMetadata() {
        val f = CombatDamageFixture(attackers = listOf("1/1", "2/2", "3/3"))
        val state = f.play.copy(board = f.play.board.copy(cards = f.play.board.cards.mapIndexed { i, card ->
            when (i) { 0 -> card.copy(note = "FIRST STRIKE"); 1 -> card.copy(eotNote = "Double strike"); else -> card }
        }))
        assertTrue(CombatDamage.splitSteps(state))
        assertEquals("-3", f.life(f.assign(step = CombatDamageStep.FIRST_STRIKE, state = state)))
        assertEquals("-5", f.life(f.assign(step = CombatDamageStep.REGULAR, state = state)))
        assertEquals("-6", f.life(f.assign(state = state)))
        val printed = CombatDamageFixture(keyword = "Double strike")
        assertEquals("-5", printed.life(printed.assign(step = CombatDamageStep.FIRST_STRIKE)))
        assertEquals("-5", printed.life(printed.assign(step = CombatDamageStep.REGULAR)))
        val first = CombatDamageFixture(keyword = "First strike")
        assertEquals("-5", first.life(first.assign(step = CombatDamageStep.FIRST_STRIKE)))
        assertTrue(first.assign(step = CombatDamageStep.REGULAR).actions.isEmpty())
    }

    @Test fun doubleStrikeRemainsBlockedAfterFirstStrikeKillsTheOnlyBlocker() {
        for (trample in listOf(false, true)) {
            val f = CombatDamageFixture(attackers = listOf("3/3"), blockers = listOf("1/2"),
                note = "Double strike Lifelink" + if (trample) " Trample" else "")
            val state = f.play.copy(board = f.play.board.copy(containers = f.play.board.containers +
                PlayContainer(f.id(17), PlayZone.GRAVEYARD, f.defender)))
            val firstStrike = f.assign(step = CombatDamageStep.FIRST_STRIKE, state = state)
            assertEquals(if (trample) "2" else "3", f.damage(firstStrike))
            assertEquals("3", f.life(firstStrike, f.actor))
            var next = state
            var request = 9000
            fun apply(action: PlayAction) {
                val command = GameCommand(f.id(request++), GameScope(next.gameId, playId = next.id), GameAction.Play(action), f.id(99), "1")
                val result = GameplayRules.change(next, f.actor, command)
                next = assertNotNull(result.state, result.reason)
            }
            firstStrike.actions.forEach(::apply)
            val blocker = next.board.cards.single { it.id == f.blockerIds.single() }
            val graveyard = next.board.containers.single { it.zone == PlayZone.GRAVEYARD && it.accountId == f.defender }
            apply(PlayAction.MoveCard(blocker.id, MoveSource(blocker.containerId, blocker.placement),
                MoveDestination(graveyard.id, MoveInsertion.UNORDERED)))
            assertTrue(next.board.combat!!.pairs.single().blocked)
            assertTrue(next.board.combat!!.pairs.single().blockers.isEmpty())
            val regular = f.assign(step = CombatDamageStep.REGULAR, state = next)
            assertEquals(if (trample) "-3" else null, f.life(regular))
            assertEquals(if (trample) "3" else null, f.life(regular, f.actor))
            if (!trample) assertTrue(regular.actions.isEmpty())
        }
    }

    @Test fun lifelinkGainsAllDamageIncludingOverkillForControllerNotOwner() {
        val f = CombatDamageFixture(blockers = listOf("1/1"), keyword = "Lifelink")
        val state = f.play.copy(board = f.play.board.copy(cards = f.play.board.cards.map {
            if (it.id in f.attackerIds) it.copy(owner = f.defender) else it
        }))
        val result = f.assign(state = state)
        assertEquals("5", f.damage(result))
        assertEquals("5", f.life(result, f.actor))
        assertNull(f.life(result))
        val alone = CombatDamageFixture(note = "trample lifelink", blockers = listOf("1/2"))
        assertEquals("5", alone.life(alone.assign(), alone.actor))
        assertEquals("-3", alone.life(alone.assign()))
    }

    @Test fun infectWitherAndNonNumericPowerSkipWithManualNoteButOtherCreaturesStillDeal() {
        for (f in listOf(CombatDamageFixture(keyword = "Infect"), CombatDamageFixture(eotNote = "WITHER"),
            CombatDamageFixture(attackers = listOf("*/2")))) {
            assertTrue(f.assign().actions.isEmpty())
            assertEquals(listOf("Combatant: assign manually"), f.assign().notes)
        }
        val mixed = CombatDamageFixture(attackers = listOf("*/2", "3/3"))
        assertEquals("-3", mixed.life(mixed.assign()))
        assertEquals(listOf("Combatant: assign manually"), mixed.assign().notes)
    }

    @Test fun faceDownCombatantsIgnoreHiddenKeywordsAndNeverNameTheHiddenCard() {
        val f = CombatDamageFixture(keyword = "Infect")
        fun faceDown(corner: String) = f.play.copy(board = f.play.board.copy(cards = f.play.board.cards.map {
            if (it.id in f.attackerIds) it.copy(covered = true, explicitlyCovered = true, corner = corner) else it }))
        assertEquals("-2", f.life(f.assign(state = faceDown("2/2"))))
        assertTrue(f.assign(state = faceDown("2/2")).notes.isEmpty())
        assertEquals(listOf("Face-down card: assign manually"), f.assign(state = faceDown("*/2")).notes)
    }

    @Test fun keywordMatchingUsesWholeWordsAndOnlyCurrentFaceMetadataNotRulesText() {
        val f = CombatDamageFixture(note = "notinfect notwithered trampler", blockers = listOf("1/1"))
        assertEquals("5", f.damage(f.assign()))
        assertNull(f.life(f.assign()))
        val state = f.play.copy(definitions = f.play.definitions.map { submitted -> submitted.copy(definition = submitted.definition.copy(
            faces = submitted.definition.faces.map { it.copy(sourceData = "not json", rulesText = "Trample, Infect") })) })
        assertEquals("5", f.damage(f.assign(state = state)))
        assertTrue(f.assign(state = state).notes.isEmpty())
    }

    @Test fun planeswalkerAndBattleTargetsLoseCornerNotLifeOrMarkedDamage() {
        val f = CombatDamageFixture(blockers = listOf("1/2"), targetCorner = "4", note = "Trample Lifelink")
        val result = f.assign()
        assertEquals(PlayAction.SetCorner(f.target, "1"), result.actions.filterIsInstance<PlayAction.SetCorner>().single())
        assertEquals("2", f.damage(result))
        assertEquals("5", f.life(result, f.actor))
        assertNull(f.life(result))
        assertEquals(PlayAction.SetCorner(f.target, "-1"), f.assign(targetOnly = true).actions.filterIsInstance<PlayAction.SetCorner>().single())
        val battle = f.play.copy(definitions = f.play.definitions.map { submitted -> submitted.copy(definition = submitted.definition.copy(
            faces = submitted.definition.faces.map { if (it.typeLine == "Planeswalker") it.copy(typeLine = "Battle", loyalty = "", defense = "4") else it })) })
        assertEquals(result, f.assign(state = battle))
    }

    @Test fun unknownRecipientNumbersDoNotProducePartialDamageOrLifelink() {
        val f = CombatDamageFixture(blockers = listOf("1/2", "1/*"), note = "Lifelink")
        assertTrue(f.assign().actions.isEmpty())
        assertEquals(listOf("Combatant: assign manually"), f.assign().notes)
        val noAttackers = CombatDamageFixture(attackers = emptyList(), blockers = listOf("4/4"), keyword = "Lifelink")
        assertTrue(noAttackers.assign(noAttackers.blockerViews).actions.isEmpty())
    }
}
