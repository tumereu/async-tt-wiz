package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackAction
import org.asyncttwiz.protocol.CommandIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Engine calls on a player's own board (D60, integration spec §2): hidden cards are unknown objects; the host fills them
 * from ATW's card data, answers shuffles with ATW's seeded shuffle, and hands steps only the other player may see to
 * them (D31). Each call is checked by [RoundTrip] on what that player can see. */
class ViewerRoundTripTest {
    private val table = AtwTable("viewer")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private fun zone(rt: RoundTrip, card: String) =
        rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone

    // D60: Bob's library cards are unknown on Alice's board; milled into his graveyard they are filled from ATW's data
    @Test fun hiddenCardsAreFilledFromAtwsCardData() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island")
        table.put(alice, PlayZone.HAND, "Thought Siphon")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Island")
        val milled = table.put(bob, PlayZone.LIBRARY, "Storm Spark", "Curse Word", "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        val view = rt.engine(alice)
        assertTrue(view.player(PlayerId(bob)).library.all { view.objOrThrow(it).unknown }, "Bob's library is hidden from Alice")
        rt.cast(alice, "Thought Siphon", viewer = alice)
        rt.perform(Input.Answer(rt.engine(alice).pending!!.decision.id, Answer.Targets(listOf(TargetRef.Player(PlayerId(bob))))), alice, alice)
        val payment = rt.engine(alice).pending!!.decision as Decision.ManaPayment
        rt.perform(Input.Answer(payment.id, Answer.ManaPayment(payment.suggestion)), alice, alice)
        rt.pass(alice, viewer = alice)
        val call = rt.pass(bob, viewer = alice)
        assertTrue(call.changes.any { it is arcanum.engine.change.Change.Filled }, "the host filled the milled cards")
        assertEquals(listOf(PlayZone.GRAVEYARD, PlayZone.GRAVEYARD, PlayZone.LIBRARY), milled.map { zone(rt, it) })
        val after = rt.engine(alice)
        assertEquals(setOf("Storm Spark", "Curse Word"),
            after.player(PlayerId(bob)).graveyard.map { after.objOrThrow(it).definition!!.faces.first().name }.toSet())
    }

    // D60, D38: Alice searches her own library on her board (the host fills it for her) and shuffles; her board's
    // shuffled cards get new ids only this call knows, ATW shuffles with the same seed
    @Test fun ownSearchAndShuffleOnOwnBoard() {
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp")
        table.put(alice, PlayZone.HAND, "Fiendish Mentor")
        table.put(alice, PlayZone.LIBRARY, "Island", "Storm Spark", "Mountain", "Forest", "Plains", "Swamp")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.cast(alice, "Fiendish Mentor", viewer = alice)
        val payment = rt.engine(alice).pending!!.decision as Decision.ManaPayment
        rt.perform(Input.Answer(payment.id, Answer.ManaPayment(payment.suggestion)), alice, alice)
        rt.pass(alice, viewer = alice)
        rt.pass(bob, viewer = alice)
        val search = rt.engine(alice).pending!!.decision as Decision.ChooseObjects
        // Alice's client names the matches from ATW's card data: her rebuilt board shows only what ATW shows her, and ATW
        // doesn't keep the search's fill (a documented difference of a viewer's board)
        val spark = search.options.single { rt.engine().objOrThrow(it).definition?.faces?.first()?.name == "Storm Spark" }
        val call = rt.perform(Input.Answer(search.id, Answer.Objects(listOf(spark))), alice, alice)
        assertTrue(call.hidden.isNotEmpty(), "the shuffle gave Alice's board new ids (D60)")
        assertTrue(rt.lastPlan!!.actions.any { it is PlayAction.ShuffleLibrary })
        val library = rt.state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == alice }
        assertEquals(5, library.cards.size)
        assertTrue(rt.engine(alice).player(PlayerId(alice)).library.all { rt.engine(alice).objOrThrow(it).unknown })
        // The full-information board agrees: the rebuilt state's library is ATW's order
        assertEquals(library.cards, rt.engine().player(PlayerId(alice)).library.map { (it as arcanum.engine.state.ObjectId.Card).card.value })
    }

    // D31: Bob's scry resolves on Alice's board (she passed last); the step only Bob may see is handed to him, who decides
    // it in his view; the flow goes on in Alice's view
    @Test fun aStepOnlyTheOtherPlayerSeesIsHandedToThem() {
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Island")
        table.put(bob, PlayZone.HAND, "Glance Ahead")
        table.put(bob, PlayZone.LIBRARY, "Island", "Forest", "Storm Spark")
        table.priority = org.asyncttwiz.protocol.MtgPriority(bob)
        val rt = RoundTrip(table.state(), table.definitions)
        rt.cast(bob, "Glance Ahead", viewer = bob)
        val payment = rt.engine(bob).pending!!.decision as Decision.ManaPayment
        rt.perform(Input.Answer(payment.id, Answer.ManaPayment(payment.suggestion)), bob, bob)
        rt.pass(bob, viewer = bob)
        rt.pass(alice, viewer = alice)
        val handed = rt.engine(alice).pending?.decision
        assertIs<Decision.OtherView>(handed)
        assertEquals(PlayerId(bob), handed.player)
        assertEquals(alice, EngineState.flowViewer(rt.state.board), "the flow was made on Alice's board")
        // Bob's client: the flow's view, his cards filled in, his decision there
        val call = EngineCall(rt.state, rt.definitions, alice, EngineLog.from(rt.tags, rt.state, rt.definitions), rt::seed)
        val filled = call.otherView(handed)
        val scry = Engine.otherViewDecision(call.working, filled)
        assertIs<Decision.ChooseObjects>(scry)
        call.perform(Input.Answer(handed.id, Answer.OtherView(filled, listOf(Answer.Objects(scry.options.take(1))))))
        rt.apply(call, bob)
        assertNull(rt.engine(alice).pending)
        val library = rt.state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == bob }
        assertEquals(2, library.cards.size, "scried one to the bottom, drew one")
    }

    // §5: what the engine can't take over is said, not guessed
    @Test fun manualStackEntriesAndTempPilesAreReported() {
        val spark = table.put(alice, PlayZone.HAND, "Storm Spark").single()
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        val state = table.state()
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), GameScope(state.gameId, playId = state.id),
            GameAction.Play(PlayAction.Stack(StackAction.AddEffect(spark, GameplayRules.stackDigest(state)))),
            CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val manual = GameplayRules.change(state, alice, command).state!!
        val problems = EngineState.unsupported(manual, table.definitions)
        assertTrue(problems.single().contains("manual entry"), "$problems")
        assertFailsWith<Unsupported> { EngineState.from(manual, table.definitions) }
        assertTrue(EngineState.unsupported(state, table.definitions).isEmpty())
        // I-D21: a card the device couldn't read plays with its printed values and manual abilities
        assertTrue(EngineState.unsupported(state, emptyMap()).isEmpty(), "cards without definitions")
        assertTrue(rt(state).engine(bob).objects.values.all { it.unknown })
    }

    private fun rt(state: org.asyncttwiz.protocol.PlayState) = RoundTrip(state, table.definitions)
}
