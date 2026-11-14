package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OTJ chunk 01: plot (CR 702.170) and the set's first plot cards played out. */
class OtjChunk01PlotScenariosTest {
    private val plotText = { cost: String ->
        "Plot $cost (You may pay $cost and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)"
    }
    private val ox = Cards.invent("Trailside Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val grazer = Cards.invent("Mesa Grazer", "{2}{G}", "Creature — Ox", "", "3/3")
    private val insight = Cards.invent("Canyon Insight", "{U}{R}", "Sorcery", "Draw a card.")
    private val glance = Cards.invent("Prairie Glance", "{U}", "Sorcery", "Draw a card.")
    private val brewmaster = Cards.invent("Cactus Brewmaster", "{1}{G}", "Creature — Plant Warlock",
        "Trample\nWhen this card becomes plotted, target creature gets +3/+2 and gains trample until end of turn.\n${plotText("{1}{G}")}", "3/2")
    private val waylayer = Cards.invent("Hawk Waylayer", "{1}{W}{W}", "Creature — Bird Rogue",
        "Flash\nFlying\nWhen this creature enters, exile target spell. It becomes plotted. (Its owner may cast it as a sorcery on a later turn without paying its mana cost.)\n" +
            "Spells your opponents cast from graveyards or from exile cost {2} more to cast.", "2/2")
    private val wraith = Cards.invent("Dune Wraith", "{1}{W}", "Creature — Spirit",
        "Flying\nIf you control five or more untapped lands, this creature enters with two +1/+1 counters and a lifelink counter on it.\n${plotText("{1}{W}")}", "2/3")
    private val raider = Cards.invent("Freehoof Raider", "{2}{G}", "Creature — Centaur Mercenary",
        "This creature enters with two +1/+1 counters on it if it wasn't cast or no mana was spent to cast it.\n${plotText("{3}{G}")}", "3/3")
    private val vesk = Cards.invent("Vesk Unbound", "{U}{U}", "Legendary Planeswalker — Jace",
        "You can't cast Vesk Unbound during your first, second, or third turns of the game.\n[+1]: Draw a card, then discard a card.\n" +
            "[+1]: You may exile a nonland card with mana value 3 or less from your hand. If you do, it becomes plotted.\n" +
            "[−6]: Until end of turn, whenever you cast a spell, copy it. You may choose new targets for the copy.", loyalty = "3")
    private val mira = Cards.invent("Mira, Unbeaten Gunhand", "{1}{U}{R}", "Legendary Creature — Human Rogue",
        "Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)\n" +
            "Whenever you cast a multicolored instant or sorcery spell from your hand, exile that spell instead of putting it into your graveyard as it resolves. " +
            "If you do, it becomes plotted. (You may cast it as a sorcery on a later turn without paying its mana cost.)", "3/3")
    private val fortune = Cards.invent("Forge Your Fortune", "{3}{G}{U}", "Sorcery",
        "Look at the top three cards of your library. You may exile a nonland card from among them. If you do, it becomes plotted. Put the rest into your hand. " +
            "(You may cast it as a sorcery on a later turn without paying its mana cost.)")
    private val seamster = Cards.invent("Bandit Seamster", "{3}{U}", "Creature — Human Warlock",
        "When this creature enters, create a 2/2 blue and black Zombie Rogue creature token, then put two +1/+1 counters on that token for each spell " +
            "you've cast this turn other than the first.\n${plotText("{4}{U}")}", "1/4")
    private val butchery = Cards.invent("Ruthless Butchery", "{3}{B}", "Sorcery",
        "Sacrifice any number of permanents you control, then draw that many cards.\n${plotText("{1}{B}{B}")}")
    private val warden = Cards.invent("Warden of Quiet Roads", "{2}{W}", "Creature — Human Knight",
        "This creature enters with a +1/+1 counter on it plus an additional +1/+1 counter on it for each other creature you control.\n${plotText("{1}{W}")}", "0/0")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.payAll() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    /** [player] takes [name]'s plot special action (CR 702.170a, b). */
    private fun Scenario.plots(name: String, player: ScenarioPlayer = alice) {
        perform(Engine.options(state, player.id).filterIsInstance<Input.Activate>().single { it.source == card(name) })
        payAll()
    }

    private fun Scenario.plotted(name: String): Boolean = state.objOrThrow(card(name)).let { it.zone == Zone.Exile && ObjectMark.Plotted in it.marks }

    // CR 702.170c, 113.6k: "when this card becomes plotted" triggers from exile, where the plot action put it
    @Test
    fun aCardTriggersAsItBecomesPlotted() = scenario {
        alice.battlefield("Forest", "Forest", ox)
        alice.hand(brewmaster)
        plots(brewmaster)
        assertTrue(plotted(brewmaster))
        resolveAll()
        expect {
            ox.power shouldBe 5
            ox.toughness shouldBe 4
            ox hasKeyword Keyword.Trample.kind
        }
    }

    // CR 702.170c, d: an exiled spell's card becomes plotted; its owner casts it on a later turn without paying its mana cost, but a spell an
    // opponent casts from exile costs {2} more (CR 601.2f); three players: the spell's owner is the one who may cast it
    @Test
    fun anExiledSpellBecomesPlottedForItsOwnerAndIsTaxed() = scenario(players = 3) {
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(waylayer)
        bob.battlefield("Forest", "Forest")
        bob.hand(ox)
        turn(number = 2, active = bob)
        bob.casts(ox)
        bob.passes()
        carol.passes()
        alice.casts(waylayer)
        allPass()
        resolveAll()
        assertTrue(plotted(ox))
        expect { bob.exile has ox }
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        assertEquals(bob.id, state.turn.active)
        bob.casts(ox)
        payAll()
        resolveAll()
        expect {
            bob.battlefield has ox
            // plotted: without paying its mana cost, but {2} more from exile
            card("Forest", 0).tapped shouldBe true
            card("Forest", 1).tapped shouldBe true
        }
    }

    // CR 614.1c: two +1/+1 counters and a lifelink counter (CR 122.1b) when five or more lands are untapped as it enters
    @Test
    fun entersWithCountersOfTwoKindsWhenLandsAreUntapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(wraith)
        alice.casts(wraith)
        allPass()
        expect {
            wraith.power shouldBe 4
            wraith.counters(p1p1) shouldBe 2
            wraith hasKeyword Keyword.Lifelink.kind
        }
    }

    // CR 614.1c: with four untapped lands left it enters without counters
    @Test
    fun entersWithoutCountersWhenTooFewLandsAreUntapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(wraith)
        alice.casts(wraith)
        allPass()
        expect {
            wraith.power shouldBe 2
            wraith lacksKeyword Keyword.Lifelink.kind
        }
    }

    // CR 702.170d, 601.2h: cast from plot, no mana was spent to cast it, so it enters with two +1/+1 counters
    @Test
    fun noManaSpentFromPlotGivesCounters() = scenario {
        players.forEach { it.library("Forest", "Forest", "Forest") }
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(raider)
        plots(raider)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(raider)
        allPass()
        expect { raider.power shouldBe 5 }
    }

    // CR 601.2h: cast for its mana cost, it enters without counters
    @Test
    fun manaSpentGivesNoCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(raider)
        alice.casts(raider)
        allPass()
        expect { raider.power shouldBe 3 }
    }

    // CR 702.170c: the card exiled from the hand becomes plotted and is cast free on a later turn
    @Test
    fun loyaltyAbilityPlotsACardFromTheHand() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield(vesk, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(ox, grazer)
        alice.activates(vesk, ability = 1)
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        alice.chooses(grazer)
        resolveAll()
        assertTrue(plotted(grazer))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(grazer)
        allPass()
        expect { alice.battlefield has grazer }
    }

    // CR 614.1a, 702.170c: the multicolored sorcery cast from the hand is exiled as it resolves and becomes plotted; cast again from exile, it
    // isn't cast from the hand, so it goes to the graveyard
    @Test
    fun aResolvingSpellIsExiledAndPlotted() = scenario {
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.battlefield(mira, "Island", "Mountain")
        alice.hand(insight)
        alice.casts(insight)
        resolveAll()
        assertTrue(plotted(insight))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(insight)
        resolveAll()
        expect { alice.graveyard has insight }
    }

    // CR 702.170c: one of the cards looked at is exiled and plotted, the rest go to the hand
    @Test
    fun oneLookedAtCardBecomesPlotted() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island")
        alice.library(grazer, "Plains", "Swamp", "Mountain")
        alice.hand(fortune)
        alice.casts(fortune)
        allPass()
        alice.chooses(grazer)
        resolveAll()
        assertTrue(plotted(grazer))
        expect {
            alice.hand has "Plains"
            alice.hand has "Swamp"
            alice.library has "Mountain"
        }
    }

    // CR 111.1, 107.1b: two counters on the token for each spell cast this turn after the first (the creature spell itself is the second)
    @Test
    fun tokenGetsCountersForSpellsAfterTheFirst() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.library("Island", "Island")
        alice.hand(glance, seamster)
        alice.casts(glance)
        allPass()
        alice.casts(seamster)
        allPass()
        resolveAll()
        expect { "Zombie Rogue Token".power shouldBe 4 }
    }

    // CR 701.21a: any number of permanents sacrificed, as many cards drawn
    @Test
    fun sacrificesAnyNumberAndDrawsThatMany() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", ox, grazer)
        alice.library("Island", "Island", "Island")
        alice.hand(butchery)
        alice.casts(butchery)
        allPass()
        alice.chooses(ox, grazer)
        resolveAll()
        expect {
            alice.graveyard has ox
            alice.graveyard has grazer
            alice.hand.size shouldBe 2
        }
    }

    // CR 614.1c: one +1/+1 counter plus one for each other creature you control
    @Test
    fun entersWithACounterPlusOneForEachOtherCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", ox, grazer)
        alice.hand(warden)
        alice.casts(warden)
        allPass()
        expect { warden.counters(p1p1) shouldBe 3 }
    }
}
