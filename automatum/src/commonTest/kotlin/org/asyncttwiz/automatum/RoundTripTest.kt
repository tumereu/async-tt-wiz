package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The I0 acceptance check (integration spec §3): engine flows driven through ATW's board, actions and reducer. Every
 * engine call in these tests is checked by [RoundTrip]: the state rebuilt from the new ATW board equals the engine's own
 * result, and undoing the call's batch gives the board back.
 */
class RoundTripTest {
    private val table = AtwTable()
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun game(): RoundTrip = RoundTrip(table.state(), table.definitions)
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life
    private fun zone(rt: RoundTrip, card: String) = rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone

    // CR 601.2, 608.2: a burn spell is cast (an entry pointing at its card, STK-001) and resolves
    @Test fun burnSpellIsCastAndResolves() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val spark = table.put(alice, PlayZone.HAND, "Storm Spark").single()
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.cast(alice, "Storm Spark")
        // The open cast is a continuation on the board (D58)
        assertNotNull(rt.state.board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID })
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.settle()
        val entry = rt.state.board.stackEffects.single()
        assertEquals(spark, entry.sourceId)
        assertEquals(MtgStackKind.Spell, entry.item?.kind)
        assertEquals(PlayZone.HAND, zone(rt, spark), "the card stays where it was cast from (STK-003)")
        rt.resolveAll()
        assertEquals("17", life(rt, bob))
        assertEquals(PlayZone.GRAVEYARD, zone(rt, spark))
        assertTrue(rt.state.board.stackEffects.isEmpty())
        assertNull(rt.state.board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID })
    }

    // I-D11: a private cast keeps its engine calls on the device and sends them as one batch when the cast completes
    @Test fun privateCastIsOneBatch() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = game()
        val call = EngineCall(rt.state, rt.definitions, log = EngineLog.from(rt.tags, rt.state, rt.definitions), seeds = rt::seed)
        val spark = rt.obj("Storm Spark", Zone.Hand)
        call.perform(Engine.options(call.working, PlayerId(alice)).filterIsInstance<Input.Cast>().single { it.card == spark })
        val targets = call.pending!!.decision as Decision.Targets
        call.perform(Input.Answer(targets.id, Answer.Targets(listOf(TargetRef.Player(PlayerId(bob))))))
        val payment = call.pending!!.decision as Decision.ManaPayment
        call.perform(Input.Answer(payment.id, Answer.ManaPayment(payment.suggestion)))
        rt.apply(call, alice)
        assertEquals(1, rt.batches.size)
        assertNull(rt.state.board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID })
        assertEquals(MtgStackKind.Spell, rt.state.board.stackEffects.single().item?.kind)
    }

    // D58: cancelling a cast takes back everything it did, here inside one shared call each
    @Test fun cancelledCastLeavesTheBoardAsItWas() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = game()
        val before = rt.engine()
        rt.cast(alice, "Storm Spark")
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.perform(Input.Cancel(PlayerId(alice)), alice)
        val after = rt.engine()
        assertTrue(rt.state.board.stackEffects.isEmpty())
        assertEquals(before.players, after.players)
        assertEquals(before.objects.keys, after.objects.keys, "zone-change counts are back (D59)")
    }

    // CR 603.2, 603.3: a creature spell resolves onto the battlefield; its enters trigger goes on the Stack and draws
    @Test fun creatureEntersAndItsTriggerDraws() {
        table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest", "Forest")
        val visionary = table.put(alice, PlayZone.HAND, "Canopy Visionary").single()
        val top = table.put(alice, PlayZone.LIBRARY, "Mountain", "Island").first()
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.cast(alice, "Canopy Visionary")
        rt.settle()
        rt.pass(alice); rt.pass(bob)
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, visionary))
        val trigger = rt.state.board.stackEffects.single()
        assertEquals(visionary, trigger.sourceId)
        assertEquals(MtgStackKind.Triggered, trigger.item?.kind)
        rt.resolveAll()
        assertEquals(PlayZone.HAND, zone(rt, top))
        assertTrue(rt.state.board.stackEffects.isEmpty())
    }

    // CR 701.8, 700.4, 702.79a: removal destroys a creature with persist; it dies and returns with a -1/-1 counter
    @Test fun removalKillsAndTheDiesTriggerReturnsItWithACounter() {
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp")
        table.put(alice, PlayZone.HAND, "Curse Word")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Swamp")
        val gremlin = table.put(bob, PlayZone.BATTLEFIELD, "Festering Gremlin").single()
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.cast(alice, "Curse Word")
        rt.target(TargetRef.Object(rt.obj("Festering Gremlin", Zone.Battlefield)))
        rt.settle()
        rt.resolveAll()
        rt.resolveAll()
        val card = rt.state.board.cards.single { it.id == gremlin }
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, gremlin))
        assertEquals(listOf("-1/-1"), card.typedCounters.map { it.kind })
        assertEquals(2, card.zoneChanges, "died and returned: two zone changes (D21)")
        // The history says it died (D33)
        assertTrue(rt.tags.any { it != null && "died" in it.meanings })
    }

    // CR 508, 509, 510: an attacker blocked and unblocked deals combat damage
    @Test fun combatWithDamage() {
        table.put(alice, PlayZone.BATTLEFIELD, "Festering Gremlin", "Tinkered Mite")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Swamp")
        table.put(bob, PlayZone.BATTLEFIELD, "Tinkered Mite")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.pass(alice); rt.pass(bob)
        assertEquals(TurnPosition.BEGIN_COMBAT, TurnPosition.of(rt.state.board.turn))
        rt.pass(alice); rt.pass(bob)
        val state = rt.engine()
        val attackers = state.battlefield.filter { state.objOrThrow(it).controller?.value == alice }
        rt.perform(Input.DeclareAttackers(PlayerId(alice), attackers.map { Attack(it, TargetRef.Player(PlayerId(bob))) }), alice)
        assertEquals(2, rt.state.board.combat!!.pairs.size, "the lanes show the attackers")
        rt.pass(alice); rt.pass(bob)
        val bobMite = rt.engine().battlefield.single { rt.engine().objOrThrow(it).controller?.value == bob }
        val gremlin = rt.obj("Festering Gremlin", Zone.Battlefield)
        rt.perform(Input.DeclareBlockers(PlayerId(bob), listOf(Block(bobMite, gremlin))), bob)
        while (rt.engine().turn.step != arcanum.api.TurnStep.EndOfCombat && rt.decision == null) rt.pass(rt.engine().priority.holder!!.value)
        assertEquals("19", life(rt, bob))
    }

    // CR 111.1: a sorcery creates two tokens; ATW makes its own token cards and keeps the engine's ids (D35)
    @Test fun tokensAreCreated() {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Lingering Shades")
        table.put(alice, PlayZone.LIBRARY, "Plains", "Plains")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.cast(alice, "Lingering Shades")
        rt.settle()
        rt.resolveAll()
        val tokens = rt.state.board.cards.filter { it.token }
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { zone(rt, it.id) == PlayZone.BATTLEFIELD })
        assertTrue(rt.engine().battlefield.map { rt.engine().objOrThrow(it) }.count { it.token } == 2)
    }

    // CR 106.4, 605: a mana ability fills the pool; a spell is paid from it; the pool empties at the step's end
    @Test fun manaPool() {
        table.put(alice, PlayZone.BATTLEFIELD, "Garnet Bauble")
        table.put(alice, PlayZone.HAND, "Storm Spark", "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.activate(alice, "Garnet Bauble")
        rt.settle()
        assertEquals(listOf("R"), rt.state.board.players.single { it.accountId == alice }.manaPool.map { it.color })
        rt.cast(alice, "Storm Spark")
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.settle()
        assertTrue(rt.state.board.players.single { it.accountId == alice }.manaPool.isEmpty())
        rt.resolveAll()
        assertEquals("17", life(rt, bob))
    }

    // D58, I-D8: a shared flow's open decision is stored on the ATW board and resumed from it by the other player (D11)
    @Test fun sharedContinuationIsStoredAndResumedFromTheBoard() {
        table.put(alice, PlayZone.HAND, "Chromed Locket", "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.cast(alice, "Chromed Locket")
        rt.settle()
        rt.pass(alice); rt.pass(bob)
        rt.pass(alice); rt.pass(bob)
        // The trigger's "you may exile" waits on the board, in the continuation record
        assertNotNull(rt.state.board.engineRecords.singleOrNull { it.id == Automatum.CONTINUATION_ID })
        val choice = rt.decision as Decision.ChooseObjects
        assertEquals(PlayerId(alice), choice.player)
        // Bob answers for Alice (D11) from a fresh engine state built from the board alone
        val spark = rt.obj("Storm Spark", Zone.Hand)
        rt.perform(Input.Answer(choice.id, Answer.Objects(listOf(spark))), bob)
        rt.settle()
        assertTrue(rt.state.board.cards.any { it.id in rt.state.board.containers.single { c -> c.zone == PlayZone.EXILE && c.accountId == alice }.cards })
    }

    // CR 701.24, D38: a search and a shuffle, said with ATW's own seeded ShuffleLibrary
    @Test fun searchAndShuffle() {
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp")
        table.put(alice, PlayZone.HAND, "Fiendish Mentor")
        table.put(alice, PlayZone.LIBRARY, "Island", "Storm Spark", "Mountain", "Forest", "Plains", "Swamp")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        rt.cast(alice, "Fiendish Mentor")
        rt.settle()
        rt.resolveAll()
        rt.answer { it is Answer.Objects && it.chosen.size == 1 && rt.name(rt.engine().obj(it.chosen.single())) == "Storm Spark" }
        val library = rt.state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == alice }
        assertEquals(5, library.cards.size)
        assertTrue(rt.state.board.cards.filter { it.id in library.cards }.all { it.zoneChanges == 1 }, "a shuffle makes new objects (D38)")
        assertTrue(rt.lastPlan!!.actions.any { it is org.asyncttwiz.protocol.PlayAction.ShuffleLibrary })
    }
}
