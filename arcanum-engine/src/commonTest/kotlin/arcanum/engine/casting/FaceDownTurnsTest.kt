package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Turning face-down permanents face up and down again (CR 702.37, 702.168, 708): the X a cost was turned up for, what stays. */
class FaceDownTurnsTest {
    private val trumpeter = Cards.invent("Hidden Horn Trumpeter", "{R}", "Creature — Goblin", """
        Morph {X}{X}{R} (You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)
        When this creature is turned face up, create X 1/1 red Goblin creature tokens.
    """, "1/1")
    private val witness = Cards.invent("Veiled Tally Witness", "{3}{W}", "Creature — Human Detective", """
        Disguise {X}{3}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this creature is turned face up, you gain X life.
    """, "3/4")

    private fun Scenario.alternativeWay(name: String): CastWay =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(name) && it.way.alternative != null }.way

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    /** Casts [name] face down for {3}, then turns it face up for its cost with X = [x]. */
    private fun Scenario.castDownTurnUp(name: String, x: Int) {
        alice.casts(name, way = alternativeWay(name))
        pay()
        allPass()
        val id = card(name)
        assertTrue(state.objOrThrow(id).faceDown)
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == id })
        if (pending is Decision.Announce) answer(Answer.Number(x))
        pay()
    }

    // CR 702.37f, 107.3d: X in another ability of a permanent with morph is the X chosen as it was turned face up
    @Test
    fun morphsXIsTheXItWasTurnedUpFor() = scenario {
        alice.battlefield(*Array(8) { "Mountain" })
        alice.hand(trumpeter)
        castDownTurnUp(trumpeter, x = 2)
        allPass()
        val tokens = state.battlefield.filter { state.objOrThrow(it).token }
        assertEquals(2, tokens.size)
    }

    // CR 702.168e: the same for disguise
    @Test
    fun disguisesXIsTheXItWasTurnedUpFor() = scenario {
        alice.battlefield(*Array(10) { "Plains" })
        alice.hand(witness)
        castDownTurnUp(witness, x = 3)
        allPass()
        expect { alice.life shouldBe 23 }
    }

    // ---- A manifested or cloaked instant or sorcery card

    private val manifester = Cards.invent("Mist Manifester", "{0}", "Artifact", "{0}: Manifest the top card of your library.")
    private val cloaker = Cards.invent("Mist Cloaker", "{0}", "Artifact", "{0}: Cloak the top card of your library.")
    private val spark = Cards.invent("Veiled Spark", "{R}", "Instant", "Veiled Spark deals 1 damage to any target.")
    private val hound = Cards.invent("Veiled Hound", "{1}{G}", "Creature — Dog", "", "3/3")

    /** A sorcery: "Turn each face-down creature face up." */
    private val unveiling = Cards.define("Unveiling Light", CardDefinition(Layout.Normal, listOf(FaceDefinition("Unveiling Light",
        types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.TurnFaceUp(
            Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.HasStatus(Status.FaceDown))))))))))))))

    /** "Whenever a permanent is turned face up, you gain 1 life." */
    private val watcher = Cards.define("Unveiling Watcher", CardDefinition(Layout.Normal, listOf(FaceDefinition("Unveiling Watcher",
        types = listOf(CardType.Enchantment), abilities = listOf(Ability.Triggered(Trigger.On(EventPattern.TurnedFaceUp(ObjectFilter.Any)),
            Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(1)))))))))))

    private fun Scenario.putFaceDown(by: String, top: String): ObjectId {
        alice.battlefield(by, watcher)
        alice.library(top)
        alice.hand(unveiling)
        alice.activates(by)
        allPass()
        return state.battlefield.single { state.objOrThrow(it).faceDown }
    }

    private fun Scenario.unveil() {
        alice.castsWithoutCosts(unveiling)
        resolve()
        repeat(3) { if (state.stack.isNotEmpty()) allPass() }
    }

    private fun Scenario.revealed(id: ObjectId) = state.eventsOf(state.turn.number).any {
        ((it as? Change.Happened)?.event as? GameEvent.Revealed)?.ids?.contains(id) == true
    }

    // CR 701.40g: a manifested instant or sorcery card an effect would turn face up is revealed and stays face down; nothing triggers
    @Test
    fun aManifestedInstantIsRevealedAndStaysFaceDown() = scenario {
        val id = putFaceDown(manifester, spark)
        unveil()
        assertTrue(state.objOrThrow(id).faceDown)
        assertTrue(revealed(id))
        expect { alice.life shouldBe 20 }
    }

    // CR 701.58g: the same for a cloaked one, which keeps ward {2} as it stays face down (CR 701.58a)
    @Test
    fun aCloakedSorceryIsRevealedAndStaysFaceDown() = scenario {
        val id = putFaceDown(cloaker, spark)
        unveil()
        assertTrue(state.objOrThrow(id).faceDown)
        assertTrue(revealed(id))
        assertTrue(view()[id]!!.characteristics!!.abilities.any { it.ability is Keyword.Ward })
        expect { alice.life shouldBe 20 }
    }

    // CR 701.40a, 708.8: a manifested creature card the same effect turns face up is turned face up, and that triggers
    @Test
    fun aManifestedCreatureIsTurnedFaceUp() = scenario {
        val id = putFaceDown(manifester, hound)
        unveil()
        assertTrue(!state.objOrThrow(id).faceDown)
        expect { alice.life shouldBe 21 }
    }

    // ---- Turned face down again

    /** A sorcery: "Turn target creature face down." */
    private val shrouding = Cards.define("Shrouding Mist", CardDefinition(Layout.Normal, listOf(FaceDefinition("Shrouding Mist",
        types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.TurnFaceDown(Objects.Target("creature")))))))))))

    // CR 708.2a, 702.168a: cast disguised it was a 2/2 with ward {2}; turned face up and then face down by an effect that lists no
    // characteristics, it is a plain 2/2 with no ward
    @Test
    fun aDisguisedPermanentTurnedDownAgainHasNoWard() = scenario {
        alice.battlefield(*Array(10) { "Plains" })
        alice.hand(witness, shrouding)
        castDownTurnUp(witness, x = 0)
        allPass()
        val id = card(witness)
        assertTrue(!state.objOrThrow(id).faceDown)
        alice.castsWithoutCosts(shrouding, target = witness)
        resolve()
        assertTrue(state.objOrThrow(id).faceDown)
        val face = view()[id]!!.characteristics!!
        assertEquals(2, face.power)
        assertTrue(face.abilities.none { it.ability is Keyword.Ward }, "${face.abilities}")
    }
}
