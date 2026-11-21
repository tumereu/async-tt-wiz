package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.api.PredefinedTokens
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.cards.Cards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a card's menu offers on a game's own engine state ([CardPlays], I-D28) and activating through the cast panel's
 * flow ([CastFlow]). */
class EngineCardPlaysTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val bob get() = table.bob
    private fun start(state: arcanum.engine.state.GameState, input: Input) =
        CastFlow.start(table.source(state), alice, input, CastFlow.Seeds(table::seed))

    // TBL-115 (CR 602.2, 605.3a): each activated ability with its printed text, the legal ones with the engine's input,
    // the others greyed with why; a land's Play; the other player's abilities greyed as theirs; out of priority the
    // holder is named
    @Test fun abilitiesListedLegalAndRefused() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (seer, workshop) = table.put(alice, Zone.Battlefield, "Elvish Seer", "Refitting Workshop")
        val (bobSeer) = table.put(bob, Zone.Battlefield, "Elvish Seer")
        val (land) = table.put(alice, Zone.Hand, "Mountain")
        val plays = table.plays()
        val mana = plays.of(seer).single()
        assertEquals(CardPlay.Kind.Mana, mana.kind)
        assertEquals("{T}: Add {G}.", mana.text)
        assertIs<Input.Activate>(mana.input)
        val tools = plays.of(workshop)
        assertTrue(tools.first { it.text.startsWith("{2}, {T}") }.legal, "two Mountains pay {2}")
        val thopter = tools.first { it.text.startsWith("{1}, {T}, Sacrifice a Servo") }
        assertNull(thopter.input, "no Servo to sacrifice")
        assertIs<CardPlay.Refusal.Other>(thopter.refusal)
        assertEquals(CardPlay.Refusal.NotYours, plays.of(bobSeer).single().refusal)
        assertEquals(CardPlay.Kind.Play, plays.of(land).single().kind)
        assertEquals(CardPlay.Refusal.Priority(alice), table.plays(viewer = bob).of(bobSeer).single().refusal)
    }

    // SYS-067, TBL-126 (CR 605.3a, 117.3c): a mana ability outside a payment goes out at once and its player keeps priority
    @Test fun aManaAbilityKeepsPriority() {
        val (seer) = table.put(alice, Zone.Battlefield, "Elvish Seer")
        val state = table.state()
        val flow = start(state, assertNotNull(table.plays(state).of(seer).single().input))
        assertNull(flow.pending)
        assertEquals(PlayerId(alice), flow.state.priority.holder)
        assertEquals(1, flow.state.player(PlayerId(alice)).manaPool.size)
    }

    // SYS-067 (CR 305.2): lands played through Automatum count one per turn: after the first, a second is greyed as used
    @Test fun aSecondLandIsGreyedAsUsed() {
        val (first, second) = table.put(alice, Zone.Hand, "Mountain", "Mountain")
        val state = table.state()
        val played = start(state, assertNotNull(table.plays(state).of(first).single().input)).state
        val greyed = table.plays(played).of(second).single()
        assertNull(greyed.input)
        assertEquals(CardPlay.Refusal.Used, greyed.refusal)
    }

    // TBL-126 (CR 602.2): an ability with a cost to pay opens the cast panel's payment, and the activation goes out
    // with the suggested payment
    @Test fun activatingAsksThePayment() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (workshop) = table.put(alice, Zone.Battlefield, "Refitting Workshop")
        val state = table.state()
        val servo = table.plays(state).of(workshop).first { it.text.startsWith("{2}, {T}") }
        var flow = start(state, assertNotNull(servo.input))
        assertEquals(workshop, flow.cardId)
        assertTrue(flow.activation)
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        flow = flow.answer(Answer.ManaPayment(payment.suggestion))
        assertNull(flow.pending)
        assertEquals(1, flow.state.stack.size, "the ability is on the Stack")
    }

    /** Records the faults reported while [block] runs. */
    private fun <T> faults(block: () -> T): Pair<T, List<org.asyncttwiz.protocol.Fault>> {
        val faults = mutableListOf<org.asyncttwiz.protocol.Fault>()
        val sink = org.asyncttwiz.protocol.Faults.sink
        org.asyncttwiz.protocol.Faults.sink = { faults += it }
        try { return block() to faults } finally { org.asyncttwiz.protocol.Faults.sink = sink }
    }

    /** A predefined token (CR 111.10) as the engine makes it: its face, and its back face when double-faced. */
    private fun predefined(name: String) = PredefinedTokens.back(name).let { back ->
        CardDefinition(if (back == null) Layout.Normal else Layout.Transform, listOfNotNull(PredefinedTokens.face(name), back))
    }

    // TBL-115, TBL-124, SYS-086 (CR 111.10, owner fault 2PTQG5: a Food's chip read "{2}, {T}, Sacrifice: …"): every predefined
    // token's activated ability reads in its menu and chips as the rules word it, never as a stand-in
    @Test fun predefinedTokensAbilitiesReadAsTheRulesWordThem() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val tokens = PredefinedTokens.names.associateWith { predefined(it) }
            .filterValues { d -> d.faces.first().abilities.any { it is Ability.Activated } }
            .mapValues { (_, d) -> table.putToken(alice, d) to d.faces.first().abilities.filterIsInstance<Ability.Activated>() }
        assertTrue(listOf("Food", "Treasure", "Clue", "Map", "Incubator").all { it in tokens })
        val (plays, faults) = faults { table.plays() }
        tokens.forEach { (name, token) ->
            val (id, abilities) = token
            assertEquals(abilities.map { assertNotNull(it.text, name) }, plays.of(id).map { it.text }, name)
        }
        assertEquals("{2}, {T}, Sacrifice this token: You gain 3 life.", plays.of(tokens.getValue("Food").first).single().text)
        assertEquals(emptyList(), faults)
    }

    // STK-022, SYS-086 (CR 111.10b, 111.7, owner fault 2PTQG5): a Food's ability on the Stack, its Food sacrificed, reads as
    // the rules word it, and an entry of its own names the Food as its source
    @Test fun aFoodsAbilityOnTheStackReadsItsText() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val food = table.putToken(alice, predefined("Food"))
        val (state, faults) = faults {
            val state = table.state()
            var flow = start(state, assertNotNull(table.plays(state).of(food).single().input))
            val payment = assertIs<Decision.ManaPayment>(flow.pending)
            flow = flow.answer(Answer.ManaPayment(payment.suggestion))
            assertNull(flow.pending)
            flow.state
        }
        assertEquals(1, state.stack.size, "the ability is on the Stack")
        val (projected, projectionFaults) = faults {
            EngineProjection.project(table.base(), org.asyncttwiz.protocol.game.GameModel(state.deepCopy(), org.asyncttwiz.protocol.game.TableState()), alice, "node")
        }
        val entry = projected.board.stackEffects.single()
        assertEquals("{2}, {T}, Sacrifice this token: You gain 3 life.", StackAbilityText.of(entry.item!!))
        entry.frozen?.let { own ->
            val face = projected.definitions.single { it.digest == own.definition }.definition.faces.single()
            assertEquals("Food Token", face.name)
            assertEquals("{2}, {T}, Sacrifice this token: You gain 3 life.", face.rulesText)
        }
        assertEquals(emptyList(), faults + projectionFaults)
    }

    private val room = Cards.inventFaces("Split",
        arcanum.engine.cards.face("Lamplit Parlor", "{1}{W}", "Enchantment — Room", "When you unlock this door, you gain 2 life.", null, null, null),
        arcanum.engine.cards.face("Dusty Attic", "{2}{W}", "Enchantment — Room", "Creatures you control get +1/+0.", null, null, null),
    )

    // TBL-115, TBL-232, SYS-153 (CR 709.5e, prod faults 9SF97R, P86WUK: the unlock read as the placeholder "Special action"; owner 2026-11-20:
    // as an ability reads): a locked door's unlocking is offered as its cost and its name, "{2}{W}: Unlock Dusty Attic"
    @Test fun unlockingADoorIsOfferedByItsName() {
        table.put(alice, Zone.Battlefield, "Plains", "Plains", "Plains")
        val (parlor) = table.put(alice, Zone.Battlefield, room)
        table.update(parlor) { it.copy(marks = it.marks + arcanum.api.ObjectMark.Unlocked(arcanum.api.Door.Left)) }
        val (plays, faults) = faults { table.plays() }
        val unlock = plays.of(parlor).single { it.kind == CardPlay.Kind.Special }
        assertEquals("{2}{W}: Unlock Dusty Attic", unlock.text)
        assertIs<Input.Activate>(unlock.input)
        assertEquals(emptyList(), faults)
    }

    // TBL-115, TBL-232 (CR 701.40b; owner 2026-11-20: as an ability reads): a manifested creature card's turning face up is offered as its mana
    // cost, then "Turn face up", never as a placeholder
    @Test fun turningAManifestedCardFaceUpIsNamed() {
        table.put(alice, Zone.Battlefield, "Forest", "Forest", "Forest")
        val (seer) = table.put(alice, Zone.Battlefield, "Elvish Seer")
        table.update(seer) { it.copy(faceDown = true, marks = it.marks + arcanum.api.ObjectMark.Manifested) }
        val (plays, faults) = faults { table.plays() }
        val faceUp = plays.of(seer).single { it.kind == CardPlay.Kind.Special }
        assertEquals("${Cards.definition("Elvish Seer").faces.single().manaCost}: Turn face up", faceUp.text)
        assertEquals(emptyList(), faults)
    }

    private val mask = Cards.invent("Ember Mask", "{2}{R}", "Creature — Elemental", "Morph {1}{R}", "2/2")
    private val plotter = Cards.invent("Ember Plotter", "{3}{R}", "Sorcery", "Ember Plotter deals 2 damage to any target.\nPlot {1}{R}")
    private val comet = Cards.invent("Ember Comet", "{4}{R}", "Sorcery", "Ember Comet deals 5 damage to any target.\nSuspend 3—{R}")

    // TBL-115, TBL-232 (CR 702.37e; owner 2026-11-20: a special action reads as an ability): a face-down morph's turning face up is offered as its
    // morph cost, then "Turn face up"
    @Test fun turningAMorphFaceUpReadsAsAnAbility() {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (face) = table.put(alice, Zone.Battlefield, mask)
        table.update(face) { it.copy(faceDown = true) }
        val (plays, faults) = faults { table.plays() }
        assertEquals("{1}{R}: Turn face up", plays.of(face).single { it.kind == CardPlay.Kind.Special }.text)
        assertEquals(emptyList(), faults)
    }

    // TBL-115, TBL-232 (CR 702.170a, 702.62a, 702.143b; owner 2026-11-20): plot, suspend and foretell read as abilities: "{1}{R}: Plot",
    // "{R}: Suspend 3" (its time counters kept), "{2}: Foretell" instead of the reminder sentence
    @Test fun plotSuspendAndForetellReadAsAbilities() {
        val (plot, suspend) = table.put(alice, Zone.Hand, plotter, comet)
        val (plays, faults) = faults { table.plays() }
        assertEquals("{1}{R}: Plot", plays.of(plot).single { it.kind == CardPlay.Kind.Special }.text)
        assertEquals("{R}: Suspend 3", plays.of(suspend).single { it.kind == CardPlay.Kind.Special }.text)
        assertEquals("{2}: Foretell", CastFlow.specialActionText(null, arcanum.engine.view.AbilityOrigin.Rule(CastFlow.FORETELL)))
        assertEquals(emptyList(), faults)
    }
}
