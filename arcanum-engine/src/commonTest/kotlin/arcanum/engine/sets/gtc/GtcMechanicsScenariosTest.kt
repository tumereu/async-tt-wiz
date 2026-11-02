package arcanum.engine.sets.gtc

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
import kotlin.test.assertTrue

/** Gatecrash's mechanics in play: battalion, bloodrush, extort, evolve and cipher, with the invented cards of the parser's
 * chunks 01–05. */
class GtcMechanicsScenariosTest {
    private val recruit = Cards.invent("Shieldwall Recruit", "{W}", "Creature — Human Soldier",
        "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +2/+2 until end of turn.", "1/1")
    private val goblin = Cards.invent("Brandskin Goblin", "{1}{R}", "Creature — Goblin Warrior",
        "Bloodrush — {R}, Discard this card: Target attacking creature gets +2/+1 until end of turn.", "2/1")
    private val collector = Cards.invent("Tithe Collector", "{1}{W}", "Creature — Human Cleric",
        "Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)", "2/2")
    private val specimen = Cards.invent("Specimen Zero", "{G}", "Creature — Human Ooze", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        Remove two +1/+1 counters from this creature: Regenerate it. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
    """, "1/1")
    private val depthsight = Cards.invent("Depthsight Mage", "{2}{G}{U}", "Creature — Human Wizard", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        Whenever a +1/+1 counter is put on this creature, you may draw a card.
    """, "1/1")
    private val rogue = Cards.invent("Rogue Krasis", "{1}{G}{G}", "Creature — Beast Mutant", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        Whenever this creature evolves, put a +1/+1 counter on each other creature you control with a +1/+1 counter on it.
    """, "3/2")
    private val incision = Cards.invent("Shadow Incision", "{4}{B}", "Sorcery", """
        Target opponent loses 3 life.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4")
    private val giant = Cards.invent("Hill Brute", "{3}{G}", "Creature — Giant", "", "4/4")
    private val counter = CounterKind.PowerToughness(1, 1)

    private fun Scenario.yes() = answer(Answer.YesNo(true))
    private fun Scenario.no() = answer(Answer.YesNo(false))
    private fun Scenario.payAsSuggested() {
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // ---- Battalion (CR 207.2c: an ability word; the trigger is "this creature and at least two other creatures attack")

    /** With two other attackers it triggers. */
    // CR 508.1m, 603.2
    @Test
    fun battalionWithTwoOthers() = scenario {
        alice.battlefield(recruit, bear, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(recruit, bear, ox)
        resolveStack()
        expect {
            recruit.power shouldBe 3
            recruit.toughness shouldBe 3
        }
    }

    /** With one other attacker it doesn't. */
    // CR 603.2
    @Test
    fun battalionNeedsTwoOthers() = scenario {
        alice.battlefield(recruit, bear, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(recruit, bear)
        expect {
            stack.isEmpty()
            recruit.power shouldBe 1
        }
    }

    // ---- Bloodrush (an ability word: an activated ability from the hand, CR 602.1, 113.6m)

    /** Discarding the card from the hand pumps an attacking creature. */
    // CR 602.2, 601.2h: the card is discarded as a cost
    @Test
    fun bloodrushFromTheHand() = scenario {
        alice.battlefield(bear, "Mountain")
        alice.hand(goblin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.activates(goblin, target = bear)
        allPass()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 3
            alice.graveyard.names shouldBe listOf(goblin)
        }
    }

    /** Only an attacking creature can be its target: before combat there is nothing to activate it for. */
    // CR 601.2c, 602.2b
    @Test
    fun bloodrushNeedsAnAttacker() = scenario {
        alice.battlefield(bear, "Mountain")
        alice.hand(goblin)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Activate && it.source == card(goblin) })
    }

    // ---- Extort (CR 702.101)

    /** Casting a spell, paying {W/B}: each opponent loses 1 life and you gain that much. */
    // CR 702.101a
    @Test
    fun extortDrainsWhenPaid() = scenario {
        alice.battlefield(collector, "Forest", "Forest", "Plains")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        allPass()
        yes()
        payAsSuggested()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
            card("Plains").tapped shouldBe true
        }
    }

    /** Declining to pay does nothing. */
    // CR 702.101a
    @Test
    fun extortDeclined() = scenario {
        alice.battlefield(collector, "Forest", "Forest", "Plains")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        allPass()
        no()
        expect {
            bob.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    /** Each instance triggers separately. */
    // CR 702.101b
    @Test
    fun twoExtortsTriggerTwice() = scenario {
        alice.battlefield(collector, collector, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
        expect { stack.names.size shouldBe 3 }
    }

    // ---- Evolve (CR 702.100)

    /** A bigger creature entering puts a counter on it. */
    // CR 702.100a
    @Test
    fun evolveWithABiggerCreature() = scenario {
        alice.battlefield(specimen)
        alice.hand(bear)
        alice.castsWithoutCosts(bear)
        resolve()
        resolveStack()
        expect { specimen.counters(counter) shouldBe 1 }
    }

    /** A creature greater in neither power nor toughness doesn't (the comparison is made as it triggers and resolves). */
    // CR 702.100a, b
    @Test
    fun evolveNeedsGreaterPowerOrToughness() = scenario {
        alice.battlefield(rogue)
        alice.hand(bear)
        alice.castsWithoutCosts(bear)
        resolve()
        expect {
            stack.isEmpty()
            rogue.counters(counter) shouldBe 0
        }
    }

    /** Toughness alone is enough. */
    // CR 702.100a
    @Test
    fun evolveOnToughness() = scenario {
        alice.battlefield(rogue)
        alice.hand(ox)
        alice.castsWithoutCosts(ox)
        resolve()
        resolveStack()
        expect { rogue.counters(counter) shouldBe 1 }
    }

    /** An opponent's creature doesn't evolve it. */
    // CR 702.100a
    @Test
    fun evolveOnlyForYourCreatures() = scenario {
        alice.battlefield(specimen)
        bob.hand(giant)
        bob.castsWithoutCosts(giant)
        resolve()
        expect { stack.isEmpty() }
    }

    /** "Whenever this creature evolves": its evolve ability put the counters on it; another creature's +1/+1 counters
     * grow. */
    // CR 702.100d
    @Test
    fun evolvesTriggers() = scenario {
        alice.battlefield(rogue, specimen)
        alice.hand(giant)
        alice.castsWithoutCosts(giant)
        resolve()
        // the two evolve triggers: the Rogue's first on the Stack, so the Specimen's resolves first
        val order = decision<Decision.OrderTriggers>().triggers.sortedBy { if (it.source == card(rogue)) 0 else 1 }.map { it.index }
        answer(Answer.Order(order))
        resolveStack()
        expect {
            rogue.counters(counter) shouldBe 1
            specimen.counters(counter) shouldBe 2
        }
    }

    /** "A +1/+1 counter is put on this creature" triggers for evolve too. */
    // CR 702.100a, 603.2
    @Test
    fun countersPutTriggerAfterEvolve() = scenario {
        alice.battlefield(depthsight)
        alice.library("Island")
        alice.hand(bear)
        alice.castsWithoutCosts(bear)
        resolve()
        resolveStack()
        yes()
        expect {
            depthsight.counters(counter) shouldBe 1
            alice.hand.names shouldBe listOf("Island")
        }
    }

    // ---- Cipher (CR 702.99)

    /** As it resolves the card may be exiled encoded on a creature you control; when that creature deals combat damage
     * to a player, a copy of the card may be cast without paying its mana cost. */
    // CR 702.99a, c
    @Test
    fun cipherEncodesAndCopies() = scenario {
        alice.battlefield(bear)
        alice.hand(incision)
        alice.castsWithoutCosts(incision, target = "bob")
        resolve()
        yes()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        expect {
            bob.life shouldBe 17
            alice.exile.names shouldBe listOf(incision)
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveStack()
        yes()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("bob"))))
        resolveStack()
        expect {
            bob.life shouldBe 12
            alice.exile.names shouldBe listOf(incision)
        }
    }

    /** Not encoding it, the card goes to the graveyard. */
    // CR 702.99a
    @Test
    fun cipherDeclined() = scenario {
        alice.battlefield(bear)
        alice.hand(incision)
        alice.castsWithoutCosts(incision, target = "bob")
        resolve()
        no()
        expect { alice.graveyard.names shouldBe listOf(incision) }
    }

    /** Without a creature to encode it on, it simply goes to the graveyard. */
    // CR 702.99a
    @Test
    fun cipherWithoutCreature() = scenario {
        alice.hand(incision)
        alice.castsWithoutCosts(incision, target = "bob")
        resolve()
        expect {
            bob.life shouldBe 17
            alice.graveyard.names shouldBe listOf(incision)
        }
    }

    /** When the creature leaves the battlefield the card stays in exile, no longer encoded. */
    // CR 702.99c
    @Test
    fun cipherEndsWhenTheCreatureLeaves() = scenario {
        alice.battlefield(bear)
        alice.hand(incision)
        alice.library("Forest", "Forest")
        bob.library("Swamp", "Swamp")
        alice.castsWithoutCosts(incision, target = "bob")
        resolve()
        yes()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        move(bear, Zone.Hand)
        move(bear, Zone.Battlefield)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        expect {
            stack.isEmpty()
            bob.life shouldBe 15
            alice.exile.names shouldBe listOf(incision)
        }
    }
}
