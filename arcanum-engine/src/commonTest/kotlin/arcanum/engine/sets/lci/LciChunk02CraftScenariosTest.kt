package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** LCI chunk 02: craft (CR 702.167) played: materials from the battlefield and the graveyard, "one or more" and several kinds, only as a
 * sorcery, and the back faces reading "the exiled cards used to craft it"; and the cards around them. Invented names carry the oracle text. */
class LciChunk02CraftScenariosTest {
    private fun reminder(cost: String, materials: String) =
        "($cost, Exile this artifact, Exile $materials: Return this card transformed under its owner's control. Craft only as a sorcery.)"
    private val anotherArtifact = "another artifact you control or an artifact card from your graveyard"

    private val skyMarker = Cards.inventFaces("Transform",
        face("Sky Temple Marker", "{W}", "Artifact", "When this artifact enters, scry 2.\nCraft with artifact {2}{W} ${reminder("{2}{W}", anotherArtifact)}", null, null, null),
        face("Sky Temple Floater", null, "Artifact Creature — Golem", "Flying\nWhenever this creature attacks, target attacking creature without flying gains flying until end of turn.", "1/4", null, null))
    private val seedgems = Cards.inventFaces("Transform",
        face("Verdant Seedgems", "{3}{G}", "Artifact", "When this artifact enters, distribute three +1/+1 counters among one, two, or three target creatures you control.\n" +
            "Craft with creature {5}{G}{G} ${reminder("{5}{G}{G}", "a creature you control or a creature card from your graveyard")}", null, null, null),
        face("Verdantheart Warden", null, "Artifact Creature — Golem", "When this creature enters, you gain life equal to the mana value of the exiled card used to craft it.", "7/7", null, null))
    private val frame = Cards.inventFaces("Transform",
        face("Tinkerer's Frame", "{1}{R}", "Artifact", "When this artifact enters, you may discard a card. If you do, draw two cards.\n" +
            "Craft with one or more Dinosaurs {4}{R} ${reminder("{4}{R}", "one or more Dinosaurs you control and/or Dinosaur cards from your graveyard")}", null, null, null),
        face("Masterwork Runner", null, "Artifact Creature — Dinosaur", "Masterwork Runner's power is equal to the total power of the exiled cards used to craft it.", "*/4", null, null))
    private val banner = Cards.inventFaces("Transform",
        face("Dawnbird Banner", "{3}", "Artifact", "{T}: Add one mana of any color.\n" +
            "Craft with one or more {5} ${reminder("{5}", "one or more other permanents you control and/or cards from your graveyard")}", null, null, null),
        face("Dawnbird Totem", null, "Artifact Creature — Bird Construct", "Flying, vigilance, haste\n" +
            "Dawnbird Totem's power and toughness are each equal to the number of colors among the exiled cards used to craft it.\n" +
            "{T}: For each color among the exiled cards used to craft this creature, add one mana of that color.", "*/*", null, null))
    private val mask = Cards.inventFaces("Transform",
        face("Mask of Terror", "{1}{B}", "Artifact", "When this artifact enters, target opponent reveals their hand. You choose an artifact or creature card from it. That player discards that card.\n" +
            "Craft with two creatures {5}{B} ${reminder("{5}{B}", "the two from among creatures you control and/or creature cards in your graveyard")}", null, null, null),
        face("Terror Bonebeast", null, "Creature — Dinosaur Skeleton Horror", "Menace\nWhenever this creature enters or attacks, you may mill two cards.", "5/4", null, null))
    private val seat = Cards.inventFaces("Transform",
        face("Seat of Bones", "{2}", "Legendary Artifact", "{T}: Mill two cards.\n" +
            "Craft with a Dinosaur, a Merfolk, a Pirate, and a Vampire {4} ${reminder("{4}", "the four from among permanents you control and/or cards in your graveyard")}", null, null, null),
        face("Marrow Regent", null, "Legendary Creature — Skeleton Spirit Pirate", "Menace, trample, lifelink, hexproof", "7/7", null, null))
    private val admiral = Cards.inventFaces("Transform",
        face("Seat of the Dread Admiral", "{2}", "Legendary Artifact", "{T}: Mill two cards.\n" +
            "Craft with a Dinosaur, a Merfolk, a Pirate, and a Vampire {4} ${reminder("{4}", "the four from among permanents you control and/or cards in your graveyard")}", null, null, null),
        face("The Dread Admiral", null, "Legendary Creature — Skeleton Spirit Pirate", "Menace, trample, lifelink, hexproof\n" +
            "Whenever The Dread Admiral attacks, each opponent sacrifices a nonland permanent of their choice. Then you may put an exiled creature card used to craft " +
            "The Dread Admiral onto the battlefield under your control tapped and attacking.", "7/7", null, null))
    private val riddleGem = Cards.inventFaces("Transform",
        face("The Riddle Gem", "{U}", "Legendary Artifact", "The Riddle Gem enters tapped.\n{T}: Add {C}{C}. Spend this mana only to activate abilities.\n" +
            "Craft with four or more nonlands with activated abilities {8}{U}", null, null, null),
        face("Nexus of Insight", null, "Legendary Artifact", "Nexus of Insight has each activated ability of the exiled cards used to craft it. You may activate each of those abilities only once each turn.\n" +
            "Whenever you activate an ability that isn't a mana ability, copy it. You may choose new targets for the copy.", null, null, null))
    private val charm = Cards.invent("Lifespring Charm", "{1}", "Artifact", "{1}: You gain 1 life.")
    private val snare = Cards.inventFaces("Transform",
        face("Woven Snare", "{2}{U}", "Artifact", "This artifact enters with three net counters on it.\n" +
            "{T}, Remove a net counter from this artifact: Tap another target nonland permanent. Its activated abilities can't be activated for as long as it remains tapped.\n" +
            "Craft with artifact {1}{U}", null, null, null),
        face("Woven Knot", null, "Artifact", "{3}{U}, {T}: Draw a card for each artifact you control, then put this artifact into its owner's library third from the top.", null, null, null))
    private val wallPlan = Cards.inventFaces("Transform",
        face("Artisan's Wall-Plan", "{3}{W}{U}", "Artifact", "When this artifact enters, create a 4/4 white and blue Golem artifact creature token.\n" +
            "Craft with artifact {4}{W}{W}{U} ${reminder("{4}{W}{W}{U}", anotherArtifact)}", null, null, null),
        face("Artisan's Workshop", null, "Artifact", "{T}: Create a 4/4 white and blue Golem artifact creature token. Activate only if this artifact or another artifact entered the battlefield under your control this turn.", null, null, null))
    private val chainwhip = Cards.inventFaces("Transform",
        face("Grim Chainwhip", "{R}", "Artifact — Equipment", "Equipped creature gets +2/+0.\nEquip {1}\nCraft with artifact {3}{R}{R} ${reminder("{3}{R}{R}", anotherArtifact)}", null, null, null),
        face("Grim Hand Cannon", null, "Artifact — Equipment", "Equipped creature gets +3/+0 and has \"Whenever this creature attacks, you may sacrifice an artifact other than Grim Hand Cannon. " +
            "When you do, this creature deals damage equal to its power to target creature.\"\nEquip {1}", null, null, null))

    private val trinket = Cards.invent("Tidecaller Trinket", "{U}", "Artifact", "")
    private val stomper = Cards.invent("Ridgeback Stomper", "{2}{R}", "Creature — Dinosaur", "", "3/3")
    private val runt = Cards.invent("Crestfang Runt", "{1}{G}", "Creature — Dinosaur", "", "2/2")
    private val lurker = Cards.invent("Brinefin Lurker", "{U}", "Creature — Merfolk", "", "1/1")
    private val corsair = Cards.invent("Gallows Corsair", "{B}", "Creature — Human Pirate", "", "1/1")
    private val courtier = Cards.invent("Duskfang Courtier", "{B}", "Creature — Vampire", "", "1/1")
    private val pumper = Cards.invent("Oakhide Pumper", "{1}{G}", "Creature — Elf", "{1}: This creature gets +1/+1 until end of turn.", "2/2")
    private val ogre = Cards.invent("Riverside Ogre", "{2}{R}", "Creature — Ogre", "", "4/4")

    // CR 702.167a: the cost exiles this artifact and another artifact you control; the card returns transformed under its owner's control
    @Test
    fun craftsWithAnArtifactYouControl() = scenario {
        alice.battlefield(skyMarker, trinket, "Plains", "Plains", "Plains")
        alice.activates(skyMarker, choosing = listOf(trinket))
        resolveAll()
        expect {
            "Sky Temple Floater".zone shouldBe Zone.Battlefield
            alice.exile has trinket
            "Sky Temple Floater".power shouldBe 1
        }
    }

    // CR 702.167b: "artifact" also means an artifact card in your graveyard
    @Test
    fun craftsWithAnArtifactCardFromYourGraveyard() = scenario {
        alice.battlefield(skyMarker, "Plains", "Plains", "Plains")
        alice.graveyard(trinket)
        alice.activates(skyMarker, choosing = listOf(trinket))
        resolveAll()
        expect {
            "Sky Temple Floater".power shouldBe 1
            alice.exile has trinket
        }
    }

    // CR 702.167a: "Activate only as a sorcery"
    @Test
    fun craftsOnlyAsASorcery() = scenario {
        alice.battlefield(skyMarker, trinket, "Plains", "Plains", "Plains")
        turn(number = 2, active = bob, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.activates(skyMarker, choosing = listOf(trinket)) }
    }

    // CR 702.167a: "two creatures": with only one there is no way to pay
    @Test
    fun twoCreaturesNeedTwo() = scenario {
        alice.battlefield(mask, stomper, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        assertFailsWith<IllegalArgumentException> { alice.activates(mask, choosing = listOf(stomper)) }
    }

    // CR 702.167c: the back face's enters trigger reads the mana value of the creature card exiled from the graveyard
    @Test
    fun wardenGainsTheExiledCardsManaValue() = scenario {
        alice.battlefield(seedgems, *Array(7) { "Forest" })
        alice.graveyard(stomper)
        alice.activates(seedgems, choosing = listOf(stomper))
        resolveAll()
        expect {
            "Verdantheart Warden".power shouldBe 7
            alice.life shouldBe 23
        }
    }

    // CR 702.167a, c; CR 604.3: one or more Dinosaurs from the battlefield and the graveyard; its power is their total power
    @Test
    fun runnersPowerIsTheExiledDinosaursTotalPower() = scenario {
        alice.battlefield(frame, stomper, *Array(5) { "Mountain" })
        alice.graveyard(runt)
        alice.activates(frame, choosing = listOf(stomper, runt))
        resolveAll()
        expect {
            alice.exile has stomper
            alice.exile has runt
            "Masterwork Runner".power shouldBe 5
            "Masterwork Runner".toughness shouldBe 4
        }
    }

    // CR 702.167c; CR 105.4: P/T and mana from the colors among the exiled cards (a red card, a blue card and a colorless land: two colors)
    @Test
    fun totemCountsTheColorsAmongTheExiledCards() = scenario {
        alice.battlefield(banner, *Array(5) { "Plains" }, "Island")
        alice.graveyard(stomper, trinket)
        alice.activates(banner, ability = 1, choosing = listOf(stomper, trinket, "Island"), paying = List(5) { "Plains" })
        resolveAll()
        expect {
            "Dawnbird Totem".power shouldBe 2
            "Dawnbird Totem".toughness shouldBe 2
        }
        alice.activates("Dawnbird Totem")
        expect { alice.pool.size shouldBe 2 }
    }

    // CR 702.167a: one Dinosaur, one Merfolk, one Pirate and one Vampire, each its own material, from both zones
    @Test
    fun craftsWithFourDifferentKinds() = scenario {
        alice.battlefield(seat, stomper, lurker, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(corsair, courtier)
        alice.activates(seat, ability = 1, choosing = listOf(stomper, lurker, corsair, courtier), paying = List(4) { "Swamp" })
        resolveAll()
        expect {
            "Marrow Regent".power shouldBe 7
            alice.exile.names.size shouldBe 4
        }
    }

    // CR 611.2b: its activated abilities can't be activated while it stays tapped; once it untaps, the effect is over
    @Test
    fun snaredPermanentsAbilitiesWaitUntilItUntaps() = scenario {
        alice.battlefield(snare, counters = listOf(CounterKind.Named("net") to 3))
        bob.battlefield(pumper, "Forest", "Forest")
        alice.library("Island", "Island")
        bob.library("Forest", "Forest")
        alice.activates(snare, target = pumper)
        resolveAll()
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates(pumper) }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.activates(pumper)
        resolveAll()
        expect { pumper.power shouldBe 3 }
    }

    // the back face's condition: the crafted Workshop itself entered under your control this turn
    @Test
    fun workshopMakesAGolemTheTurnItIsCrafted() = scenario {
        alice.battlefield(wallPlan, trinket, *Array(4) { "Plains" }, "Island", "Island", "Island")
        alice.activates(wallPlan, choosing = listOf(trinket))
        resolveAll()
        alice.activates("Artisan's Workshop")
        resolveAll()
        expect { alice.battlefield has "Golem Token" }
    }

    // the back face's condition: no artifact entered under your control this turn
    @Test
    fun workshopNeedsAnArtifactToHaveEntered() = scenario {
        alice.battlefield(wallPlan)
        backFaceUp(wallPlan)
        assertFailsWith<IllegalArgumentException> { alice.activates("Artisan's Workshop") }
    }

    // CR 113.10, 603.12: the granted attack trigger sacrifices another artifact, then deals the creature's power (2 + 3) to a creature
    @Test
    fun handCannonSacrificesAnotherArtifactToShoot() = scenario {
        alice.battlefield(chainwhip, runt, trinket)
        backFaceUp(chainwhip)
        attach(chainwhip, runt)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(runt)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect {
            alice.graveyard has trinket
            bob.graveyard has ogre
            "Grim Hand Cannon".zone shouldBe Zone.Battlefield
        }
    }

    // CR 702.167c, 508.4: each opponent sacrifices, then an exiled creature card used to craft it enters tapped and attacking (three players)
    @Test
    fun dreadAdmiralBringsBackACraftingCreature() = scenario(players = 3) {
        alice.battlefield(admiral, stomper, lurker, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(corsair, courtier)
        bob.battlefield(ogre)
        carol.battlefield(pumper)
        alice.library("Swamp", "Swamp", "Swamp")
        bob.library("Forest", "Forest")
        carol.library("Forest", "Forest")
        alice.activates(admiral, ability = 1, choosing = listOf(stomper, lurker, corsair, courtier), paying = List(4) { "Swamp" })
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 4)
        alice.attacks("The Dread Admiral", defender = "bob")
        resolveAll()
        alice.chooses(stomper)
        resolveAll()
        expect {
            bob.graveyard has ogre
            carol.graveyard has pumper
            stomper.zone shouldBe Zone.Battlefield
            stomper.tapped shouldBe true
        }
    }

    // layer 6 (CR 613.1f): the exiled cards' activated abilities, each once a turn (CR 602.5b); each non-mana activation is copied (CR 707.10)
    @Test
    fun nexusBorrowsAbilitiesOnceEachAndCopiesThem() = scenario {
        alice.battlefield(riddleGem, charm, charm, *Array(11) { "Island" })
        alice.graveyard(charm, charm)
        alice.activates(riddleGem, ability = 1, choosing = listOf(charm, charm, charm, charm), paying = List(9) { "Island" })
        resolveAll()
        alice.activates("Nexus of Insight", ability = 0, paying = listOf("Island#9"))
        resolveAll()
        expect { alice.life shouldBe 22 }
        assertFailsWith<IllegalArgumentException> { alice.activates("Nexus of Insight", ability = 0, paying = listOf("Island#10")) }
        alice.activates("Nexus of Insight", ability = 1, paying = listOf("Island#10"))
        resolveAll()
        expect { alice.life shouldBe 24 }
    }
}
