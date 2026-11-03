package arcanum.engine.sets.sos

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Secrets of Strixhaven's mechanics in play: paradigm (CR 702.192), increment (CR 702.191), opus and converge (mana spent, CR 601.2h),
 * infusion (life gained this turn), repartee (a spell that targets a creature) and stun counters (CR 122.1d).
 */
class SosMechanicsScenariosTest {
    private val thesis = Cards.invent("Etiquette Thesis", "{3}{B}{B}", "Sorcery — Lesson", """
        Target player draws two cards and loses 2 life.
        Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
    """)
    private val docent = Cards.invent("Ponderous Docent", "{1}{U}{U}", "Creature — Human Wizard", """
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        Whenever one or more +1/+1 counters are put on this creature, draw a card.
    """, "0/2")
    private val drummer = Cards.invent("Stormskin Drummer", "{1}{R}", "Creature — Dwarf Bard", """
        Reach
        Opus — Whenever you cast an instant or sorcery spell, this creature deals 1 damage to each opponent. If five or more mana was spent to cast that spell, this creature deals 3 damage to each opponent instead.
    """, "1/3")
    private val antiquity = Cards.invent("Spiteful Antiquity", "{5}", "Creature — Avatar", """
        Reach, trample
        Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.
    """, "2/2")
    private val vendor = Cards.invent("Marrow Lane Vendor", "{2}{B}", "Creature — Goblin Warlock", """
        Menace
        Infusion — This creature gets +2/+0 as long as you gained life this turn.
    """, "2/3")
    private val versifier = Cards.invent("Wistful Versifier", "{1}{B}", "Creature — Elf Bard",
        "Repartee — Whenever you cast an instant or sorcery spell that targets a creature, each opponent loses 1 life and you gain 1 life.", "2/2")
    private val riposte = Cards.invent("Foil Riposte", "{1}{W}", "Instant", """
        Tap target creature. If it's your turn, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Draw a card.
    """)
    private val shock = Cards.invent("Campus Sparkling Jolt", "{R}", "Instant", "Campus Sparkling Jolt deals 2 damage to any target.")
    private val bigSpell = Cards.invent("Campus Gathered Storm", "{4}{R}", "Sorcery", "Campus Gathered Storm deals 1 damage to any target.")
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val lifeSpell = Cards.invent("Campus Small Mercy", "{W}", "Instant", "You gain 2 life.")
    private val imp = Cards.invent("Campus Tally Imp", "{1}{R}", "Creature — Imp", "Increment", "2/2")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** Paradigm: exiled as it resolves; at each of its controller's first main phases a copy may be cast for free, and only the
     * first resolution makes the delayed trigger (a copy resolving later is not the first). */
    // CR 702.192a, 707.10, 707.12
    @Test
    fun paradigmCastsACopyEachFirstMainPhase() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(thesis)
        alice.library(bear, bear, bear, bear, bear, bear, bear, bear)
        bob.library(bear, bear, bear, bear)
        alice.casts(thesis, target = "alice")
        resolveAll()
        expect {
            alice.exile has "Etiquette Thesis"
            alice.graveyard lacks "Etiquette Thesis"
            alice.hand.names.count { it == "Plaza Bear" } shouldBe 2
        }
        assertEquals(18, alice.life)
        assertEquals(1, state.delayedTriggers.size)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        allPass()
        yes()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("alice"))))
        resolveAll()
        assertEquals(16, alice.life)
        assertEquals(1, state.delayedTriggers.size)
        expect { alice.exile.names.count { it == "Etiquette Thesis" } shouldBe 1 }
    }

    /** Increment: a counter when the mana spent is greater than its power or toughness; the counters draw a card. */
    // CR 702.191a
    @Test
    fun incrementCountsTheManaSpent() = scenario {
        alice.battlefield(docent, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shock, bigSpell)
        alice.library(bear, bear)
        bob.battlefield(bear)
        alice.casts(shock, target = "Plaza Bear", paying = listOf("Mountain"))
        resolveAll()
        expect { "Ponderous Docent".counters(p1p1) shouldBe 1 }
        alice.casts(bigSpell, target = "bob")
        resolveAll()
        expect {
            "Ponderous Docent".counters(p1p1) shouldBe 2
            alice.hand.names.count { it == "Plaza Bear" } shouldBe 2
        }
    }

    /** No counter when the mana spent isn't greater than its power or its toughness. */
    // CR 702.191a
    @Test
    fun incrementNotWhenTheManaIsNotGreater() = scenario {
        alice.battlefield(imp, "Mountain")
        alice.hand(shock)
        bob.battlefield(bear)
        alice.casts(shock, target = "Plaza Bear")
        resolveAll()
        expect { "Campus Tally Imp".counters(p1p1) shouldBe 0 }
    }

    /** Opus: one damage for a small spell, three when five or more mana was spent on it. */
    // CR 601.2h, 608.2c
    @Test
    fun opusReadsTheManaSpentOnThatSpell() = scenario {
        alice.battlefield(drummer, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shock, bigSpell)
        bob.battlefield(bear)
        alice.casts(shock, target = "Plaza Bear", paying = listOf("Mountain"))
        resolveAll()
        assertEquals(19, bob.life)
        alice.casts(bigSpell, target = "bob")
        resolveAll()
        assertEquals(15, bob.life)
    }

    /** Converge: a counter for each colour of mana spent (generic paid with three colours: three counters). */
    // CR 601.2h, 614.1c, 400.7d
    @Test
    fun convergeCountsColoursSpent() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", "Swamp", "Swamp")
        alice.hand(antiquity)
        alice.casts(antiquity)
        resolveAll()
        expect { "Spiteful Antiquity".counters(p1p1) shouldBe 3 }
    }

    /** Infusion: the bonus only once you have gained life this turn. */
    // CR 119.9
    @Test
    fun infusionAfterGainingLife() = scenario {
        alice.battlefield(vendor, "Plains")
        alice.hand(lifeSpell)
        expect { "Marrow Lane Vendor".power shouldBe 2 }
        alice.casts(lifeSpell)
        resolveAll()
        expect { "Marrow Lane Vendor".power shouldBe 4 }
    }

    /** Repartee: only a spell that targets a creature. */
    // CR 601.2c
    @Test
    fun reparteeNeedsACreatureTarget() = scenario {
        alice.battlefield(versifier, "Mountain", "Mountain")
        alice.hand(shock, shock)
        bob.battlefield(bear)
        alice.casts(shock, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        assertEquals(18, bob.life)
        alice.casts(shock, target = "Plaza Bear")
        resolveAll()
        assertEquals(17, bob.life)
        assertEquals(21, alice.life)
    }

    /** A stun counter on your turn: the creature stays tapped through its controller's untap step, losing the counter. */
    // CR 122.1d
    @Test
    fun stunCounterReplacesTheUntap() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(riposte)
        alice.library(bear, bear)
        bob.battlefield(bear)
        bob.library(bear, bear)
        alice.casts(riposte, target = "Plaza Bear")
        resolveAll()
        expect {
            "Plaza Bear".counters(CounterKind.Stun) shouldBe 1
            "Plaza Bear".tapped shouldBe true
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            "Plaza Bear".counters(CounterKind.Stun) shouldBe 0
            "Plaza Bear".tapped shouldBe true
        }
    }

    /** Not on your turn: tapped but no stun counter. */
    // CR 122.1d
    @Test
    fun noStunCounterOnAnOpponentsTurn() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(riposte)
        alice.library(bear)
        bob.battlefield(bear)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        passPriority()
        alice.casts(riposte, target = "Plaza Bear")
        resolveAll()
        expect {
            "Plaza Bear".counters(CounterKind.Stun) shouldBe 0
            "Plaza Bear".tapped shouldBe true
        }
    }
}
