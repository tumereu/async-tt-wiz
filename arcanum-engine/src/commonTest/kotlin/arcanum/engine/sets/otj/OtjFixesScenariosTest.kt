package arcanum.engine.sets.otj

import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.resolveAll
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertTrue

/** OTJ fixes: rules findings from the set's chunks, fixed generally. */
class OtjFixesScenariosTest {
    private val firebird = Cards.invent("Emberwake Firebird", "{2}{R}{R}", "Creature — Phoenix", """
        Flying
        When this creature dies, create a 0/1 red Elemental creature token with "At the beginning of your upkeep, sacrifice this token and return target card named Emberwake Firebird from your graveyard to the battlefield. It gains haste until end of turn."
    """, "4/3")
    private val coach = Cards.invent("Velvet Rail Coach", "{5}", "Artifact — Vehicle", """
        Whenever this Vehicle attacks, create a Treasure token for each creature that crewed it this turn. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
        Crew 1. Activate only once each turn. (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "6/5")
    private val scorch = Cards.invent("Mesa Flashfire", "{R}", "Instant", "Mesa Flashfire deals 4 damage to target creature.")
    private val drover = Cards.invent("Dusty Drover", "{W}", "Creature — Human Peasant", "", "1/1")
    private val wrangler = Cards.invent("Corral Wrangler", "{1}{G}", "Creature — Human Ranger", "", "2/2")
    private val magistrate = Cards.invent("Ledgerkeep Magistrate", "{1}{W}{B}", "Legendary Creature — Human Advisor", """
        Whenever one or more tokens your opponents control enter, for each of them, create a tapped token that's a copy of it. This ability triggers only once each turn.
        Whenever one or more tokens you control enter, each opponent loses 1 life and you gain 1 life.
    """, "2/4")
    private val muster = Cards.invent("Posse Muster Call", "{2}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")
    private val drifter = Cards.invent("Topdeck Drifter", "{1}{U}{U}", "Legendary Creature — Homunculus", """
        Ward {2}
        You may look at the top card of your library any time.
        The top card of your library has plot. The plot cost is equal to its mana cost.
        You may plot nonland cards from the top of your library.
    """, "1/1")
    private val ambusher = Cards.invent("Gulch Ambusher", "{2}{R}", "Creature — Human Rogue",
        "Plot {1}{R} (You may pay {1}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plotting is a sorcery.)", "3/2")
    private infix fun <T> T.shouldBe(expected: T) = kotlin.test.assertEquals(expected, this)

    // CR 400.7j: "return target card … to the battlefield. It gains haste": the effect finds the permanent the card became, though
    // the model names the target card in the graveyard
    @Test
    fun returnedCardGainsHasteAsTheNewPermanent() = scenario {
        alice.battlefield(firebird, "Mountain")
        alice.hand(scorch)
        alice.library("Mountain", "Mountain")
        bob.library("Mountain", "Mountain")
        alice.casts(scorch, target = firebird)
        resolveAll()
        tokens("Elemental Token").size shouldBe 1
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(firebird)
        expect {
            firebird.zone shouldBe Zone.Battlefield
            firebird hasKeyword Keyword.Haste.kind
        }
        tokens("Elemental Token").size shouldBe 0
    }

    // CR 109.2: "each creature that crewed it this turn" names creature permanents on the battlefield (no zone, no "card"), so a crewer
    // that left the battlefield before the attack trigger resolves isn't counted (CR 702.122c)
    @Test
    fun crewerThatLeftTheBattlefieldIsNotCounted() = scenario {
        alice.battlefield(coach, drover, wrangler)
        alice.activates(coach, choosing = listOf(drover, wrangler))
        resolveAll()
        move(drover, Zone.Graveyard)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(coach)
        resolveAll()
        tokens("Treasure Token").size shouldBe 1
    }

    // CR 608.2f: "for each of them, create a token" creates the tokens simultaneously, one event, so "whenever one or more tokens you control
    // enter" triggers once (CR 603.2c); before, each copy entered alone and the second ability triggered once per token
    @Test
    fun tokensCreatedForEachEnterTogether() = scenario(players = 3) {
        alice.battlefield(magistrate)
        bob.battlefield("Plains", "Plains", "Plains")
        bob.hand(muster)
        turn(number = 2, active = bob)
        bob.casts(muster)
        resolveAll()
        tokens("Soldier Token").size shouldBe 4
        expect {
            alice.life shouldBe 21
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    /** The plot special actions [name] offers alice now (CR 702.170a, b). */
    private fun arcanum.engine.scenario.Scenario.plotActions(name: String) =
        Engine.options(state, alice.id).filterIsInstance<Input.Activate>().filter { it.source == card(name) }

    // CR 702.170a, f: the top card of your library has plot for its mana cost and is plotted from there, exiled from the library;
    // a land on top can't be (only nonland cards)
    @Test
    fun plotsTheTopCardOfTheLibraryForItsManaCost() = scenario {
        alice.battlefield(drifter, "Mountain", "Mountain")
        alice.library(scorch, "Island")
        perform(plotActions(scorch).single())
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertTrue(state.objOrThrow(card(scorch)).let { it.zone == Zone.Exile && ObjectMark.Plotted in it.marks })
        state.battlefield.count { state.objOrThrow(it).tapped } shouldBe 1
        plotActions("Island").size shouldBe 0
    }

    // CR 702.170a: plot works only from a hand; a card on top of a library with plot of its own can't be plotted there without a permission
    @Test
    fun plotDoesNotWorkFromTheLibraryWithoutPermission() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.library(ambusher)
        plotActions(ambusher).size shouldBe 0
    }
}
