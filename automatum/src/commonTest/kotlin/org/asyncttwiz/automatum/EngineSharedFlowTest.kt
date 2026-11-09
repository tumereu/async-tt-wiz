package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Priority, resolution and the choices met on the way on a game's own engine state ([SharedFlow], [StepFlow]). */
class EngineSharedFlowTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val bob get() = table.bob

    /** Alice's Glance Ahead cast and paid: the state after the cast alone. */
    private fun cast(): GameState {
        table.put(alice, Zone.Battlefield, "Island")
        table.put(alice, Zone.Library, "Island", "Island", "Island")
        table.put(bob, Zone.Library, "Island", "Island")
        val (glance) = table.put(alice, Zone.Hand, "Glance Ahead")
        val state = table.state()
        val flow = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(glance).first(), CastFlow.Seeds(table::seed))
        val payment = assertIs<Decision.ManaPayment>(flow.pending)
        return flow.sending(payment.suggestion).cast.working
    }

    // STK-060 (CR 701.44c, d): an explorer that left is offered beside one still there, by what it was: no longer an object, but public as it left
    @Test fun anExplorerThatLeftIsOfferedByWhatItWas() {
        val everyone = arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Creature)
        val goners = arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype("Goner"))
        val trek = arcanum.engine.cards.Cards.define("Parting Trail", arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition("Parting Trail",
            manaCost = arcanum.api.ManaCost.parse("{0}"), types = listOf(arcanum.api.CardType.Sorcery),
            abilities = listOf(arcanum.api.Ability.Spell(arcanum.api.Instructions(steps = listOf(
                arcanum.api.Step.Bind(arcanum.api.Objects.Each(everyone), "them"),
                arcanum.api.Step.Do(arcanum.api.Effect.Destroy(arcanum.api.Objects.Each(goners))),
                arcanum.api.Step.Do(arcanum.api.Effect.Action(arcanum.api.KeywordAction.Explore(arcanum.api.Objects.Bound("them")))),
            ))))))))
        val goner = arcanum.engine.cards.Cards.invent("Parting Goner", "{0}", "Creature — Goner", "", "1/1")
        val scout = arcanum.engine.cards.Cards.invent("Remaining Scout", "{0}", "Creature — Elf Scout", "", "1/1")
        val (gone) = table.put(alice, Zone.Battlefield, goner)
        table.put(alice, Zone.Battlefield, scout)
        table.put(alice, Zone.Library, "Island", "Island")
        table.put(bob, Zone.Library, "Island")
        val left = table.obj(gone)
        val (spell) = table.put(alice, Zone.Hand, trek)
        val state = table.state()
        val cast = CastFlow.start(table.source(state), alice, HandPlays.of(state, alice, null).of(spell).first(), CastFlow.Seeds(table::seed)).sending(null).cast.working
        val resolved = table.flow(table.flow(cast).pass(alice).working).pass(bob).working
        val flow = table.flow(resolved)
        val choice = assertIs<Decision.ChooseObjects>(flow.decision)
        assertTrue(left in choice.options)
        assertNull(flow.state.obj(left))
        assertTrue(flow.offeredAfterLeaving(left))
        assertEquals("Parting Goner", arcanum.engine.Engine.lastKnown(flow.state, left)?.characteristics?.name)
        // One still there isn't one that left
        assertTrue(!flow.offeredAfterLeaving(choice.options.first { it != left }))
    }

    // SYS-067 (CR 117.3a, 117.3d): the active player holds priority in their main phase; the step arrow passes it, so the
    // other player may act once the active player has passed
    @Test fun theActivePlayerPassesToTheOther() {
        table.put(alice, Zone.Library, "Island")
        table.put(bob, Zone.Library, "Island")
        val flow = table.flow()
        assertEquals(alice, flow.holder)
        val passed = flow.step(alice)
        assertEquals(listOf<Input>(Input.PassPriority(PlayerId(alice))), passed.inputs)
        assertEquals(PlayerId(bob), passed.working.priority.holder)
    }

    // SYS-067 (CR 117.3c, 117.4): after a cast its caster holds priority; both passing resolves the top of the Stack
    // ("Resolve", STK-027), and the active player gets priority again
    @Test fun bothPassingResolvesAndTheActivePlayerGetsPriority() {
        val cast = cast()
        val flow = table.flow(cast)
        assertEquals(alice, flow.priority, "the caster holds priority with the spell on the Stack")
        val bobsTurn = flow.pass(alice).working
        assertEquals(PlayerId(bob), bobsTurn.priority.holder)
        val resolving = table.flow(bobsTurn).pass(bob).working
        // STK-030: the scry met while resolving is the caster's own choice, which nobody makes for them
        val scry = assertNotNull(table.flow(resolving).decision)
        assertEquals(PlayerId(alice), scry.player)
        val answered = table.flow(resolving).let { it.answer(it.listedAnswers().first()) }.working
        assertNull(answered.pending)
        assertTrue(answered.stack.isEmpty())
        assertEquals(PlayerId(alice), answered.priority.holder, "the active player gets priority after a resolution")
    }
}
