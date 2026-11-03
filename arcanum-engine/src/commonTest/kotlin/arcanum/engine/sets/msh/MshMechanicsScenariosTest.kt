package arcanum.engine.sets.msh

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Marvel Super Heroes' mechanics in play: power-up, teamwork, connive and its replacement, plan counters, harness and ∞. */
class MshMechanicsScenariosTest {
    private val scrapper = Cards.invent("Plucky Scrapper", "{1}{W}", "Creature — Human Warrior Hero", """
        Lifelink
        Power-up — {4}{W}: Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "2/1")
    private val blast = Cards.invent("Gauntlet Blast", "{3}{R}", "Sorcery", """
        Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
        Gauntlet Blast deals 5 damage to target creature. If this spell was cast using teamwork, it also deals 2 damage to that creature's controller.
    """)
    private val rowan = Cards.invent("Field Agent Rowan Vale", "{W}", "Legendary Creature — Human Spy Hero",
        "Whenever Field Agent Rowan Vale becomes tapped to pay a teamwork cost, put a +1/+1 counter on her and draw a card.", "2/1")
    private val recruit = Cards.invent("Crimson Room Recruit", "{1}{B}", "Creature — Human Spy Villain",
        "When this creature enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)", "1/2")
    private val mastermind = Cards.invent("Mastermind, Gamma Genius", "{2}{U}{U}", "Legendary Creature — Gamma Scientist Villain", """
        If a creature you control would connive, instead you draw a card, then that creature connives.
        At the beginning of combat on your turn, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
    """, "1/3")
    private val landslide = Cards.invent("Political Landslide", "{W}", "Enchantment — Plan", """
        Whenever a creature you control enters, scry 1 and put a plan counter on this enchantment.
        When the fourth plan counter is put on this enchantment, sacrifice it, draw a card, and put a +1/+1 counter on each creature you control.
    """)
    private val jewel = Cards.invent("The Psyche Jewel", "{1}{W}", "Legendary Artifact — Infinity Stone", """
        Indestructible
        {T}: Add {W}.
        {5}{W}, {T}: Harness The Psyche Jewel. (Once harnessed, its ∞ ability is active.)
        ∞ — At the beginning of your end step, exile up to one other target nonland permanent you control, then return that card to the battlefield under its owner's control.
    """)
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Answers the open choice with the offered card named [name] (a card drawn while the ability resolves is a new object, CR 400.7). */
    private fun Scenario.discards(name: String) {
        val options = decision<Decision.ChooseObjects>().options
        val card = (card(name) as arcanum.engine.state.ObjectId.Card).card
        answer(Answer.Objects(listOf(options.first { (it as? arcanum.engine.state.ObjectId.Card)?.card == card })))
    }

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Entered this turn: the power-up costs {4}{W} less {1}{W}, so three Plains pay for it; and only once. */
    // CR 702.193a, 702.193b
    @Test
    fun powerUpCostsLessTheTurnItEntersAndWorksOnlyOnce() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(scrapper)
        alice.casts(scrapper)
        resolveAll()
        alice.activates(scrapper, paying = listOf("Plains#2", "Plains#3", "Plains#4"))
        resolveAll()
        expect { scrapper.counters(p1p1) shouldBe 2 }
        assertFailsWith<IllegalArgumentException> { alice.activates(scrapper, paying = listOf("Plains#5", "Plains#6", "Plains#7")) }
    }

    /** On the battlefield since an earlier turn it costs the full {4}{W}. */
    // CR 702.193a
    @Test
    fun powerUpCostsFullLater() = scenario {
        alice.battlefield(scrapper)
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(scrapper)
        assertEquals(5, state.battlefield.count { state.obj(it)!!.tapped })
    }

    /** Teamwork paid by tapping creatures with total power 2: 4 damage, and the tapped creature's trigger. */
    // CR 702.194a, 702.194b
    @Test
    fun teamworkTapsCreaturesAndMakesTheSpellStronger() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", rowan)
        alice.hand(blast)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(blast, target = ogre, kicked = 1, choosing = listOf(rowan))
        expect { rowan.tapped shouldBe true }
        resolveAll()
        expect {
            ogre.zone shouldBe arcanum.api.Zone.Graveyard
            rowan.counters(p1p1) shouldBe 1
            alice.hand has bear
        }
        assertEquals(18, bob.life)
    }

    /** Without teamwork the spell deals its first amount. */
    // CR 702.194b
    @Test
    fun withoutTeamworkTheSpellDealsLess() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", rowan)
        alice.hand(blast)
        bob.battlefield(ogre)
        alice.casts(blast, target = ogre, kicked = 0)
        resolveAll()
        expect {
            ogre.zone shouldBe arcanum.api.Zone.Graveyard
            rowan.tapped shouldBe false
        }
        assertEquals(20, bob.life)
    }

    /** Connive: draw, discard a nonland card, a +1/+1 counter. */
    // CR 701.50a
    @Test
    fun conniveDrawsDiscardsAndCounts() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(recruit, ogre)
        alice.library(bear)
        alice.casts(recruit)
        resolveAll()
        discards(ogre)
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has ogre
            recruit.counters(p1p1) shouldBe 1
        }
    }

    /** The replacement draws first, then the creature connives as usual. */
    // CR 614.1a, 701.50a
    @Test
    fun conniveReplacementDrawsAnExtraCard() = scenario {
        alice.battlefield(mastermind, "Swamp", "Swamp")
        alice.hand(recruit)
        alice.library(bear, ogre)
        alice.casts(recruit)
        resolveAll()
        discards(ogre)
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has ogre
            recruit.counters(p1p1) shouldBe 1
        }
    }

    /** The fourth plan counter: the Plan is sacrificed, a card drawn, counters on each creature. */
    // CR 205.3h, 603.2
    @Test
    fun fourthPlanCounterFinishesThePlan() = scenario {
        alice.battlefield(landslide, bear)
        alice.library(ogre, ogre, ogre, ogre, ogre)
        alice.hand(bear, bear, bear, bear)
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        repeat(4) {
            alice.casts(bear)
            var guard = 20
            while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
                when (pending) {
                    is Decision.ChooseObjects -> answer(Answer.Objects(emptyList()))
                    null -> allPass()
                    else -> break
                }
            }
        }
        expect {
            alice.graveyard has landslide
            alice.hand has ogre
            card(bear, 0).counters(p1p1) shouldBe 1
        }
    }

    /** The ∞ ability does nothing until the stone is harnessed; harnessed, the end-step blink triggers. */
    // CR 701.64a, 702.186b
    @Test
    fun harnessTurnsOnTheInfinityAbility() = scenario {
        alice.battlefield(jewel, bear)
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        advanceTo(TurnStep.End)
        assertEquals(null, pending)
        assertTrue(state.stack.isEmpty())
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(jewel, ability = 1)
        resolveAll()
        assertTrue(arcanum.api.ObjectMark.Harnessed in state.obj(card(jewel))!!.marks)
        val before = card(bear)
        advanceTo(TurnStep.End)
        alice.answers(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect { bear.zone shouldBe arcanum.api.Zone.Battlefield }
        assertTrue(card(bear) != before, "The bear came back as a new object")
    }
}
