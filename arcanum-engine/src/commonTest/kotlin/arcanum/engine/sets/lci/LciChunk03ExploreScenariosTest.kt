package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** LCI chunk 03: explore (CR 701.44) played: Map tokens (CR 111.10s), exploring twice and X times, what was explored, an explore replaced, and
 * the cards around them. Invented names carry the oracle text. */
class LciChunk03ExploreScenariosTest {
    private val exploreReminder = "(Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this " +
        "creature, then put the card back or put it into your graveyard.)"
    private val mapReminder = "(It's an artifact with \"{1}, {T}, Sacrifice this token: Target creature you control explores. Activate only as a sorcery.\")"
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val siren = Cards.invent("Lookout Siren", "{U}", "Creature — Siren Pirate", "Flying\nWhen this creature enters, create a Map token. $mapReminder", "1/1")
    private val misstep = Cards.invent("Briny Misstep", "{1}{U}", "Instant", "Return target creature to its owner's hand. If it was tapped, create a Map token. $mapReminder")
    private val loseTheTrail = Cards.invent("Lose the Trail", "{1}{W}", "Instant", "Destroy target creature, enchantment, or planeswalker. Its controller creates two Map " +
        "tokens. (They're artifacts with \"{1}, {T}, Sacrifice this token: Target creature you control explores. Activate only as a sorcery.\")")
    private val fossilRouse = Cards.invent("Fossil Rouse", "{4}{B}", "Sorcery", "Return target creature card from your graveyard to the battlefield. That creature " +
        "explores, then it explores again. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that " +
        "creature, then put the card back or put it into your graveyard. Then repeat this process.)")
    private val grottoDelver = Cards.invent("Grotto Delver", "{2}{U}", "Creature — Merfolk Scout",
        "Whenever a creature you control explores, this creature gets +1/+0 until end of turn and can't be blocked this turn.", "2/4")
    private val jadeglow = Cards.invent("Jadeglow Delver", "{X}{G}", "Creature — Merfolk Scout", "When this creature enters, it explores X times. (To have it explore, " +
        "reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on that creature, then put the card back or " +
        "put it into your graveyard.)", "1/1")
    private val lifewarden = Cards.invent("Vesperine Lifewarden", "{W}{B}", "Legendary Creature — Vampire Scout", "Ward—Pay 3 life.\nWhenever you gain life, " +
        "Vesperine Lifewarden explores. Then destroy all other creatures if its power is exactly 20. (To have this creature explore, reveal the top card of your " +
        "library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)", "2/2")
    private val mimic = Cards.invent("Fathomdeep Mimic", "{2}{G}{U}", "Creature — Merfolk Spirit", "At the beginning of combat on your turn, this creature explores. " +
        "Then you may have it become a copy of another creature you control until end of turn. (To have this creature explore, reveal the top card of your library. " +
        "Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)", "4/4")
    private val nicoru = Cards.invent("Nicoru, Tide Pilot", "{G}{U}", "Legendary Creature — Merfolk Scout",
        "Whenever a creature you control explores a land card, you may put a land card from your hand onto the battlefield tapped.\n" +
            "Whenever a creature you control explores a nonland card, put a +1/+1 counter on Nicoru.", "2/3")
    private val sprout = Cards.invent("Sunseeker Sprout", "{G}", "Creature — Merfolk Scout", "{2}{G}: This creature explores. Activate only as a sorcery. $exploreReminder", "1/1")
    private val windingPaths = Cards.inventFaces("Transform",
        face("Winding Paths", "{G}", "Enchantment", "If a creature you control would explore, instead you scry 1, then that creature explores.\n" +
            "When this enchantment enters, target creature you control explores.\n" +
            "When a land you control enters, if you control seven or more lands, transform this enchantment.", null, null, null),
        face("Sporeling Grotto", null, "Land — Cave", "(Transforms from Winding Paths.)\n{T}: Add {G}.\n{3}{G}, {T}: Look at the top four cards of your library. You may " +
            "reveal a creature card from among them and put that card into your hand. Put the rest on the bottom of your library in a random order.", null, null, null))

    private val shambler = Cards.invent("Cavern Shambler", "{2}{G}", "Creature — Fungus", "", "2/2")
    private val slasher = Cards.invent("Bog Slasher", "{2}{B}", "Creature — Lizard Warrior", "", "3/3")
    private val grub = Cards.invent("Tunnel Grub", "{1}{G}", "Creature — Insect", "", "1/1")
    private val sip = Cards.invent("Sip of Dawnlight", "{W}", "Instant", "You gain 2 life.")

    // CR 111.10s, 701.44a: a Map token, sacrificed: the target creature explores; a land goes to your hand
    @Test
    fun aMapTokenMakesACreatureExplore() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(siren)
        alice.library("Forest", "Island")
        alice.casts(siren)
        playOut()
        alice.activates("Map Token", target = siren)
        playOut()
        expect {
            alice.battlefield lacks "Map Token"
            alice.hand has "Forest"
            siren.counters(p1p1) shouldBe 0
        }
    }

    // CR 608.2h: "if it was tapped" is the returned creature's last known information
    @Test
    fun aTappedCreatureReturnedMakesAMap() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(misstep)
        bob.battlefield(slasher, tapped = true)
        alice.casts(misstep, target = slasher)
        playOut()
        expect {
            bob.hand has slasher
            alice.battlefield has "Map Token"
        }
    }

    @Test
    fun anUntappedCreatureReturnedMakesNoMap() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(misstep)
        bob.battlefield(slasher)
        alice.casts(misstep, target = slasher)
        playOut()
        expect {
            bob.hand has slasher
            alice.battlefield lacks "Map Token"
        }
    }

    // CR 111.2: the destroyed permanent's controller creates the Maps, not the caster or another opponent
    @Test
    fun theDestroyedCreaturesControllerGetsTwoMaps() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        alice.hand(loseTheTrail)
        carol.battlefield(slasher)
        alice.casts(loseTheTrail, target = slasher)
        playOut()
        expect {
            carol.graveyard has slasher
            carol.battlefield.names.count { it == "Map Token" } shouldBe 2
            alice.battlefield lacks "Map Token"
            bob.battlefield lacks "Map Token"
        }
    }

    // CR 701.44a, b: two explores, each one done (Grotto Delver triggers twice); a nonland put into the graveyard, then a land to the hand
    @Test
    fun exploresThenExploresAgain() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", grottoDelver)
        alice.hand(fossilRouse)
        alice.graveyard(slasher)
        alice.library(shambler, "Forest", "Island")
        alice.casts(fossilRouse, target = slasher)
        playOut(may = true)
        expect {
            slasher.zone shouldBe Zone.Battlefield
            slasher.counters(p1p1) shouldBe 1
            alice.graveyard has shambler
            alice.hand has "Forest"
            grottoDelver.power shouldBe 4
        }
    }

    // CR 107.3m, 701.44a: X in its enters trigger is the X paid; it explores that many times
    @Test
    fun exploresXTimes() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(jadeglow)
        alice.library(shambler, grub, "Forest", "Island")
        alice.casts(jadeglow, x = 2)
        playOut(may = true)
        expect {
            jadeglow.counters(p1p1) shouldBe 2
            alice.graveyard has shambler
            alice.graveyard has grub
            alice.hand lacks "Forest"
        }
    }

    // CR 608.2h: its power after exploring; exactly 20 destroys every other creature
    @Test
    fun powerTwentyDestroysAllOtherCreatures() = scenario(players = 3) {
        alice.battlefield("Plains", grottoDelver)
        alice.battlefield(lifewarden, counters = listOf(p1p1 to 17))
        alice.hand(sip)
        alice.library(shambler, "Forest")
        bob.battlefield(slasher)
        carol.battlefield(grub)
        alice.casts(sip)
        playOut(may = false)
        expect {
            lifewarden.power shouldBe 20
            lifewarden.zone shouldBe Zone.Battlefield
            alice.graveyard has grottoDelver
            bob.graveyard has slasher
            carol.graveyard has grub
        }
    }

    @Test
    fun powerNotTwentyDestroysNothing() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(lifewarden, counters = listOf(p1p1 to 16))
        alice.hand(sip)
        alice.library(shambler, "Forest")
        bob.battlefield(slasher)
        alice.casts(sip)
        playOut(may = false)
        expect {
            lifewarden.power shouldBe 19
            bob.battlefield has slasher
        }
    }

    // CR 707.2, 613.2a: it becomes a copy of the chosen creature until end of turn (copiable values only; not targeted)
    @Test
    fun becomesACopyOfAnotherCreatureUntilEndOfTurn() = scenario {
        alice.battlefield(mimic, slasher)
        alice.library("Forest", "Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(slasher)
        expect {
            alice.hand has "Forest"
            mimic.power shouldBe 3
            mimic.view.characteristics!!.hasSubtype(Subtype("Lizard")) shouldBe true
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { mimic.power shouldBe 4 }
    }

    // CR 701.44a: "explores a land card": the revealed land; the land from hand enters tapped
    @Test
    fun exploringALandLetsALandFromHandEnterTapped() = scenario {
        alice.battlefield(nicoru, sprout, "Forest", "Forest", "Forest")
        alice.hand("Island")
        alice.library("Forest", "Swamp")
        alice.activates(sprout)
        playOut("Island")
        expect {
            alice.hand has "Forest"
            card("Island").zone shouldBe Zone.Battlefield
            card("Island").tapped shouldBe true
            nicoru.counters(p1p1) shouldBe 0
        }
    }

    // CR 701.44a: "explores a nonland card"
    @Test
    fun exploringANonlandCardGrowsNicoru() = scenario {
        alice.battlefield(nicoru, sprout, "Forest", "Forest", "Forest")
        alice.hand("Island")
        alice.library(shambler, "Swamp")
        alice.activates(sprout)
        playOut(may = false)
        expect {
            sprout.counters(p1p1) shouldBe 1
            nicoru.counters(p1p1) shouldBe 1
            alice.hand has "Island"
        }
    }

    // CR 614.1a: scry 1 first, then the explore; CR 614.5: the explore it brings isn't replaced again (one scry)
    @Test
    fun anExploreReplacedScriesFirst() = scenario {
        alice.battlefield(windingPaths, sprout, "Forest", "Forest", "Forest")
        alice.library(shambler, "Forest", "Island")
        alice.activates(sprout)
        playOut(shambler)
        expect {
            alice.hand has "Forest"
            sprout.counters(p1p1) shouldBe 0
            alice.library.names.last() shouldBe shambler
            alice.library.size shouldBe 2
        }
    }

    // CR 614.1a: the enters trigger's explore is replaced too
    @Test
    fun itsOwnEntersTriggerScriesThenExplores() = scenario {
        alice.battlefield(sprout, "Forest")
        alice.hand(windingPaths)
        alice.library(shambler, "Forest")
        alice.casts(windingPaths)
        playOut(shambler)
        expect {
            alice.hand has "Forest"
            sprout.counters(p1p1) shouldBe 0
        }
    }
}
