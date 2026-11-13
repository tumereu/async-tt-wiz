package org.asyncttwiz.automatum.boundary

import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.ReplacementOption
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.CombatChoices
import org.asyncttwiz.automatum.CombatCostShown
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.DamageDivision
import org.asyncttwiz.automatum.DamagePreview
import org.asyncttwiz.automatum.EngineTable
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.LaneAttack
import org.asyncttwiz.automatum.LaneBlock
import org.asyncttwiz.automatum.PreviewDamage
import org.asyncttwiz.protocol.MtgRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Web client step 4e (docs/specs/web-client.md §1 item 3): a board's shared flow and its combat across the engine boundary.
 * The shared panels' value of a real flow, every step of a shared-flow session and its answers, what the table shows of
 * combat (declarations, costs, the damage preview and divisions) and the cost chips' requests come back the same from their
 * JSON text.
 */
class SharedMessagesTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val bob get() = table.bob

    /** Alice's Glance Ahead cast, paid and passed by both: its scry asked of her while it resolves (STK-030). */
    private fun scrying(): org.asyncttwiz.automatum.SharedFlow {
        table.put(alice, Zone.Battlefield, "Island")
        table.put(alice, Zone.Library, "Island", "Island", "Island")
        table.put(bob, Zone.Library, "Island", "Island")
        val (glance) = table.put(alice, Zone.Hand, "Glance Ahead")
        val state = table.state()
        val flow = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(glance).first(), CastFlow.Seeds(table::seed))
        val cast = flow.sending((flow.pending as Decision.ManaPayment).suggestion).cast.working
        val resolving = table.flow(table.flow(cast).pass(alice).working).pass(bob).working
        return table.flow(resolving).prepared()
    }

    private fun roundTrip(answer: DeviceAnswer): DeviceAnswer = DeviceMessage.decode(DeviceMessage.answer("game", answer)).answer!!

    // Web client step 4e, STK-030: the shared panels' value of a real flow (the scry asked while a spell resolves, its listed
    // answers, what Alice may see) comes back the same from its text inside the device's answer, worked out from the flow
    @Test fun aSharedFlowsValueComesBackTheSameFromItsText() {
        val flow = scrying()
        val made = SharedFlowValue.of(flow, alice, 12)
        assertEquals(flow, made.flow, "in-process it keeps its flow")
        val value = made.copy(version = 3)
        val scry = assertNotNull(value.decision)
        assertEquals(PlayerId(alice), scry.player)
        assertEquals(flow.listedAnswers(), value.listed)
        assertTrue(value.listed.isNotEmpty())
        assertEquals(flow.visible(alice), value.visible)
        assertEquals(12, value.board)
        val done = DeviceAnswer.Done(4, DeviceResult.Shared(SharedAnswer.Show(value)))
        val back = roundTrip(done)
        assertEquals(done, back)
        val shown = assertIs<SharedAnswer.Show>(assertIs<DeviceResult.Shared>(assertIs<DeviceAnswer.Done>(back).result).answer).value
        assertEquals(null, shown.flow, "the live flow never crosses")
        assertEquals(scry, shown.decision)
    }

    // Web client step 4e: every step of a shared-flow session and every answer comes back the same from its text
    @Test fun everySharedStepComesBackTheSameFromItsText() {
        val use = ManaUse.Life(2)
        val taps = ManaTapsWire(tapped = listOf(use), outs = listOf(use))
        val card = TargetRef.Object(ObjectId.Card(arcanum.engine.state.CardId("alice.1"), 2))
        val ops = listOf(
            SharedOp.Show(autotap = true), SharedOp.Show(false, "d7", listOf(use)), SharedOp.Pass(bob), SharedOp.Step(true),
            SharedOp.Declaring, SharedOp.Give("d7", Answer.Targets(listOf(card)), listOf(Answer.Objects(listOf(card.id)))),
            SharedOp.Give("d8", Answer.Division(listOf(arcanum.engine.decision.Share(card, 3)))),
            SharedOp.Replacements("d9", listOf(ReplacementOption(1, card.id, "If it would die, exile it instead."), ReplacementOption(0, null, null))),
            SharedOp.Pay("d10", listOf(use)), SharedOp.Early,
            SharedOp.Tap(listOf(use), ManaBelongs.Key("life"), "chip 2 life", listOf(use), taps, true),
            SharedOp.ChooseWay("c1", use, listOf(use), emptyList(), ManaTapsWire(), false),
            SharedOp.Pays(listOf(use)), SharedOp.Selection(listOf(card, TargetRef.Player(PlayerId(bob)))), SharedOp.Close("no flow shown"))
        for ((n, op) in ops.withIndex()) {
            val request = DeviceRequest.Shared(n.toLong(), SharedRequest(6, n.toLong().takeIf { it % 2 == 1L }, 40L.takeIf { n % 3 != 0 }, op,
                listOf(listOf("m1", "m2")).takeIf { n == 3 }))
            assertEquals(request, DeviceMessage.decode(DeviceMessage.request("game", request)).request, "${op::class.simpleName}")
        }
        val answers = listOf(SharedAnswer.Send(listOf(Input.PassPriority(PlayerId(alice)))), SharedAnswer.Empty,
            SharedAnswer.Refuse(null), SharedAnswer.Refuse("You don't hold priority now."),
            SharedAnswer.Mana(2, listOf(use), true, taps, "chip, added"), SharedAnswer.Selected(2, null),
            SharedAnswer.Selected(2, SelectionWire(listOf(card), true)), SharedAnswer.Declaring(bob), SharedAnswer.Declaring(null),
            SharedAnswer.None)
        for (a in answers) {
            val done = DeviceAnswer.Done(1, DeviceResult.Shared(a))
            assertEquals(done, roundTrip(done))
        }
    }

    // Web client step 4e, CMB-033, CMB-037, I-D29: what the table shows of combat (the declarations allowed, the costs of the
    // drafted attackers, the damage preview with the divisions on the way, the divisions still to come) and an open division
    // in a shared flow's value come back the same from their text, the amounts by object in their order
    @Test fun combatsValuesComeBackTheSameFromTheirText() {
        val blocker = MtgRef.Card("bob.4", 1)
        val other = MtgRef.Card("bob.5", 0)
        val defender = MtgRef.Player(bob)
        val division = DamageDivision(alice, "alice.2", 5, listOf(blocker, other, defender), linkedMapOf(blocker to 2, other to 3),
            linkedMapOf(blocker to 2, other to 3, defender to 0), defender, firstStrike = true)
        val shown = CombatShown(
            CombatChoices(bob, attackers = false, blocks = mapOf("bob.4" to listOf("alice.2")), requiredBlocks = listOf(LaneBlock("bob.4", "alice.2")),
                capacity = mapOf("bob.5" to null, "bob.4" to 2), attacks = mapOf("alice.2" to listOf(null, "bob.9")),
                requiredAttacks = listOf(LaneAttack("alice.2", null, listOf("alice.3"), listOf("exert"), 1)), enlist = mapOf("alice.2" to 1),
                enlistable = listOf("alice.3"), optional = mapOf("alice.2" to listOf(CombatCostShown("Exert", "You may exert it.", "exert"))),
                banding = setOf("alice.2")),
            DamagePreview(listOf(PreviewDamage("alice.2", blocker, 2, true), PreviewDamage("alice.2", defender, 3, false)),
                mapOf(bob to -3), listOf("bob.4"), listOf(division to linkedMapOf(defender to 3, blocker to 2)), assumed = true),
            draftRefusal = "Bob's creature can't block that.", noneRefusal = null, early = listOf(division), passing = alice,
            firstStrikeStep = true, costs = mapOf("alice.2" to listOf(CombatCostShown("{2}", "Pay {2} to attack."))))
        val done = DeviceAnswer.Done(9, DeviceResult.Combat(shown))
        val back = roundTrip(done)
        assertEquals(done, back)
        val again = assertNotNull(assertIs<DeviceResult.Combat>(assertIs<DeviceAnswer.Done>(back).result).shown)
        assertEquals(listOf(defender, blocker), again.preview!!.divisions.single().second.keys.toList(), "the shares in their order")
        assertEquals(mapOf("bob.4" to 2, bob to 3), again.preview!!.damage)
        val flow = DeviceAnswer.Done(2, DeviceResult.Shared(SharedAnswer.Show(SharedFlowValue(1, 5, division = division))))
        assertEquals(flow, roundTrip(flow))
        assertEquals(DeviceAnswer.Done(3, DeviceResult.Combat(null)), roundTrip(DeviceAnswer.Done(3, DeviceResult.Combat(null))))
    }

    // Web client step 4e (I-D36, CMB-040, ZON-080): the combat's and cost chips' requests come back the same from their text
    @Test fun combatAndChipRequestsComeBackTheSameFromTheirText() {
        val requests = listOf(
            DeviceRequest.Combat(1, 7), DeviceRequest.Combat(2, 7, null, drawn = true),
            DeviceRequest.Combat(3, 7, org.asyncttwiz.protocol.CombatArea("area", emptyList()), drawn = true),
            DeviceRequest.CombatRefusal(4, 7, null, attacks = listOf(LaneAttack("alice.2", "bob.9"))),
            DeviceRequest.CombatRefusal(5, 7, null, blocks = listOf(LaneBlock("bob.4", "alice.2"))),
            DeviceRequest.Chips(6, 7, "alice.8"))
        for (r in requests) assertEquals(r, DeviceMessage.decode(DeviceMessage.request("game", r)).request)
        for (a in listOf(DeviceResult.Chips(null), DeviceResult.Chips(null, held = false))) {
            val done = DeviceAnswer.Done(1, a)
            assertEquals(done, roundTrip(done))
        }
    }
}
