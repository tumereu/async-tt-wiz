package arcanum.engine.sets.woe

import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.fin.choosePlayer
import arcanum.engine.sets.hob.pickTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WOE chunk 07, fights, mills, Treasures and cards that have an Adventure: the invented names carry the oracle text of Curse of the
 * Werefox, Faunsbane Troll, Beluna Grandsquall // Seek Thrills, Howling Galefang, Decadent Dragon // Expensive Taste, Discerning
 * Financier and Spell Stutter.
 */
class WoeChunk07FightMillTreasureScenariosTest {
    private val boar = Cards.invent("Brambleback Boar", "{1}{G}", "Creature — Boar", "", "2/2")
    private val yearling = Cards.invent("Glade Yearling", "{1}{G}", "Creature — Elk", "", "2/2")
    private val brute = Cards.invent("Thornhide Brute", "{2}{G}", "Creature — Beast", "", "3/3")
    private val courier = Cards.inventFaces("Adventure",
        face("Briar Courier", "{2}{G}", "Creature — Human Scout", "", "2/2", null, null),
        face("Swift Errand", "{G}", "Sorcery — Adventure", "Draw a card. (Then exile this card. You may cast the creature later from exile.)", null, null, null))
    private val bramblekin = Cards.invent("Plain Bramblekin", "{2}{G}", "Creature — Elemental", "", "2/2")
    private val trinket = Cards.invent("Gilt Trinket", "{1}", "Artifact — Treasure", "{T}, Sacrifice this artifact: Add one mana of any color.")
    private val sprite = Cards.invent("Thistle Sprite", "{U}", "Creature — Faerie", "Flying", "1/1")
    private val notes = Cards.invent("Hasty Notes", "{R}", "Instant", "Draw a card.")

    private val hex = Cards.invent("Hex of the Moonhound", "{2}{G}", "Sorcery",
        "Create a Monster Role token attached to target creature you control. When you do, that creature fights up to one target creature you don't control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample. Creatures that fight each deal damage equal to their power to the other.)")
    private val ogre = Cards.invent("Glenbane Ogre", "{2}{B}{G}", "Creature — Troll", """
        When this creature enters, create a Monster Role token attached to it. (Enchanted creature gets +1/+1 and has trample.)
        {1}, Sacrifice an Aura attached to this creature: This creature fights target creature you don't control. If that creature would die this turn, exile it instead. Activate only as a sorcery.
    """, "4/4")
    private val thessaly = Cards.inventFaces("Adventure",
        face("Thessaly Stormstride", "{G}{U}{R}", "Legendary Creature — Giant Noble", "Trample\nPermanent spells you cast that have an Adventure cost {1} less to cast.", "4/4", null, null),
        face("Chase the Gale", "{2}{G}{U}{R}", "Instant — Adventure",
            "Mill seven cards. Then put all cards that have an Adventure from among the milled cards into your hand.", null, null, null))
    private val windfang = Cards.invent("Baying Windfang", "{2}{G}{G}", "Creature — Beast",
        "Vigilance\nThis creature has haste as long as you own a card in exile that has an Adventure.", "4/4")
    private val wyrm = Cards.inventFaces("Adventure",
        face("Gilded Wyrm", "{2}{R}{R}", "Creature — Dragon", "Flying, trample\nWhenever this creature attacks, create a Treasure token.", "4/4", null, null),
        face("Rich Appetite", "{2}{B}", "Instant — Adventure",
            "Exile the top two cards of target opponent's library face down. You may look at and play those cards for as long as they remain exiled.", null, null, null))
    private val moneylender = Cards.invent("Shrewd Moneylender", "{2}{W}", "Creature — Human Noble", """
        At the beginning of your upkeep, if an opponent controls more lands than you, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        {2}{W}: Choose another player. That player gains control of target Treasure you control. You draw a card.
    """, "2/3")
    private val falter = Cards.invent("Charm Falter", "{1}{U}", "Instant",
        "Counter target spell unless its controller pays {2} plus an additional {1} for each Faerie you control.")

    // CR 603.12: the reflexive trigger once the Monster Role is created, its target chosen then among every opponent's creatures (3 players);
    // CR 701.14a: the enchanted 3/3 deals 3 to Carol's 2/2 and takes 2
    @Test
    fun hexOfTheMoonhoundFightsAfterTheRoleIsCreated() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", boar)
        bob.battlefield(brute)
        carol.battlefield(yearling)
        alice.hand(hex)
        alice.casts(hex, target = boar)
        resolveAll()
        assertEquals(setOf(target(brute), target(yearling)), decision<Decision.Targets>().options.toSet())
        pickTargets(yearling)
        resolveAll()
        expect {
            yearling.zone shouldBe Zone.Graveyard
            boar.power shouldBe 3
            boar hasKeyword Keyword.Trample.kind
            card(boar).view.obj?.damage shouldBe 2
            brute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.14b: "up to one target creature" with none chosen: the Role stays, no fight
    @Test
    fun hexOfTheMoonhoundWithNoSecondTargetDoesntFight() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", boar)
        bob.battlefield(brute)
        alice.hand(hex)
        alice.casts(hex, target = boar)
        resolveAll()
        answer(Answer.Targets(emptyList()))
        resolveAll()
        expect {
            alice.battlefield has "Monster"
            card(boar).view.obj?.damage shouldBe 0
            card(brute).view.obj?.damage shouldBe 0
        }
    }

    // CR 111.10n; CR 118.3 (the Aura attached to it, sacrificed as a cost); CR 701.14a; CR 614.1a: Bob's creature, dealt lethal damage, is
    // exiled instead of dying
    @Test
    fun glenbaneOgreSacrificesItsRoleToFightAndExiles() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(brute)
        alice.hand(ogre)
        alice.casts(ogre, paying = listOf("Swamp", "Forest", "Forest", "Forest"))
        resolveAll()
        expect { ogre.power shouldBe 5 }
        alice.activates(ogre, target = brute)
        resolveAll()
        expect {
            alice.battlefield lacks "Monster"
            brute.zone shouldBe Zone.Exile
            ogre.power shouldBe 4
            card(ogre).view.obj?.damage shouldBe 3
        }
    }

    // CR 715.2a, 601.2f: a creature spell without an Adventure costs its full {2}{G}
    @Test
    fun thessalyDoesntReduceACreatureWithoutAnAdventure() = scenario {
        alice.battlefield(thessaly, "Forest", "Forest")
        alice.hand(courier, bramblekin)
        alice.casts(bramblekin)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 715.2a, 110.4b, 601.2f: the adventurer cast as a creature (a permanent spell that has an Adventure) costs {1} less
    @Test
    fun thessalyLetsTheAdventurerBeCastForLess() = scenario {
        alice.battlefield(thessaly, "Forest", "Forest")
        alice.hand(courier)
        alice.casts(courier)
        resolveAll()
        expect { courier.zone shouldBe Zone.Battlefield }
    }

    // CR 701.17a, 715.2a: of the seven milled cards, each that has an Adventure goes to the hand
    @Test
    fun chaseTheGalePutsTheAdventurersIntoTheHand() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Mountain", "Mountain")
        alice.hand(thessaly)
        alice.library(courier, "Forest", wyrm, "Island", bramblekin, "Swamp", "Plains", "Mountain")
        alice.casts("Chase the Gale", way = CastWay(face = 1))
        resolveAll()
        expect {
            alice.hand has courier
            alice.hand has wyrm
            alice.graveyard has bramblekin
            alice.graveyard.size shouldBe 5
            alice.library.size shouldBe 1
        }
    }

    // CR 715.2a: an adventurer card Alice owns in exile gives haste; a card without an Adventure doesn't
    @Test
    fun bayingWindfangHasHasteWithAnAdventurerInExile() = scenario {
        alice.battlefield(windfang)
        alice.exile(courier, bramblekin)
        expect { windfang hasKeyword Keyword.Haste.kind }
        move(courier, Zone.Graveyard)
        expect { windfang lacksKeyword Keyword.Haste.kind }
    }

    // CR 406.3: Bob's top two cards exiled face down; CR 406.3a, 305.1: Alice may play the land among them, turned face up as it's played
    @Test
    fun richAppetiteLetsAlicePlayTheOpponentsExiledCards() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(wyrm)
        bob.library("Island", notes, "Plains")
        alice.casts("Rich Appetite", target = "bob", way = CastWay(face = 1))
        resolveAll()
        expect {
            notes.zone shouldBe Zone.Exile
            bob.library.size shouldBe 1
            wyrm.zone shouldBe Zone.Exile
        }
        alice.plays("Island")
        expect {
            "Island".zone shouldBe Zone.Battlefield
            "Island".controller shouldBe alice.id
            "Island".types shouldBe listOf(arcanum.api.CardType.Land)
        }
    }

    // CR 108.4: another player chosen as it resolves (any of the 3+ players but Alice) gains control of the target Treasure; Alice draws
    @Test
    fun shrewdMoneylenderGivesATreasureToTheChosenPlayer() = scenario(players = 3) {
        alice.battlefield(moneylender, "Plains", "Plains", "Plains", trinket)
        alice.library("Island")
        alice.activates(moneylender, target = trinket, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        assertEquals(setOf(bob.id, carol.id), decision<Decision.ChoosePlayer>().options.toSet())
        choosePlayer(carol)
        expect {
            trinket.controller shouldBe carol.id
            alice.hand has "Island"
        }
    }

    // CR 118.12: Bob pays {2} plus {1} for each of Alice's two Faeries, {4}: his spell isn't countered
    @Test
    fun charmFalterIsPaidWithTheFaerieCount() = scenario {
        alice.battlefield(sprite, "Island", "Island")
        alice.battlefield("Thistle Sprite")
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.library("Plains")
        bob.hand(notes)
        alice.hand(falter)
        turn(priority = bob)
        bob.casts(notes, paying = listOf("Mountain"))
        bob.passes()
        alice.casts(falter, target = notes)
        resolve()
        payYes()
        expect {
            bob.hand has "Plains"
            bob.graveyard has notes
        }
    }

    // CR 118.12: with only three untapped lands Bob can't pay {4}: the spell is countered
    @Test
    fun charmFalterCountersWhenTheAmountCantBePaid() = scenario {
        alice.battlefield(sprite, "Island", "Island")
        alice.battlefield("Thistle Sprite")
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.library("Plains")
        bob.hand(notes)
        alice.hand(falter)
        turn(priority = bob)
        bob.casts(notes, paying = listOf("Mountain"))
        bob.passes()
        alice.casts(falter, target = notes)
        resolveAll()
        expect {
            bob.hand lacks "Plains"
            bob.graveyard has notes
        }
    }
}
