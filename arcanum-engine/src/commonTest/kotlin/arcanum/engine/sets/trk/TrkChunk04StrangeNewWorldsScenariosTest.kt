package arcanum.engine.sets.trk

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Star Trek chunk 04 in play: "the number of differently named lands you control" (CR 201.2) as reveal count, exile count, power
 * boost and cost reduction; the revealed permanent cards put onto the battlefield tapped; an opponent-by-opponent choice of an
 * exiled land to play; protection from opponents' artifact abilities only. Cards that only count the lands repeat the Edge of
 * Eternities shapes. */
class TrkChunk04StrangeNewWorldsScenariosTest {
    private val chronicle = Cards.invent("Splitting Chronicle", "{6}{G}{G}", "Sorcery",
        "Strange new worlds — Reveal the top X cards of your library, where X is the number of differently named lands you control. Put all permanent cards revealed this way onto the battlefield tapped and the rest on the bottom of your library in a random order.")
    private val varnek = Cards.invent("Ensign Varnek", "{3}{U}", "Legendary Creature — Human Pilot",
        "Strange new worlds — When Ensign Varnek enters, each opponent exiles the top X cards of their library, where X is the number of differently named lands you control. For each opponent, choose a land card that player owns exiled this way. You may play the chosen cards for as long as they remain exiled.", "1/5")
    private val beast = Cards.invent("Beast of Octarii", "{4}{G}{G}", "Creature — Insect Beast", """
        Strange new worlds — When this creature enters, you gain life equal to the number of differently named lands you control.
        This creature can't be blocked by artifact creatures.
        This creature can't be the target of abilities from artifact sources your opponents control.
    """, "7/5")
    private val wraith = Cards.invent("Wraith of the Void", "{5}{R}{R}", "Creature — Wraith", """
        First strike, haste
        Strange new worlds — Whenever this creature attacks, creatures you control get +X/+0 until end of turn, where X is the number of differently named lands you control.
    """, "4/4")
    private val splice = Cards.invent("Splice and Phase", "{8}{U}", "Sorcery", """
        Strange new worlds — This spell costs {X} less to cast, where X is the number of differently named lands you control.
        Create two 2/2 black Romulan creature tokens. Draw two cards, then discard a card.
    """)
    private val garden = Cards.invent("Hyperspace Garden", "{2}{G}", "Enchantment", """
        Lands you control enter untapped.
        When this enchantment enters, search your library for a basic land card or Planet card, reveal it, put it into your hand, then shuffle.
        Strange new worlds — {7}{U}{U}, Sacrifice this enchantment: Draw two cards. This ability costs {X} less to activate, where X is the number of differently named lands you control.
    """)
    private val slowLand = Cards.invent("Sluggish Vale", null, "Land", "This land enters tapped.")
    private val prod = Cards.invent("Trk4 Prod", "{1}", "Artifact", "{T}: This artifact deals 1 damage to target creature.")
    private val shrine = Cards.invent("Trk4 Shrine", "{1}", "Enchantment", "{T}: This enchantment deals 1 damage to target creature.")
    private val bear = Cards.invent("Trk4 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk4 Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val study = Cards.invent("Trk4 Quiet Study", "{U}", "Sorcery", "Draw a card.")

    private fun Scenario.cantPay() = assertEquals(emptyList(), decision<Decision.ManaPayment>().suggestion)

    // CR 701.20, 401.4, 201.2: two names among three lands (Forest twice) reveal two cards; the permanent card enters tapped, the sorcery goes to the bottom
    @Test
    fun revealsAsManyCardsAsLandNamesAndPutsPermanentsOntoTheBattlefieldTapped() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Island")
        alice.hand(chronicle)
        alice.library(bear, study, "Mountain", "Swamp")
        alice.castsWithoutCosts(chronicle)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
            study.zone shouldBe Zone.Library
            alice.library.names.takeLast(1) shouldBe listOf("Trk4 Quiet Study")
            alice.library.names.size shouldBe 3
        }
    }

    // CR 101.4, 608.2e, 400.7: each of two opponents exiles two cards; I choose a land from each pile and may play the chosen ones only
    @Test
    fun eachOpponentExilesAndIChooseOneLandFromEachToPlay() = scenario(players = 3) {
        alice.battlefield("Island", "Forest", "Island", "Island", "Island")
        alice.hand(varnek)
        bob.library("Mountain", "Swamp", "Plains")
        carol.library(bear, "Plains", "Plains")
        alice.casts(varnek)
        resolveAll()
        alice.chooses("Mountain")
        resolveAll()
        expect {
            bob.exile.names.toSet() shouldBe setOf("Mountain", "Swamp")
            carol.exile.names.toSet() shouldBe setOf("Plains", "Trk4 Bear")
        }
        alice.plays("Mountain")
        expect { "Mountain".zone shouldBe Zone.Battlefield }
        assertFailsWith<IllegalArgumentException> { alice.plays("Swamp") }
    }

    // CR 115.1: only abilities of artifact sources an opponent controls can't target it; a non-artifact source's ability can
    @Test
    fun cantBeTargetedByAnOpponentsArtifactAbilityButByOthers() = scenario(players = 3) {
        alice.battlefield(beast)
        bob.battlefield(prod, shrine)
        turn(number = 1, active = alice, priority = bob)
        assertFailsWith<IllegalArgumentException> { bob.activates(prod, target = beast) }
        bob.activates(shrine, target = beast)
        resolveAll()
        expect { beast.view.obj?.damage shouldBe 1 }
    }

    // CR 603.2, 107.3: Island and Forest and Mountain are three names, so each creature gets +3/+0 until end of turn
    @Test
    fun attackingGivesEveryCreatureYouControlPlusTheNumberOfLandNames() = scenario(players = 3) {
        alice.battlefield(wraith, ox, "Island", "Forest", "Forest", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wraith)
        resolveAll()
        expect {
            wraith.power shouldBe 7
            ox.power shouldBe 6
        }
    }

    // CR 601.2f, 107.3: {8}{U} costs three less with three land names; five lands can't pay {5}{U}
    @Test
    fun costsLessByTheNumberOfLandNames() = scenario {
        alice.battlefield("Island", "Island", "Forest", "Forest", "Mountain", "Mountain")
        alice.hand(splice)
        alice.library("Island", "Island", "Island")
        alice.casts(splice)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Romulan Token" } shouldBe 2 }
    }

    @Test
    fun fiveLandsCantPayTheReducedCost() = scenario {
        alice.battlefield("Island", "Island", "Island", "Forest", "Mountain")
        alice.hand(splice)
        alice.casts(splice)
        cantPay()
    }

    // CR 614.1d, 616.1: the land's own "enters tapped" and the Garden's "enter untapped" are both offered; "enter untapped" is applied after
    // the others (engine design from Final Fantasy), so the land is untapped
    private fun Scenario.landEntersChoosing(garden1: Boolean): Boolean? {
        val choice = pending as Decision.ChooseReplacement
        answer(arcanum.engine.decision.Answer.Replacement(choice.options.first { (it.source == card(garden)) == garden1 }.index))
        return null
    }

    @Test
    fun landsEnterUntappedWhateverTheirOwnReplacementSays() = scenario {
        alice.battlefield(garden, "Island", "Forest")
        alice.hand(slowLand)
        alice.plays(slowLand)
        landEntersChoosing(garden1 = false)
        expect { slowLand.tapped shouldBe false }
    }

    // CR 601.2f, 602.2: {7}{U}{U} costs three less with three land names, so six lands pay it
    @Test
    fun theGardensAbilityCostsLess() = scenario {
        alice.battlefield(garden, "Island", "Island", "Island", "Island", "Forest", "Mountain")
        alice.library("Island", "Island", "Island")
        alice.activates(garden)
        resolveAll()
        expect { garden.zone shouldBe Zone.Graveyard }
    }
}
