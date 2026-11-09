package arcanum.engine.sets.fdn

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The FDN cards that scry, played: the invented names carry the oracle text of Charming Prince, Mazemind Tome, Devout Decree, Opt and the Temples. */
class FdnChunk01ScryScenariosTest {
    private val gallantViscount = Cards.invent("Gallant Viscount", "{1}{W}", "Creature — Human Noble", """
        When this creature enters, choose one —
        • Scry 2.
        • You gain 3 life.
        • Exile another target creature you own. Return it to the battlefield under your control at the beginning of the next end step.
    """, "2/2")
    private val recollectionCodex = Cards.invent("Recollection Codex", "{2}", "Artifact — Book", """
        {T}, Put a page counter on this artifact: Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        {2}, {T}, Put a page counter on this artifact: Draw a card.
        When there are four or more page counters on this artifact, exile it. If you do, you gain 4 life.
    """)
    private val austereMandate = Cards.invent("Austere Mandate", "{1}{W}", "Sorcery",
        "Exile target creature or planeswalker that's black or red. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)")
    private val peekForward = Cards.invent("Peek Forward", "{U}", "Instant", """
        Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        Draw a card.
    """)

    private val bear = Cards.invent("Sunlit Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val blackBear = Cards.invent("Dusk Bear", "{1}{B}", "Creature — Bear", "", "2/2")
    private val redWalker = Cards.invent("Cinder Oracle", "{1}{R}", "Legendary Planeswalker — Oracle", "[+1]: You gain 1 life.", loyalty = "3")
    private val whiteBear = Cards.invent("Dawn Bear", "{1}{W}", "Creature — Bear", "", "2/2")
    private val shatterCodex = Cards.invent("Sudden Shatter", "{R}", "Instant", "Destroy target artifact.")
    private val page = CounterKind.Named("page")

    private fun Scenario.libraryNames() = state.player(alice.id).library.map { nameOf(state.obj(it)!!) }

    /** Puts the one card of a scry on the bottom. */
    private fun Scenario.scryToBottom(name: String) {
        answer(Answer.Objects(listOf(card(name))))
    }

    /** Keeps the one card of a scry on top. */
    private fun Scenario.scryKeep() {
        answer(Answer.Objects(emptyList()))
    }

    private fun Scenario.castViscount() {
        alice.battlefield("Plains", "Plains")
        alice.hand(gallantViscount)
        alice.casts(gallantViscount)
        allPass()
    }

    // CR 701.22 (scry 2: one on the bottom, the other kept)
    @Test
    fun viscountScriesTwo() = scenario {
        alice.library("Island", "Mountain", "Forest")
        castViscount()
        alice.answers(Answer.Modes(listOf(0)))
        allPass()
        alice.chooses("Island")
        expect { libraryNames() shouldBe listOf("Mountain", "Forest", "Island") }
    }

    // CR 700.2
    @Test
    fun viscountGainsThreeLife() = scenario {
        castViscount()
        alice.answers(Answer.Modes(listOf(1)))
        allPass()
        expect { alice.life shouldBe 23 }
    }

    // CR 603.7 (returns at the next end step), CR 400.7 (a new object: its counters don't stay, it is summoning sick)
    @Test
    fun viscountBlinksAnotherCreatureUntilTheNextEndStep() = scenario {
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.battlefield(whiteBear)
        castViscount()
        alice.answers(Answer.Modes(listOf(2)))
        alice.answers(Answer.Targets(listOf(target(bear))))
        allPass()
        expect { card(bear).zone shouldBe Zone.Exile }
        advanceTo(TurnStep.End)
        allPass()
        expect {
            card(bear).zone shouldBe Zone.Battlefield
            card(bear).controller shouldBe alice.id
            card(bear).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 700.2b: "another" and "you own" leave no legal target, so that mode can't be chosen
    @Test
    fun viscountCantExileItselfOrACreatureYouDontOwn() = scenario {
        bob.battlefield(bear)
        castViscount()
        assertFailsWith<IllegalArgumentException> { alice.answers(Answer.Modes(listOf(2))) }
        alice.answers(Answer.Modes(listOf(1)))
    }

    // CR 603.8: three page counters don't trigger it; the fourth does, and it exiles itself for 4 life
    @Test
    fun codexExilesItselfAtFourCounters() = scenario {
        alice.battlefield(recollectionCodex, counters = listOf(page to 3))
        alice.library("Island", "Mountain")
        expect { state.stack.size shouldBe 0 }
        alice.activates(recollectionCodex)
        expect {
            card(recollectionCodex).counters(page) shouldBe 4
            alice.life shouldBe 20
        }
        allPass()
        expect { card(recollectionCodex).zone shouldBe Zone.Exile }
    }

    // CR 118.3: the second ability pays {2}, taps and adds a counter, and draws
    @Test
    fun codexDrawsForTwoAndACounter() = scenario {
        alice.battlefield(recollectionCodex, "Island", "Island")
        alice.library("Mountain")
        alice.activates(recollectionCodex, ability = 1)
        allPass()
        expect {
            alice.hand has "Mountain"
            card(recollectionCodex).tapped shouldBe true
            card(recollectionCodex).counters(page) shouldBe 1
            card(recollectionCodex).zone shouldBe Zone.Battlefield
        }
    }

    // CR 400.7: destroyed in response it can't be exiled, so "if you do" fails and no life is gained
    @Test
    fun codexGoneInResponseGainsNoLife() = scenario {
        alice.battlefield(recollectionCodex, "Mountain", counters = listOf(page to 3))
        alice.hand(shatterCodex)
        alice.library("Island", "Forest")
        alice.activates(recollectionCodex)
        alice.casts(shatterCodex, target = recollectionCodex)
        allPass()
        expect { card(recollectionCodex).zone shouldBe Zone.Graveyard }
        resolveStack()
        expect {
            card(recollectionCodex).zone shouldBe Zone.Graveyard
            alice.life shouldBe 20
        }
    }

    // CR 115.1
    @Test
    fun mandateExilesABlackCreatureAndScries() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(austereMandate)
        alice.library("Island", "Forest")
        bob.battlefield(blackBear)
        alice.casts(austereMandate, target = blackBear)
        allPass()
        scryToBottom("Island")
        expect {
            card(blackBear).zone shouldBe Zone.Exile
            libraryNames() shouldBe listOf("Forest", "Island")
        }
    }

    // CR 115.1
    @Test
    fun mandateExilesARedPlaneswalker() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(austereMandate)
        alice.library("Island", "Forest")
        bob.battlefield(redWalker, counters = listOf(CounterKind.Loyalty to 3))
        alice.casts(austereMandate, target = redWalker)
        allPass()
        scryKeep()
        expect { card(redWalker).zone shouldBe Zone.Exile }
    }

    // CR 601.2c: a white creature is no legal target
    @Test
    fun mandateCantTargetAWhiteCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(austereMandate)
        bob.battlefield(whiteBear)
        assertFailsWith<Throwable> { alice.casts(austereMandate, target = whiteBear) }
    }

    // CR 701.22, CR 608.2c: the scry comes first, so the draw takes the next card
    @Test
    fun peekScriesThenDraws() = scenario {
        alice.battlefield("Island")
        alice.hand(peekForward)
        alice.library("Mountain", "Forest")
        alice.casts(peekForward)
        allPass()
        scryToBottom("Mountain")
        expect {
            alice.hand has "Forest"
            alice.hand lacks "Mountain"
        }
    }

    /** Played from hand it's tapped and scries 1; untapped on the next turn it makes either of its two colors. */
    // CR 614.1c, CR 603.6a, CR 701.22, CR 106.1
    private fun sanctum(name: String, first: Color, second: Color) {
        val land = Cards.invent(name, null, "Land", """
            This land enters tapped.
            When this land enters, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            {T}: Add {${first.symbol}} or {${second.symbol}}.
        """)
        for (color in listOf(first, second)) scenario {
            alice.hand(land)
            alice.library("Island", "Forest")
            alice.plays(land)
            expect { card(land).tapped shouldBe true }
            allPass()
            scryToBottom("Island")
            expect { libraryNames() shouldBe listOf("Forest", "Island") }
            assertFailsWith<Throwable> { alice.activates(land, color = color) }
            change(Changes.tap(state, card(land), tapped = false))
            alice.activates(land, color = color)
            assertEquals(listOf(color), state.player(alice.id).manaPool.map { it.color })
        }
    }

    @Test fun sanctumOfAbandon() = sanctum("Sanctum of Abandon", Color.Red, Color.Green)
    @Test fun sanctumOfDeceit() = sanctum("Sanctum of Deceit", Color.Blue, Color.Black)
    @Test fun sanctumOfEnlightenment() = sanctum("Sanctum of Enlightenment", Color.White, Color.Blue)
    @Test fun sanctumOfEpiphany() = sanctum("Sanctum of Epiphany", Color.Blue, Color.Red)
    @Test fun sanctumOfMalady() = sanctum("Sanctum of Malady", Color.Black, Color.Green)
    @Test fun sanctumOfMalice() = sanctum("Sanctum of Malice", Color.Black, Color.Red)
    @Test fun sanctumOfMystery() = sanctum("Sanctum of Mystery", Color.Green, Color.Blue)
    @Test fun sanctumOfPlenty() = sanctum("Sanctum of Plenty", Color.Green, Color.White)
    @Test fun sanctumOfSilence() = sanctum("Sanctum of Silence", Color.White, Color.Black)
    @Test fun sanctumOfTriumph() = sanctum("Sanctum of Triumph", Color.Red, Color.White)
}
