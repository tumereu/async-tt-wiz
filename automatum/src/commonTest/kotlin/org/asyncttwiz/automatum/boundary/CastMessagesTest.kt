package org.asyncttwiz.automatum.boundary

import arcanum.api.Zone
import arcanum.engine.Host
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.builtins.ListSerializer
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.EngineTable
import org.asyncttwiz.automatum.HandCasts
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Web client step 4e (docs/specs/web-client.md §1 item 3): cast flow sessions across the engine boundary. The panel's
 * values and every step come back the same from their JSON text; the page's copy of the board with a flow's changes is the
 * flow's own state; the engine side's sessions keep a few versions of each flow, refuse a step on a version or session the
 * page no longer shows as stale, and log each opening, step, refusal and end.
 */
class CastMessagesTest {
    init { arcanum.engine.cards.Cards.invent("Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2") }
    private val table = EngineTable()
    private val alice get() = table.alice
    private val logged = mutableListOf<Pair<Boolean, String>>()
    private val log = EngineLog { warn, text -> logged += warn to text }

    /** Storm Spark cast with its target given, on two Mountains: the flow at its payment. */
    private fun sparkAtItsPayment(): Triple<CastFlow, arcanum.engine.state.GameState, ObjectId> {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        val (bear) = table.put(table.bob, Zone.Battlefield, "Stout Bear")
        val (spark) = table.put(alice, Zone.Hand, "Storm Spark")
        val state = table.state()
        val begin = HandPlays.of(state, alice, null).of(spark).first()
        val started = CastFlow.start(table.source(state), alice, begin, CastFlow.Seeds(table::seed))
        val targets = assertIs<Decision.Targets>(started.pending)
        val target = TargetRef.Object(table.obj(bear))
        assertTrue(target in targets.options)
        val flow = started.answer(Answer.Targets(listOf(target)))
        assertIs<Decision.ManaPayment>(flow.pending, "on to its payment")
        return Triple(flow, state, table.obj(bear))
    }

    // Web client step 4e, STK-024, STK-023: the cast panel's value of a real flow (its answered target, its open payment with
    // Autotap's suggestion, what the caster may see) comes back the same from its text, inside the device's answer
    @Test fun aCastPanelValueComesBackTheSameFromItsText() {
        val (flow, _, _) = sparkAtItsPayment()
        val payment = flow.pending as Decision.ManaPayment
        val codec = EngineJson(GameDefinitions(flow.state.objects.values.mapNotNull { it.definition }.distinct()))
        val value = CastPanelValue(
            CastFlowValue(3, fresh = false, flow.cardId, flow.begin, flow.steps.map { (d, a) -> CastStepValue(d, a) }, flow.pending,
                flow.call.pending?.continuation, codec.encodeElement(ListSerializer(Change.serializer()), flow.call.changes)),
            mana = payment.suggestion, paid = flow.pays(payment.suggestion), visible = Host.visible(flow.state, PlayerId(alice)),
            ways = listOf(flow.begin), most = listOf(AnnouncedMost(payment.id, 4, setOf(2))), payment = payment,
            fieldSources = payment.options.mapNotNullTo(HashSet()) { (it as? arcanum.engine.decision.ManaUse.Ability)?.source },
            selection = SelectionWire(listOf(TargetRef.Player(PlayerId(table.bob))), true), choosing = false)
        val answer = DeviceAnswer.Done(7, DeviceResult.Cast(CastAnswer.Show(value)))
        val back = DeviceMessage.decode(DeviceMessage.answer("game", answer)).answer
        assertEquals(answer, back)
        val shown = assertIs<CastAnswer.Show>(assertIs<DeviceResult.Cast>(assertIs<DeviceAnswer.Done>(back).result).answer).panel
        assertEquals(flow.steps.map { it.first }, shown.flow.steps.map { it.decision })
        assertEquals(payment, shown.payment)
        assertTrue(shown.paid)
    }

    // Web client step 4e: the page's copy of the board's engine state with the flow's changes applied straight is the flow's
    // own state (the panel names and reads from it, nothing worked out again on the page)
    @Test fun theBoardsStateWithTheFlowsChangesIsTheFlowsState() {
        val (flow, state, _) = sparkAtItsPayment()
        val codec = EngineJson(GameDefinitions(flow.state.objects.values.mapNotNull { it.definition }.distinct()))
        val sent = codec.encodeElement(ListSerializer(Change.serializer()), flow.call.changes)
        val changes = codec.decodeElement(ListSerializer(Change.serializer()), sent)
        val page = state.deepCopy().also { s -> changes.forEach(s::apply) }
        assertEquals(codec.encodeModel(GameModel(flow.state, TableState())), codec.encodeModel(GameModel(page, TableState())))
    }

    // Web client step 4e: every step of a session comes back the same from its text inside the device's request
    @Test fun everyStepComesBackTheSameFromItsText() {
        val (flow, _, bear) = sparkAtItsPayment()
        val payment = flow.pending as Decision.ManaPayment
        val use = payment.options.first()
        val taps = ManaTapsWire(tapped = listOf(use))
        val choices = CastChoices(autotap = false, keep = listOf(use), custom = true, taps = taps)
        val chips = HandCasts.chips(table.source(table.state()), table.definitions, alice, flow.cardId,
            HandPlays.of(table.state(), alice, null).of(flow.cardId), CastFlow.Seeds(table::seed))
        val ops = listOf(
            CastOp.Start(flow.cardId, flow.begin, listOf(1), autotap = false),
            CastOp.FromHand(flow.cardId, chips),
            CastOp.Begin(flow.cardId, flow.begin),
            CastOp.HandChoice(1, listOf(0, 2), choices),
            CastOp.Update(CastChange.Give(Answer.Targets(listOf(TargetRef.Object(bear)))), choices),
            CastOp.Update(CastChange.Change(0, Answer.Number(2)), choices),
            CastOp.Update(CastChange.Back(0), choices),
            CastOp.Update(CastChange.Way(flow.begin), choices),
            CastOp.Tap(listOf(use), ManaBelongs.Key("k"), "source c1", listOf(use), taps, true),
            CastOp.Tap(listOf(use), ManaBelongs.Uses(listOf(use)), "chip", emptyList(), ManaTapsWire(), false),
            CastOp.ChooseWay("c1", use, listOf(use), taps, true),
            CastOp.Cast(autotap = true, custom = false, mana = payment.suggestion),
            CastOp.Follow(choices, HandChoiceWire(0, listOf(1))),
            CastOp.Replan(true),
            CastOp.Selection(listOf(TargetRef.Object(bear))),
            CastOp.Close("cancelled"))
        for ((n, op) in ops.withIndex()) {
            val request = DeviceRequest.Cast(n.toLong(), CastRequest(5, n.toLong().takeIf { it > 2 }, 12L.takeIf { n % 2 == 0 }, op,
                listOf(listOf("m1", "m2")).takeIf { n == 3 }))
            assertEquals(request, DeviceMessage.decode(DeviceMessage.request("game", request)).request, "${op::class.simpleName}")
        }
        val answers = listOf(
            CastAnswer.Mana(2, payment.suggestion, true, taps),
            CastAnswer.Engine(CastFlowValue(4, true, flow.cardId, flow.begin), listOf(flow.begin, Input.PassPriority(PlayerId(alice)))),
            CastAnswer.Drop("Storm Spark can't be cast now."),
            CastAnswer.Refuse(FlowSessions.STALE),
            CastAnswer.Selected(3, null),
            CastAnswer.Selected(3, SelectionWire(listOf(TargetRef.Object(bear)), false)),
            CastAnswer.None)
        for (a in answers) {
            val done = DeviceAnswer.Done(1, DeviceResult.Cast(a))
            assertEquals(done, DeviceMessage.decode(DeviceMessage.answer("game", done)).answer)
        }
    }

    // Web client step 4e: a session's versions: the newest and the few before it are kept; a step on a version the session no
    // longer keeps, on a closed session or one never opened is refused as stale, each logged
    @Test fun aStepOnAFlowThePageNoLongerShowsIsStale() {
        val sessions = FlowSessions<String>(log, keep = 2)
        sessions.open(1, "cast", "Storm Spark")
        assertEquals(1L, sessions.put(1, "v1", "begun"))
        assertEquals(2L, sessions.put(1, "v2", "target answered"))
        assertEquals("v2", sessions.get(1, 2, "tap").entry)
        assertEquals("v1", sessions.get(1, 1, "tap").entry, "an older version still kept")
        assertEquals(3L, sessions.put(1, "v3", "paid"))
        val gone = sessions.get(1, 1, "answer")
        assertNull(gone.entry)
        assertTrue(gone.stale!!.contains("isn't kept"))
        assertNull(sessions.get(1, null, "answer").entry, "a step on an open session must name its version")
        assertNull(sessions.get(9, 1, "answer").entry, "never opened")
        sessions.close(1, "cancelled")
        assertNull(sessions.get(1, 3, "answer").entry, "closed")
        assertNull(sessions.put(1, "v4", "late"), "a step's flow after the session closed isn't kept")
        assertEquals(0, sessions.open)
        val text = logged.joinToString("\n") { it.second }
        assertTrue("Flow session 1 opened: cast Storm Spark" in text, text)
        assertTrue("refused as stale" in text, text)
        assertTrue("closed: cancelled" in text, text)
        assertTrue(logged.filter { "refused as stale" in it.second }.all { it.first }, "stale steps are logged as warnings")
    }

    // Web client step 4e: sessions are kept apart; one opened again with the same number starts anew
    @Test fun sessionsAreKeptApart() {
        val sessions = FlowSessions<String>(log)
        sessions.open(1, "cast", "Storm Spark")
        sessions.open(2, "cast", "Mountain")
        assertNotNull(sessions.put(1, "a", "begun"))
        assertNotNull(sessions.put(2, "b", "begun"))
        assertEquals("a", sessions.get(1, 1, "step").entry)
        assertEquals("b", sessions.get(2, 1, "step").entry)
        sessions.open(1, "cast", "Storm Spark")
        assertNull(sessions.get(1, 1, "step").entry, "opened again: its old versions are gone")
        assertTrue(sessions.isOpen(2))
    }
}
