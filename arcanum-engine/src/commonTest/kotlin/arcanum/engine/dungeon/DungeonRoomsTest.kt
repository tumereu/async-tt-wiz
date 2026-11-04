package arcanum.engine.dungeon

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The dungeon cards' room effects (CR 309.4c) read since the dungeons of the local card data are all played: each on an invented
 * dungeon whose room uses the printed template, entered by an invented sorcery that ventures into the dungeon.
 */
class DungeonRoomsTest {
    private val map = Cards.invent("Room Test Map", "{W}", "Sorcery", "Venture into the dungeon.")
    private val raider = Cards.invent("Room Test Raider", "{1}{R}", "Creature — Goblin", "", "2/1")
    private val ogre = Cards.invent("Room Test Ogre", "{5}{G}{G}", "Creature — Ogre", "", "5/5")
    private val idol = Cards.invent("Room Test Idol", "{2}", "Artifact", "")
    private val totem = Cards.invent("Room Test Totem", "{1}", "Artifact", "")
    private val golem = Cards.invent("Room Test Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val hound = Cards.invent("Room Test Hound", "{1}{G}", "Creature — Dog", "", "2/2")

    /** A one-room invented dungeon whose room is "[room] — [effect]". */
    private fun dungeon(name: String, room: String, effect: String) = Cards.invent(name, null, "Dungeon", "$room — $effect")

    /** [Scenario.alice] casts a map (with a Plains of hers), whose venture brings [dungeon] in; its room ability waits to be put on the Stack. */
    private fun Scenario.venture(dungeon: String) {
        alice.outside(dungeon)
        alice.hand(map)
        alice.casts(map)
        allPass()
    }

    // CR 309.4c, 508.1c, 611.2a: "Target creature can't attack until your next turn." The opponent's creature can't attack during
    // their next turn; the effect ends as the room's owner's next turn begins
    @Test
    fun targetCreatureCantAttackUntilYourNextTurn() = scenario {
        val warren = dungeon("Room Test Snaring Warren", "Snaring Hollow", "Target creature can't attack until your next turn.")
        alice.battlefield("Plains")
        alice.library("Plains", "Plains")
        bob.library("Mountain", "Mountain")
        bob.battlefield(raider)
        venture(warren)
        // The raider is the only creature: the target is chosen without asking
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(raider))))
        allPass()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        assertFailsWith<IllegalArgumentException> { bob.attacks(raider) }
        advanceTo(TurnStep.DeclareAttackers, turn = 4)
        bob.attacks(raider)
        advanceTo(TurnStep.EndOfCombat, turn = 4)
        expect { alice.life shouldBe 18 }
    }

    // CR 309.4c, 400.7: "Exile the top two cards of your library. You may play them." A land is played from exile this turn and the
    // other card cast from exile on a later turn: the permission has no end
    @Test
    fun exiledCardsMayBePlayedWithoutEnd() = scenario {
        val vault = dungeon("Room Test Glyph Vault", "Glyph Vault", "Exile the top two cards of your library. You may play them.")
        alice.battlefield("Plains", "Forest")
        alice.library("Island", hound, "Plains", "Plains")
        bob.library("Mountain", "Mountain")
        venture(vault)
        allPass()
        expect {
            "Island".zone shouldBe Zone.Exile
            hound.zone shouldBe Zone.Exile
        }
        alice.plays("Island")
        expect { alice.battlefield has "Island" }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(hound)
        allPass()
        expect { alice.battlefield has hound }
    }

    // CR 309.4c, 601.2, 608.2g, 701.20a: "Draw three cards and reveal them. You may cast one of them without paying its mana cost."
    // The drawn creature is cast for free while the room ability resolves; the land drawn can't be cast
    @Test
    fun castOneOfTheDrawnCardsForFree() = scenario {
        val lair = dungeon("Room Test Archmage's Study", "Archmage's Study",
            "Draw three cards and reveal them. You may cast one of them without paying its mana cost.")
        alice.battlefield("Plains")
        alice.library(ogre, "Forest", idol)
        venture(lair)
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(setOf(ogre, idol), d.options.mapNotNull { id -> state.obj(id)?.definition?.faces?.first()?.name }.toSet(), "Castable: $d")
        alice.chooses(ogre)
        allPass()
        expect {
            alice.battlefield has ogre
            alice.hand has "Forest"
            alice.hand has idol
        }
    }

    // CR 101.4, 118.12a, 309.4c: "Each player loses 2 life unless they discard a card." Alice keeps her card and loses the life;
    // Bob discards and doesn't
    @Test
    fun eachPlayerLosesLifeUnlessTheyDiscard() = scenario {
        val veil = dungeon("Room Test Weeping Veil", "Weeping Veil", "Each player loses 2 life unless they discard a card.")
        alice.battlefield("Plains")
        alice.hand("Plains")
        bob.hand("Mountain")
        venture(veil)
        allPass()
        payOrNot(pay = false)
        payOrNot(pay = true)
        if (pending is Decision.ChooseObjects) bob.chooses("Mountain")
        allPass()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 20
            alice.hand has "Plains"
            bob.graveyard has "Mountain"
        }
    }

    // CR 101.4, 118.12a, 309.4c: "Each player loses 2 life unless they sacrifice a creature, artifact, or land of their choice." Alice
    // sacrifices an artifact; Bob, who controls none of those, can't pay and loses the life
    @Test
    fun eachPlayerLosesLifeUnlessTheySacrifice() = scenario {
        val sands = dungeon("Room Test Sinking Sands", "Sinking Sands",
            "Each player loses 2 life unless they sacrifice a creature, artifact, or land of their choice.")
        alice.battlefield("Plains", idol)
        venture(sands)
        allPass()
        payOrNot(pay = true)
        if (pending is Decision.ChooseObjects) alice.chooses(idol)
        if (pending is Decision.May) payOrNot(pay = true)
        allPass()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 18
            alice.graveyard has idol
        }
    }

    // CR 309.4c, 701.9a, 701.21a: "Discard a card and sacrifice a creature, an artifact, and a land." The artifact creature chosen as
    // the creature isn't offered again as the artifact: three different permanents are sacrificed
    @Test
    fun discardAndSacrificeThreeDifferentPermanents() = scenario {
        val pit = dungeon("Room Test Forgotten Pit", "Forgotten Pit", "Discard a card and sacrifice a creature, an artifact, and a land.")
        alice.battlefield("Plains", "Forest", golem, hound, idol, totem)
        alice.hand("Mountain")
        venture(pit)
        allPass()
        if (pending is Decision.ChooseObjects && decision<Decision.ChooseObjects>().options.any { state.obj(it)?.zone == Zone.Hand }) alice.chooses("Mountain")
        alice.chooses(golem)
        val artifacts = decision<Decision.ChooseObjects>().options.mapNotNull { state.obj(it)?.definition?.faces?.first()?.name }
        assertEquals(setOf(idol, totem), artifacts.toSet(), "The golem counts once")
        alice.chooses(idol)
        alice.chooses("Forest")
        allPass()
        expect {
            alice.graveyard has "Mountain"
            alice.graveyard has golem
            alice.graveyard has idol
            alice.graveyard has "Forest"
            alice.battlefield has hound
            alice.battlefield has totem
            alice.battlefield has "Plains"
        }
    }

    // CR 122.6, 309.4c, 400.7, 701.20a, 701.24a: "Reveal the top ten cards of your library. Put a creature card from among them onto the
    // battlefield with three +1/+1 counters on it. It gains hexproof until your next turn. Then shuffle."
    @Test
    fun aRevealedCreatureEntersWithCountersAndHexproof() = scenario {
        val throne = dungeon("Room Test Bone Throne", "Bone Throne",
            "Reveal the top ten cards of your library. Put a creature card from among them onto the battlefield with three +1/+1 " +
                "counters on it. It gains hexproof until your next turn. Then shuffle.")
        alice.battlefield("Plains")
        alice.library("Forest", "Forest", hound, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.library("Mountain", "Mountain")
        venture(throne)
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(hound)
        allPass()
        expect {
            alice.battlefield has hound
            hound.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            hound.power shouldBe 5
            hound hasKeyword Keyword.Hexproof
            alice.library.size shouldBe 10
        }
        // CR 611.2a: until Alice's next turn
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { hound lacksKeyword Keyword.Hexproof }
    }

    /** Answers the open "pay?" question of an "unless" cost. */
    private fun Scenario.payOrNot(pay: Boolean) {
        val d = pending
        assertTrue(d is Decision.May, "Expected whether to pay: $d")
        answer(Answer.YesNo(pay))
    }
}
