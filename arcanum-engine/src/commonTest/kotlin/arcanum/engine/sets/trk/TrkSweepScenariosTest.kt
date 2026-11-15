package arcanum.engine.sets.trk

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.pay
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.fin.targets
import arcanum.engine.sets.fin.yes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Star Trek sweep: siblings of the rules fixes found while working the set, each played here. */
class TrkSweepScenariosTest {
    private val grimDemand = Cards.invent("Trk Sweep Grim Demand", "{1}{B}", "Sorcery", "Each opponent sacrifices a creature.")
    private val anchoredGolem = Cards.invent("Trk Sweep Anchored Golem", "{3}", "Artifact Creature — Golem", "This creature can't be sacrificed.", "2/2")
    private val bear = Cards.invent("Trk Sweep Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk Sweep Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val simDeck = Cards.invent("Trk Sweep Holodeck Archive", "{3}{G}", "Artifact",
        "Once during each of your turns, you may play a permanent card you own from outside the game or from your graveyard.")
    private val graveCaller = Cards.invent("Trk Sweep Grave Caller", "{2}{U}", "Creature — Human Wizard",
        "When this creature enters, you may cast target instant or sorcery card from a graveyard, and mana of any type can be spent to cast that spell. If that spell would be put into a graveyard, exile it instead.",
        "2/2")
    private val hollowZap = Cards.invent("Trk Sweep Hollow Zap", "{C}", "Instant", "This spell deals 2 damage to any target.")
    private val garden = Cards.invent("Trk Sweep Warp Garden", "{2}{G}", "Enchantment", "Lands you control enter untapped.")
    private val mireWard = Cards.invent("Trk Sweep Mire Ward", "{2}{B}", "Enchantment", "Lands your opponents control enter tapped.")
    private val pathfinding = Cards.invent("Trk Sweep Pathfinding", "{G}", "Sorcery",
        "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    private val drone = Cards.invent("Trk Sweep Warded Drone", "{1}{U}", "Creature — Drone", "Hexproof from artifacts", "2/2")
    private val sharedProd = Cards.invent("Trk Sweep Shared Prod", "{1}", "Artifact",
        "{1}: This artifact deals 1 damage to target creature. Any player may activate this ability.")
    private val unevenBlessing = Cards.invent("Trk Sweep Uneven Blessing", "{1}{W}", "Sorcery",
        "Target creature gets +3/+3 until end of turn. Each other creature gets -1/-1 until end of turn.")

    // CR 701.21a: a player can't sacrifice a permanent that can't be sacrificed, so an "each opponent sacrifices a creature" takes another
    // creature they control; with three players each opponent sacrifices one
    @Test
    fun anEdictTakesACreatureThatCanBeSacrificed() = scenario(players = 3) {
        alice.hand(grimDemand)
        bob.battlefield(anchoredGolem, bear)
        carol.battlefield(bear)
        alice.castsWithoutCosts(grimDemand)
        resolveAll()
        (pending as? Decision.ChooseObjects)?.let { d ->
            assertFalse(card(anchoredGolem) in d.options, "offered a creature that can't be sacrificed")
            answer(Answer.Objects(listOf(card(bear, owner = bob))))
            resolveAll()
        }
        expect {
            bob.battlefield has anchoredGolem
            bob.graveyard has bear
            carol.graveyard has bear
        }
    }

    // CR 400.11, 305.2: a land played from outside the game is the turn's land play, so no other land can be played
    @Test
    fun aLandPlayedFromOutsideTheGameIsTheTurnsLandPlay() = scenario {
        alice.battlefield(simDeck)
        alice.outside("Forest")
        alice.hand("Island")
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.PlayLand>().any { it.card == card("Forest", zone = Zone.Outside) })
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.PlayLand>().none { it.card == card("Island") })
    }

    // CR 106.1b, 609.4b, 608.2g: a spell cast while an ability resolves with "mana of any type can be spent" can pay {C} with a Plains's white
    @Test
    fun aSpellCastWhileResolvingWithManaOfAnyTypePaysColorless() = scenario {
        alice.battlefield("Island", "Island", "Island", "Plains")
        alice.hand(graveCaller)
        bob.graveyard(hollowZap)
        alice.casts(graveCaller, paying = listOf("Island", "Island", "Island"))
        resolveAll()
        yes()
        targets("bob")
        pay()
        resolveAll()
        expect {
            bob.life shouldBe 18
            hollowZap.zone shouldBe Zone.Exile
        }
    }

    private fun Scenario.chooseReplacement(gardenFirst: Boolean) {
        val d = pending as Decision.ChooseReplacement
        assertEquals(alice.id, d.player, "the land's controller orders the replacements")
        answer(Answer.Replacement(d.options.first { (it.source == card(garden)) == gardenFirst }.index))
    }

    // CR 616.1, 616.1e, 616.1f: an opponent's "enter tapped" and your "enter untapped" both apply to your land; you, its controller, choose
    // the order, and the one applied last decides; with three players
    @Test
    fun yourLandEntersUntappedWhenYouApplyYourGardenLast() = scenario(players = 3) {
        alice.battlefield(garden)
        bob.battlefield(mireWard)
        alice.hand("Forest")
        alice.plays("Forest")
        chooseReplacement(gardenFirst = false)
        expect { "Forest".tapped shouldBe false }
    }

    @Test
    fun yourLandEntersTappedWhenYouApplyTheOpponentsEffectLast() = scenario(players = 3) {
        alice.battlefield(garden)
        bob.battlefield(mireWard)
        alice.hand("Forest")
        alice.plays("Forest")
        chooseReplacement(gardenFirst = true)
        expect { "Forest".tapped shouldBe true }
    }

    // CR 614.1d: "Lands you control enter untapped" replaces how a land an effect puts onto the battlefield tapped enters
    @Test
    fun aLandPutOntoTheBattlefieldTappedEntersUntappedWithTheGarden() = scenario {
        alice.battlefield(garden)
        alice.hand(pathfinding)
        alice.library("Forest", "Island")
        alice.castsWithoutCosts(pathfinding)
        resolveAll()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(listOf(card("Forest", zone = Zone.Library)))); resolveAll() }
        expect { "Forest".tapped shouldBe false }
    }

    // CR 702.11d: "hexproof from artifacts" stops abilities an opponent controls from artifact sources, whoever controls the source (unlike
    // "abilities from artifact sources your opponents control", CR 113.7); its controller's own artifact abilities still may target it
    @Test
    fun hexproofFromArtifactsStopsAnOpponentsAbilityFromYourArtifact() = scenario {
        alice.battlefield(drone, sharedProd, "Island")
        bob.battlefield("Swamp")
        turn(number = 1, active = alice, priority = alice)
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates(sharedProd, target = drone, paying = listOf("Swamp")) }
    }

    @Test
    fun hexproofFromArtifactsLetsItsControllersArtifactTargetIt() = scenario {
        alice.battlefield(drone, sharedProd, "Island")
        alice.activates(sharedProd, target = drone, paying = listOf("Island"))
        resolveAll()
        expect { drone.view.obj?.damage shouldBe 1 }
    }

    // CR 608.2c: in a spell with a target creature, "each other creature" is each creature other than that target (the spell is no creature)
    @Test
    fun eachOtherCreatureInASpellIsOtherThanItsTarget() = scenario {
        alice.battlefield(bear)
        bob.battlefield(ox)
        alice.hand(unevenBlessing)
        alice.castsWithoutCosts(unevenBlessing, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            ox.power shouldBe 2
        }
    }
}
