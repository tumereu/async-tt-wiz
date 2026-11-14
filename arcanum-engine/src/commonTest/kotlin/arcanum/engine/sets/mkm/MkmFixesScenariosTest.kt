package arcanum.engine.sets.mkm

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.sets.tdm.Plain
import arcanum.engine.state.NamedValue
import arcanum.engine.state.Secrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM fix chunk: event history read as of the event, and the other fixes the set's review found. */
class MkmFixesScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val informant = Cards.invent("Burrow Informant", "{1}{G}", "Creature — Mole Scout", """
        At the beginning of your end step, if a face-down creature entered the battlefield under your control this turn, put a +1/+1 counter on this creature.
        {T}: Add {G}.
    """, "1/1")
    private val arrival = Cards.invent("Furtive Arrival", "{2}{R}", "Creature — Human Rogue", "Disguise {1}{R}", "3/2")

    private val brace = Cards.invent("Ossified Brace", "{2}", "Artifact", """
        You may choose not to untap this artifact during your untap step.
        {2}, {T}: Target creature gets +0/+3 for as long as this artifact remains tapped.
        Sacrifice this artifact: You gain 1 life.
    """)

    private val lineup = Cards.invent("Lineup of Suspects", "{4}{G}", "Enchantment", """
        When this enchantment enters, create a 1/1 white Human creature token, a 1/1 blue Merfolk creature token, and a 1/1 red Goblin creature token. Then secretly choose Human, Merfolk, or Goblin.
        Sacrifice this enchantment, Reveal the creature type you chose: If target attacking creature token is the chosen type, put three +1/+1 counters on it and it gains deathtouch until end of turn.
    """)

    private fun Scenario.setup() {
        alice.library("Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        carol.library("Swamp", "Swamp")
    }

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    private fun Scenario.castDisguised() {
        alice.casts(arrival, way = alternativeOf(arrival, Keyword.Disguise.Kind))
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        assertTrue(state.objOrThrow(card(arrival)).faceDown)
    }

    // CR 603.4, 708.2a: the creature entered face down; turning it face up later in the turn doesn't change what entered, so the
    // condition holds at the end step (the event is read as it happened, not as the permanent is now)
    @Test
    fun aFaceDownCreatureTurnedUpLaterStillEnteredFaceDown() = scenario(players = 3) {
        alice.battlefield(informant, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(arrival)
        setup()
        castDisguised()
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card(arrival) })
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertFalse(state.objOrThrow(card(arrival)).faceDown)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { card(informant).counters(p1p1) shouldBe 1 }
    }

    // CR 603.4: still face down at the end step, it counts as well
    @Test
    fun aFaceDownCreatureThatStaysFaceDownCounts() = scenario(players = 3) {
        alice.battlefield(informant, "Mountain", "Mountain", "Mountain")
        alice.hand(arrival)
        setup()
        castDisguised()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { card(informant).counters(p1p1) shouldBe 1 }
    }

    // CR 611.2b, 400.7: "for as long as this artifact remains tapped" ends when it leaves the battlefield, though it left tapped
    // (the duration follows the permanent, not its last known information)
    @Test
    fun remainsTappedEndsWhenTheArtifactLeaves() = scenario(players = 3) {
        alice.battlefield(brace, Plain.bear, "Mountain", "Mountain")
        setup()
        alice.activates(brace, target = Plain.bear)
        pay()
        resolveAll()
        expect { Plain.bear.toughness shouldBe 5 }
        alice.activates(brace, ability = 1)
        resolveAll()
        expect {
            brace.zone shouldBe arcanum.api.Zone.Graveyard
            Plain.bear.toughness shouldBe 2
        }
    }

    /** Casts the Lineup, secretly choosing [type], and attacks bob with the Goblin and Human tokens on alice's next turn. */
    private fun Scenario.chooseAndAttack(type: String) {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(lineup)
        alice.library("Swamp", "Swamp", "Swamp")
        bob.library("Swamp", "Swamp", "Swamp")
        carol.library("Swamp", "Swamp", "Swamp")
        alice.casts(lineup)
        pay()
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype(type)))
        resolveAll()
        val o = state.objOrThrow(card(lineup))
        // the choice is kept, secret, known only to alice
        assertEquals(NamedValue.SubtypeValue(Subtype(type)), o.linked["type"])
        assertEquals(listOf(alice.id), Secrets.knownBy(o, "type"))
        advanceTo(TurnStep.DeclareAttackers, turn = 4)
        alice.attacks("Goblin Token", "Human Token")
        resolveAll()
    }

    // CR 607.2d, 608.2h: the type chosen secretly by the enters trigger is read after the cost sacrificed the enchantment; the attacking
    // Goblin token is the chosen type: three counters and deathtouch (CR 702.2)
    @Test
    fun theChosenTypeGetsCountersAndDeathtouch() = scenario(players = 3) {
        chooseAndAttack("Goblin")
        alice.activates(lineup, target = "Goblin Token")
        resolveAll()
        expect {
            "Goblin Token".counters(p1p1) shouldBe 3
            "Goblin Token" hasKeyword Keyword.Deathtouch.kind
            lineup.zone shouldBe arcanum.api.Zone.Graveyard
        }
    }

    // CR 608.2c: another type chosen, the Goblin gets nothing (the enchantment is still sacrificed, a cost)
    @Test
    fun anotherTypeGetsNothing() = scenario(players = 3) {
        chooseAndAttack("Human")
        alice.activates(lineup, target = "Goblin Token")
        resolveAll()
        expect {
            "Goblin Token".counters(p1p1) shouldBe 0
            lineup.zone shouldBe arcanum.api.Zone.Graveyard
        }
    }

    // CR 118.3: with no type chosen (it never entered through its trigger), the reveal can't be paid, so it can't be activated
    @Test
    fun noChoiceNoActivation() = scenario(players = 3) {
        alice.battlefield(lineup)
        setup()
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card(lineup) })
    }
}
