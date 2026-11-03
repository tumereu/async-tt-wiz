package arcanum.engine.sets.sos

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * More Secrets of Strixhaven cards in play: a free spell once each turn, colourless mana at your next main phase (a delayed trigger,
 * CR 603.7), X paid as a trigger resolves with a reflexive trigger (CR 603.12), a creature spell entering with more counters (CR 400.7d),
 * a land becoming a creature for good (CR 611.2a), halves rounded down (CR 107.1a), the greatest mana value chosen by the opponent, and a
 * mana ability adding mana equal to its power.
 */
class SosMoreCardsScenariosTest {
    private val corwen = Cards.invent("Corwen and the Squalls", "{5}{U}{R}", "Legendary Creature — Human Bard Sorcerer",
        "Once during each of your turns, you may cast an instant or sorcery spell from your hand without paying its mana cost.", "5/7")
    private val chisel = Cards.invent("Mana Chisel", "{1}{U}{U}", "Instant",
        "Counter target spell. If you control a Wizard, add an amount of {C} equal to the amount of mana spent to cast that spell at the beginning of your next main phase.")
    private val prover = Cards.invent("Prover of the Peripheral", "{1}{U}", "Creature — Djinn Wizard", """
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        At the beginning of combat on your turn, you may pay {X}. When you do, move X +1/+1 counters from this creature onto another target creature.
    """, "1/1")
    private val thicketborn = Cards.invent("Thicketborn Antiquity", "{2/G}{2/G}", "Creature — Avatar", """
        Reach, trample
        Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.
        Whenever you cast a creature spell, that creature enters with X additional +1/+1 counters on it, where X is the number of colors of mana spent to cast it.
    """, "0/0")
    private val atrium = Cards.invent("Grand Atrium of the Archive", null, "Land", """
        {T}: Add {C}.
        {T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast an instant or sorcery spell.
        {5}: If this land isn't a creature, it becomes a 2/4 Wizard creature with "Whenever you cast an instant or sorcery spell, this creature gets +1/+0 until end of turn." It's still a land.
    """)
    private val blight = Cards.invent("Blight of Halves", "{B}{B}{B}{B}{B}", "Sorcery",
        "Each player loses half their life, then discards half the cards in their hand, then sacrifices half the permanents they control of their choice. Round down each time.")
    private val chase = Cards.invent("Close of the Chase", "{1}{B}", "Sorcery",
        "Target opponent exiles a creature or planeswalker they control with the greatest mana value among creatures and planeswalkers they control.")
    private val orator = Cards.invent("Hedgework Orator", "{2}{G}", "Creature — Elf Druid", """
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        {T}: Add an amount of {G} equal to this creature's power.
    """, "1/2")
    private val study = Cards.invent("Light Study", "{U}", "Sorcery", "Draw a card.")
    private val bigDraw = Cards.invent("Deep Study", "{3}{U}{U}", "Sorcery", "Draw two cards.")
    private val wizard = Cards.invent("Plaza Wizard", "{U}", "Creature — Human Wizard", "", "1/1")
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val bolt = Cards.invent("Sparkling Jolt", "{R}", "Instant", "Sparkling Jolt deals 2 damage to any target.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The free cast is offered once each of your turns. */
    // CR 601.2a, 118.9
    @Test
    fun aFreeSpellOnceEachTurn() = scenario {
        alice.battlefield(corwen)
        alice.hand(bigDraw, bigDraw)
        alice.library(bear, bear, bear, bear)
        val free = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()
        assertTrue(free.isNotEmpty(), "a sorcery castable without mana")
        perform(free.first { state.obj(it.card)?.definition?.faces?.first()?.name == "Deep Study" })
        resolveAll()
        expect { alice.hand.names.count { it == "Plaza Bear" } shouldBe 2 }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().isEmpty(), "only once each turn")
    }

    /** With a Wizard: the countered spell's mana comes back as {C} at the next main phase. */
    // CR 603.7, 106.4
    @Test
    fun colourlessManaAtYourNextMainPhase() = scenario {
        alice.battlefield(wizard, "Island", "Island", "Island")
        bob.battlefield("Forest", "Forest")
        bob.hand(bear)
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.hand(chisel)
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(bear)
        passPriority()
        alice.casts(chisel, target = "Plaza Bear")
        resolveAll()
        expect { bob.graveyard has "Plaza Bear" }
        assertEquals(1, state.delayedTriggers.size)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        allPass()
        assertEquals(2, state.player(alice.id).manaPool.size)
    }

    /** X paid as the trigger resolves: that many counters move to the target chosen by the reflexive trigger. */
    // CR 603.12
    @Test
    fun payXAndMoveThatManyCounters() = scenario {
        alice.battlefield(prover, counters = listOf(p1p1 to 2))
        alice.battlefield(bear, "Island", "Island")
        alice.library(bear)
        bob.library(bear)
        var guard = 100
        while (pending == null && guard-- > 0) if (state.priority.holder == null) declareNone() else passPriority()
        if (pending is Decision.ChooseNumber) answer(Answer.Number(2))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        if (pending is Decision.May) answer(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Plaza Bear"))))
        resolveAll()
        expect {
            "Plaza Bear".counters(p1p1) shouldBe 2
            "Prover of the Peripheral".counters(p1p1) shouldBe 0
        }
    }

    /** The creature spell cast with two colours enters with two more counters. */
    // CR 400.7d, 614.1c
    @Test
    fun creatureSpellEntersWithMoreCounters() = scenario {
        alice.battlefield(thicketborn, "Forest", "Island")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Island"))
        resolveAll()
        expect { card("Plaza Bear").counters(p1p1) shouldBe 2 }
    }

    /** It stays a creature after the turn ends; activating it again does nothing more. */
    // CR 611.2a, 205.1b
    @Test
    fun landBecomesACreatureForGood() = scenario {
        alice.battlefield(atrium, "Island", "Island", "Island", "Island", "Island")
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.activates("Grand Atrium of the Archive", ability = 2)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            "Grand Atrium of the Archive".power shouldBe 2
            "Grand Atrium of the Archive".toughness shouldBe 4
        }
    }

    /** Half of 20 life, of 3 cards (1) and of 3 permanents (1), rounded down. */
    // CR 107.1a, 101.4
    @Test
    fun halvesRoundedDown() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(blight)
        bob.battlefield(bear, ogre, "Forest")
        bob.hand(bear, bear, bear)
        alice.casts(blight)
        allPass()
        repeat(4) {
            when (val d = pending) {
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min)))
                else -> {}
            }
        }
        resolveAll()
        assertEquals(10, bob.life)
        assertEquals(2, state.player(bob.id).hand.size)
        assertEquals(2, state.battlefield.count { state.obj(it)!!.owner == bob.id })
    }

    /** The opponent exiles one of their creatures with the greatest mana value. */
    // CR 608.2d
    @Test
    fun greatestManaValueExiled() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(chase)
        bob.battlefield(bear, ogre)
        alice.casts(chase, target = "bob")
        resolveAll()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options.take(1))) }
        resolveAll()
        expect {
            bob.exile has "Plaza Ogre"
            bob.battlefield has "Plaza Bear"
        }
    }

    /** Its mana ability adds as much {G} as its power. */
    // CR 605.1a
    @Test
    fun manaEqualToPower() = scenario {
        alice.battlefield(orator, counters = listOf(p1p1 to 2))
        alice.activates("Hedgework Orator", ability = 0)
        assertEquals(3, state.player(alice.id).manaPool.size)
    }
}
