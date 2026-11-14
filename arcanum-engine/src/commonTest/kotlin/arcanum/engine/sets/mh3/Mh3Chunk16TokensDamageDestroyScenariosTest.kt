package arcanum.engine.sets.mh3

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The MH3 cards of chunk 16, played: the invented names carry the oracle text of Glaring Fleshraker, Ophiomancer, Reef Worm,
 * Wurmcoil Larva, Gravedig, Muster the Departed, Thraben Charm, Collective Resistance, Siege Smash, Sundering Eruption, White
 * Orchid Phantom and Fell the Profane (costs cut where they don't change play).
 */
class Mh3Chunk16TokensDamageDestroyScenariosTest {
    private val drone = Cards.invent("Glaring Drone", "{2}{C}", "Creature — Eldrazi Drone", """
        Whenever you cast a colorless spell, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        Whenever another colorless creature you control enters, this creature deals 1 damage to each opponent.
    """, "2/2")
    private val husk = Cards.invent("Pale Husk", "{1}", "Artifact Creature — Construct", "", "1/1")
    private val imp = Cards.invent("Red Imp", "{R}", "Creature — Imp", "", "1/1")
    private val ophiomaster = Cards.invent("Ophiomaster", "{2}{B}", "Creature — Human Shaman",
        "At the beginning of each upkeep, if you control no Snakes, create a 1/1 black Snake creature token with deathtouch.", "2/2")
    private val worm = Cards.invent("Pond Worm", "{3}{U}", "Creature — Worm",
        "When this creature dies, create a 3/3 blue Fish creature token with \"When this token dies, create a 6/6 blue Whale creature token with 'When this token dies, create a 9/9 blue Kraken creature token.'\"", "0/1")
    private val larva = Cards.invent("Larva of Wurmcoil", "{3}{B}{B}", "Artifact Creature — Phyrexian Wurm", """
        Deathtouch, lifelink
        When this creature dies, create a 1/2 black Phyrexian Wurm artifact creature token with deathtouch and a 2/1 black Phyrexian Wurm artifact creature token with lifelink.
    """, "3/3")
    private val doomMark = Cards.invent("Doom Mark", "{B}", "Instant", "Destroy target creature.")
    private val gravefill = Cards.invent("Gravefill", "{1}{B}", "Sorcery", """
        Choose one —
        • Target player creates a 2/2 black Zombie creature token.
        • Return target creature card from your graveyard to your hand.
        Entwine {2} (Choose both if you pay the entwine cost.)
    """)
    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val gather = Cards.invent("Gather the Departed", "{2}{W}", "Enchantment", """
        When this enchantment enters, create a 1/1 white Spirit creature token with flying.
        Morbid — At the beginning of your end step, if a creature died this turn, populate. (Create a token that's a copy of a creature token you control.)
    """)
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val vow = Cards.invent("Thraben Vow", "{1}{W}", "Instant", """
        Choose one —
        • Thraben Vow deals damage equal to twice the number of creatures you control to target creature.
        • Destroy target enchantment.
        • Exile any number of target players' graveyards.
    """)
    private val ogre = Cards.invent("Moor Ogre", "{3}{R}", "Creature — Ogre", "", "5/5")
    private val shared = Cards.invent("Shared Resistance", "{1}{G}", "Instant", """
        Escalate {G} (Pay this cost for each mode chosen beyond the first.)
        Choose one or more —
        • Destroy target artifact.
        • Destroy target enchantment.
        • Target creature gains hexproof and indestructible until end of turn.
    """)
    private val crush = Cards.invent("Siege Crush", "{1}{R}", "Instant", """
        Split second (As long as this spell is on the stack, players can't cast spells or activate abilities that aren't mana abilities.)
        Choose one —
        • Destroy target artifact.
        • Target creature gets +3/+2 and gains trample until end of turn.
    """)
    private val blast = Cards.inventFaces("ModalDoubleFaced",
        face("Sundering Blast", "{2}{R}", "Sorcery",
            "Destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle. Creatures without flying can't block this turn.",
            null, null, null),
        face("Volcanic Rift", null, "Land", """
            As this land enters, you may pay 3 life. If you don't, it enters tapped.
            {T}: Add {R}.
        """, null, null, null))
    private val wraith = Cards.invent("White Orchid Wraith", "{W}{W}", "Creature — Spirit Knight", """
        Flying, first strike
        When this creature enters, destroy up to one target nonbasic land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """, "2/2")
    private val gate = Cards.invent("Hollowstone Flats", null, "Land", "{T}: Add {C}.")
    private val fell = Cards.inventFaces("ModalDoubleFaced",
        face("Fell the Unclean", "{2}{B}{B}", "Instant", "Destroy target creature or planeswalker.", null, null, null),
        face("Fell Marsh", null, "Land", """
            As this land enters, you may pay 3 life. If you don't, it enters tapped.
            {T}: Add {B}.
        """, null, null, null))

    // CR 603.2, 111.1, 120.3: casting a colorless spell makes a Spawn; the Pale Husk and the colorless Spawn token each ping every opponent (three players)
    @Test
    fun droneMakesASpawnAndPingsEachOpponent() = scenario(players = 3) {
        alice.battlefield(drone, "Mountain", "Mountain")
        alice.hand(husk, imp)
        alice.casts(husk)
        resolveAll()
        expect {
            alice.battlefield has "Eldrazi Spawn Token"
            alice.battlefield has husk
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // CR 105.2: a red spell isn't colorless, and a red creature entering isn't either
    @Test
    fun droneIgnoresColoredSpellsAndCreatures() = scenario(players = 3) {
        alice.battlefield(drone, "Mountain", "Mountain")
        alice.hand(imp)
        alice.casts(imp)
        resolveAll()
        expect {
            alice.battlefield.size shouldBe 4
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 503.1, 603.4: each player's upkeep triggers it for its controller, only while they control no Snake
    @Test
    fun ophiomasterMakesOneSnakeInAnyUpkeep() = scenario(players = 3) {
        alice.battlefield(ophiomaster)
        alice.library("Forest", "Forest", "Forest")
        bob.library("Forest", "Forest", "Forest")
        carol.library("Forest", "Forest", "Forest")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Snake Token" } shouldBe 1 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Snake Token" } shouldBe 1 }
    }

    // CR 603.10a, 111.1: each token's own dies trigger makes the next, bigger one
    @Test
    fun pondWormChainsFishWhaleAndKraken() = scenario {
        alice.battlefield(worm, "Swamp", "Swamp", "Swamp")
        alice.hand(doomMark, doomMark, doomMark)
        alice.casts(doomMark, target = worm)
        resolveAll()
        alice.casts(doomMark, target = "Fish Token")
        resolveAll()
        alice.casts(doomMark, target = "Whale Token")
        resolveAll()
        expect {
            alice.battlefield has "Kraken Token"
            alice.battlefield.size shouldBe 4
            "Kraken Token".power shouldBe 9
            "Kraken Token".toughness shouldBe 9
        }
    }

    // CR 111.1: both tokens of one trigger, each with its own keyword
    @Test
    fun larvaLeavesADeathtouchWurmAndALifelinkWurm() = scenario {
        alice.battlefield(larva, "Swamp")
        alice.hand(doomMark)
        alice.casts(doomMark, target = larva)
        resolveAll()
        expect {
            val wurm = "Phyrexian Wurm Token"
            alice.battlefield.names.count { it == wurm } shouldBe 2
            val powers = listOf(card(wurm, 0).power ?: 0, card(wurm, 1).power ?: 0).sorted()
            powers shouldBe listOf(1, 2)
            val big = if (card(wurm, 0).power == 2) card(wurm, 0) else card(wurm, 1)
            val small = if (card(wurm, 0).power == 2) card(wurm, 1) else card(wurm, 0)
            big hasKeyword Keyword.Lifelink.kind
            small hasKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 702.42a, 700.2: entwined, both modes: the chosen player (an opponent) gets the Zombie and the creature card returns
    @Test
    fun gravefillEntwinedGivesAnOpponentAZombieAndReturnsACreature() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear)
        alice.hand(gravefill)
        alice.casts(gravefill, modes = listOf(0, 1), targets = listOf("carol", "Reaping Bear"), kicked = 1)
        resolveAll()
        expect {
            carol.battlefield has "Zombie Token"
            alice.hand has bear
        }
    }

    // CR 700.4, 701.36a: a creature died this turn, so the end step populates the Spirit token
    @Test
    fun gatherPopulatesAfterACreatureDied() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain", bear)
        alice.hand(gather, shock)
        alice.casts(gather)
        resolveAll()
        alice.casts(shock, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Spirit Token" } shouldBe 2 }
    }

    // CR 603.4: nothing died, so the intervening "if" stops the trigger
    @Test
    fun gatherDoesNothingWhenNothingDied() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(gather)
        alice.casts(gather)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Spirit Token" } shouldBe 1 }
    }

    // CR 120.3, 700.2: twice the number of creatures you control
    @Test
    fun vowDealsTwiceTheNumberOfYourCreatures() = scenario {
        alice.battlefield("Plains", "Plains", bear, bear)
        bob.battlefield(ogre)
        alice.hand(vow)
        alice.casts(vow, modes = listOf(0), target = ogre)
        resolveAll()
        expect { card(ogre).view.obj?.damage shouldBe 4 }
    }

    // CR 700.2, 115.1: "any number of target players' graveyards": two of three players are chosen
    @Test
    fun vowExilesTheChosenPlayersGraveyards() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(bear)
        bob.graveyard(ogre)
        carol.graveyard(imp)
        alice.hand(vow)
        alice.casts(vow, modes = listOf(2), targets = listOf("bob", "carol"))
        resolveAll()
        expect {
            bob.graveyard.isEmpty()
            carol.graveyard.isEmpty()
            alice.graveyard has bear
            bob.exile has ogre
        }
    }

    // CR 702.120a: all three modes cost {1}{G} plus {G} twice
    @Test
    fun sharedResistanceWithAllThreeModes() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear)
        bob.battlefield(husk, gather)
        alice.hand(shared)
        alice.casts(shared, modes = listOf(0, 1, 2), targets = listOf(husk, gather, bear))
        resolveAll()
        expect {
            bob.battlefield lacks husk
            bob.battlefield lacks gather
            bear hasKeyword Keyword.Hexproof.kind
            bear hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 702.61a, 117.1a: while Siege Crush is on the Stack the opponent can't cast a spell in response
    @Test
    fun siegeCrushCantBeRespondedTo() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        bob.battlefield("Mountain", husk)
        alice.hand(crush)
        bob.hand(shock)
        alice.casts(crush, modes = listOf(1), target = bear)
        alice.passes()
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast })
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    // CR 701.8a, 608.2h: the destroyed land's controller (last known) searches; 611.2c: creatures without flying can't block this turn
    @Test
    fun sundersAnOpponentsLandWhoSearchesAndNoBlockersWithoutFlying() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        bob.battlefield("Forest", ogre)
        bob.library("Island", "Island")
        alice.hand(blast)
        alice.casts(blast, target = "Forest")
        resolveAll()
        bob.answers(Answer.YesNo(true))
        bob.chooses("Island")
        resolveAll()
        expect {
            bob.battlefield has "Island"
            bob.battlefield lacks "Forest"
            "Island".tapped shouldBe true
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(ogre to bear) }
    }

    // CR 712.4, 614.1c: playing the land face, paying 3 life it enters untapped
    @Test
    fun landFacePaidEntersUntapped() = scenario {
        alice.hand(blast)
        alice.plays(blast, face = 1)
        alice.answers(Answer.YesNo(true))
        expect {
            "Volcanic Rift".tapped shouldBe false
            alice.life shouldBe 17
        }
    }

    // CR 614.1c: not paying, it enters tapped and costs nothing
    @Test
    fun landFaceUnpaidEntersTapped() = scenario {
        alice.hand(fell)
        alice.plays(fell, face = 1)
        alice.answers(Answer.YesNo(false))
        expect {
            "Fell Marsh".tapped shouldBe true
            alice.life shouldBe 20
        }
    }

    // CR 601.2c, 701.8a: "up to one" with no target chosen: nothing is destroyed and nobody searches
    @Test
    fun wraithWithNoTargetDoesNothing() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(gate)
        bob.library("Island", "Island")
        alice.hand(wraith)
        alice.casts(wraith)
        resolveAll()
        alice.answers(Answer.Targets(emptyList()))
        resolveAll()
        expect {
            bob.battlefield has gate
            bob.library.size shouldBe 2
        }
    }

    // CR 701.8a: the targeted nonbasic land is destroyed and its controller (bob, not the caster) may search for a basic
    @Test
    fun wraithDestroysANonbasicLandAndItsControllerSearches() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(gate)
        bob.library("Island", "Island")
        alice.hand(wraith)
        alice.casts(wraith)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(gate))))
        resolveAll()
        bob.answers(Answer.YesNo(true))
        bob.chooses("Island")
        resolveAll()
        expect {
            bob.battlefield lacks gate
            bob.battlefield has "Island"
        }
    }
}
