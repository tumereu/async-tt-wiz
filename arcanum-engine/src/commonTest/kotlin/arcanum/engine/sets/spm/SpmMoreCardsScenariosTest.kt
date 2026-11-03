package arcanum.engine.sets.spm

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** More Marvel's Spider-Man cards in play (red, green, multicoloured, artifacts and lands): suspect, damage to a creature and its
 * controller, a creature hurting itself, spells that can't be countered, a cost reduction for a Spider target, a 4/4 Lizard without
 * abilities, animals pumped, a land returned with this card, two creatures' damage to one target, an exchange of life totals that
 * draws, a fight's excess damage, cards exiled and returned when it dies, an artifact made a 3/3, a Spider destroyed by its damage, a
 * land of the chosen basic land type. */
class SpmMoreCardsScenariosTest {
    private val editor = Cards.invent("Editor Barnaby Stroud", "{2}{R}", "Legendary Creature — Human Citizen", """
        When Editor Barnaby Stroud enters, suspect up to one target creature. (A suspected creature has menace and can't block.)
        Whenever a creature you control with menace attacks, create a Treasure token.
    """, "2/2")
    private val tremorfist = Cards.invent("Tremorfist, Unyielding", "{4}{R}{R}", "Legendary Creature — Human Rogue Villain", """
        During your turn, Tremorfist has first strike.
        Vibro-Shock Gauntlets — When Tremorfist enters, he deals 2 damage to target creature and 2 damage to that creature's controller.
    """, "5/5")
    private val retort = Cards.invent("Snappy Retort", "{2}{R}", "Instant",
        "Target creature deals damage equal to its power to itself. If that creature is attacking, Snappy Retort deals 2 damage to that creature's controller.")
    private val anarchy = Cards.invent("Anarchy Webslinger", "{1}{R}", "Legendary Creature — Spider Human Hero", """
        Riot (This creature enters with your choice of a +1/+1 counter or haste.)
        Other Spiders you control have riot.
        Spells and abilities can't be countered.
        Damage can't be prevented.
    """, "2/1")
    private val saurox = Cards.invent("Saurox the Reptile Lord", "{4}{R}", "Legendary Creature — Dinosaur Villain", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Dinosaur Formula — {1}{R}, Discard this card: Until end of turn, target creature you control gets +3/+1 and becomes a Dinosaur in addition to its other types.
    """, "5/4")
    private val sprout = Cards.invent("Sprout More Limbs", "{1}{G}", "Instant", """
        This spell costs {1} less to cast if it targets a Spider.
        Target creature gets +4/+4 until end of turn.
    """)
    private val saurian = Cards.invent("Saurian, Doctor's Blight", "{2}{G}{G}", "Legendary Creature — Lizard Villain", """
        Trample
        Lizard Formula — When Saurian, Doctor's Blight enters, up to one other target creature loses all abilities and becomes a green Lizard creature with base power and toughness 4/4.
    """, "5/5")
    private val webhog = Cards.invent("Hamster Webhog, Snout of Justice", "{1}{G}", "Legendary Creature — Spider Boar Hero", """
        When Hamster Webhog enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Animal May-Ham — Other Spiders, Boars, Bats, Bears, Birds, Cats, Dogs, Frogs, Jackals, Lizards, Mice, Otters, Rabbits, Raccoons, Rats, Squirrels, Turtles, and Wolves you control get +1/+1.
    """, "2/2")
    private val dunewalker = Cards.invent("Dunewalker, Drifting Rogue", "{1}{G}{G}", "Legendary Creature — Sand Elemental Villain", """
        Dunewalker's power and toughness are each equal to the number of lands you control.
        Dunewalker can't be blocked by creatures with power 2 or less.
        {3}{G}{G}: Return this card and target land card from your graveyard to the battlefield tapped.
    """, "*/*")
    private val partnership = Cards.invent("Splendid Partnership", "{3}{G}", "Instant", """
        This spell costs {2} less to cast if you control a permanent with mana value 4 or greater.
        One or two target creatures you control each get +1/+0 until end of turn. They each deal damage equal to their power to target creature an opponent controls.
    """)
    private val inverso = Cards.invent("Monsieur Inverso", "{5}{W}{B}", "Legendary Creature — Human Villain", """
        Vigilance, lifelink
        Darkforce Inversion — When Monsieur Inverso enters, you may exchange life totals with target opponent. If you lost life this way, draw that many cards.
    """, "5/5")
    private val stampede = Cards.invent("Rhinox's Stampede", "{R/G}", "Sorcery",
        "Target creature you control gets +1/+0 until end of turn. It fights target creature an opponent controls. When excess damage is dealt to the creature an opponent controls this way, destroy up to one target noncreature artifact with mana value 3 or less.")
    private val blot = Cards.invent("The Blot, Walking Gateway", "{3}{W}{B}", "Legendary Creature — Human Scientist Villain", """
        When The Blot enters, exile up to one target nonland permanent and up to one target nonland permanent card from a graveyard.
        When The Blot dies, put him on the bottom of his owner's library. If you do, return the exiled cards to their owners' hands.
    """, "4/4")
    private val cogito = Cards.invent("Cogito, Thinking Engine", "{4}", "Legendary Artifact Creature — Robot Villain",
        "At the beginning of combat on your turn, target non-Equipment artifact you control becomes an artifact creature with base power and toughness 3/3 until end of turn. Untap it.", "3/3")
    private val arachnicide = Cards.invent("Arachnicide, Spite Engine", "{2}", "Legendary Artifact Creature — Human Villain", """
        Whenever Arachnicide deals damage to a Spider, destroy that creature.
        {6}, Exile this card from your graveyard: Create two tapped 1/1 colorless Robot artifact creature tokens with flying.
    """, "2/1")
    private val archway = Cards.invent("Crossworld Archway", null, "Land", """
        As this land enters, choose a basic land type. Then you may pay 2 life. If you don't, it enters tapped.
        This land is the chosen type.
    """)
    private val krovak = Cards.invent("Krovak the Tracker", "{1}{B}{G}", "Legendary Creature — Human Warrior Villain", """
        Trample
        Whenever a creature an opponent controls with the greatest power among creatures that player controls dies, draw a card and put a +1/+1 counter on Krovak the Tracker.
    """, "4/3")
    private val apex = Cards.invent("Hunter Krovak, Apex Stalker", "{1}{R}{G}", "Legendary Creature — Human Warrior Villain", """
        Vigilance
        Top of the Food Chain — Hunter Krovak's power is equal to the greatest mana value among permanents you control.
    """, "*/4")
    private val screech = Cards.invent("Screech, Discord Maker", "{2}{B/R}", "Legendary Creature — Mutant Villain", """
        At the beginning of your first main phase, you may discard a card. When you do, target creature can't block this turn.
        Sonic Blast — Whenever a creature an opponent controls dies, Screech deals 1 damage to that player.
    """, "2/3")
    private val pact = Cards.invent("Parasite Pact", "{1}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1, has menace, and is a Symbiote in addition to its other types.
        You may cast this card from your graveyard by discarding a card in addition to paying its other costs.
    """)
    private val varrik = Cards.invent("Varrik, Thousand Faces", "{3}{U}", "Legendary Creature — Human Shapeshifter Villain", """
        You may have Varrik enter as a copy of a creature you control, except his name is Varrik, Thousand Faces.
        Mayhem {2}{U} (You may cast this card from your graveyard for {2}{U} if you discarded it this turn. Timing rules still apply.)
    """, "2/3")
    private val knock = Cards.invent("Landlord's Knock", "{W}", "Enchantment",
        "At the beginning of your end step, you may tap two untapped creatures and/or Treasures you control. If you do, draw a card. Otherwise, sacrifice this enchantment.")
    private val limbs = Cards.invent("Grapple's Steel Limbs", "{1}", "Artifact — Equipment", """
        Whenever a creature you control with mana value 5 or greater enters, you may attach this Equipment to it.
        Equipped creature gets +4/+4.
        Equip {5}
    """)
    private val barb = Cards.invent("Stinger's Barb", "{1}{B}", "Instant", "Target creature gets -3/-3 until end of turn.")
    private val destroy = Cards.invent("Sudden End", "{1}{B}", "Instant", "Destroy target creature.")
    private val counterspell = Cards.invent("Hard Refusal", "{U}{U}", "Instant", "Counter target spell.")
    private val jolt = Cards.invent("Static Jolt", "{R}", "Instant", "Static Jolt deals 2 damage to any target.")
    private val trinket = Cards.invent("Webbed Trinket", "{2}", "Artifact", "")
    private val bear = spmBear
    private val ogre = spmOgre
    private val giant = spmGiant
    private val spider = spmSpider

    /** A suspected creature has menace and can't block; its attack makes a Treasure. */
    // CR 701.60c
    @Test
    fun suspectGivesMenace() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(editor)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        alice.casts(editor)
        drive({ d: Decision -> d is Decision.Targets } to { targets(bear) })
        expect { bear hasKeyword Keyword.Menace }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(bear)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    /** 2 damage to the creature and 2 to its controller. */
    // CR 120.3
    @Test
    fun damageToCreatureAndController() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(tremorfist)
        bob.battlefield(bear)
        alice.casts(tremorfist)
        drive({ d: Decision -> d is Decision.Targets } to { targets(bear) })
        expect { bear.zone shouldBe Zone.Graveyard }
        assertEquals(18, bob.life)
    }

    /** An attacking creature hurts itself and its controller. */
    // CR 120.3, 506.4
    @Test
    fun attackerHurtsItself() = scenario {
        bob.battlefield(ogre)
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(retort)
        bob.library(bear, bear)
        alice.library(bear, bear)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(retort, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
        assertEquals(18, bob.life)
    }

    /** Spells can't be countered while it is around. */
    // CR 701.6
    @Test
    fun spellsCantBeCountered() = scenario {
        alice.battlefield(anarchy, "Mountain")
        alice.hand(jolt)
        bob.battlefield("Island", "Island")
        bob.hand(counterspell)
        alice.casts(jolt, target = "bob")
        alice.passes()
        bob.casts(counterspell, target = jolt)
        resolveAll()
        assertEquals(18, bob.life)
    }

    /** Discarded from the hand: +3/+1 and a Dinosaur until end of turn. */
    // CR 602.2, 205.1b
    @Test
    fun formulaFromTheHand() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(saurox)
        alice.activates(saurox, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.subtypes shouldBe listOf(Subtype("Bear"), Subtype("Dinosaur"))
            alice.graveyard has saurox
        }
    }

    /** Targeting a Spider, it costs {G}. */
    // CR 601.2f
    @Test
    fun cheaperForASpider() = scenario {
        alice.battlefield("Forest", spider)
        alice.hand(sprout)
        alice.casts(sprout, target = spider, paying = listOf("Forest"))
        resolveAll()
        expect { spider.power shouldBe 6 }
    }

    /** A 4/4 green Lizard with no abilities, for good. */
    // CR 613.1d-f, 613.4b
    @Test
    fun becomesAPlainLizard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(saurian)
        bob.battlefield(editor)
        alice.casts(saurian)
        drive({ d: Decision -> d is Decision.Targets } to { targets(editor) })
        expect {
            editor.power shouldBe 4
            editor.colors shouldBe listOf(Color.Green)
            editor.subtypes shouldBe listOf(Subtype("Lizard"))
        }
    }

    /** Other animals and Spiders get +1/+1; the Ogre doesn't. */
    // CR 613.4c
    @Test
    fun animalsPumped() = scenario {
        alice.battlefield(webhog, bear, spider, ogre)
        expect {
            bear.power shouldBe 3
            spider.power shouldBe 3
            ogre.power shouldBe 3
            webhog.power shouldBe 2
        }
    }

    /** From the graveyard it returns with a land card, both tapped. */
    // CR 113.6m
    @Test
    fun returnsWithALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(dunewalker, "Mountain")
        alice.activates(dunewalker, target = "Mountain")
        resolveAll()
        expect {
            dunewalker.zone shouldBe Zone.Battlefield
            dunewalker.tapped shouldBe true
            dunewalker.power shouldBe 6
        }
    }

    /** Two creatures pumped, each dealing damage equal to its power to one creature. */
    // CR 120.3, 608.2c
    @Test
    fun twoCreaturesDamageOne() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear, spider)
        alice.hand(partnership)
        bob.battlefield(giant)
        alice.casts(partnership, targets = listOf(bear, spider, giant))
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    /** Exchanged life totals: the life lost is drawn as cards. */
    // CR 701.12c
    @Test
    fun exchangeLifeAndDraw() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(inverso)
        alice.library(bear, bear, bear, bear)
        bob.life = 17
        alice.casts(inverso)
        drive({ d: Decision -> d is Decision.Targets } to { targets("bob") }, { d: Decision -> d is Decision.May } to { yes() })
        assertEquals(17, alice.life)
        assertEquals(20, bob.life)
        assertEquals(3, alice.hand.size)
    }

    /** Excess damage from the fight destroys a small noncreature artifact. */
    // CR 701.14, 120.4a, 603.12
    @Test
    fun fightExcessDestroysAnArtifact() = scenario {
        alice.battlefield("Forest", giant)
        alice.hand(stampede)
        bob.battlefield(bear, trinket)
        alice.casts(stampede, targets = listOf(giant, bear))
        drive({ d: Decision -> d is Decision.Targets } to { targets(trinket) })
        expect {
            bear.zone shouldBe Zone.Graveyard
            trinket.zone shouldBe Zone.Graveyard
        }
    }

    /** Without excess damage nothing more happens. */
    // CR 120.4a
    @Test
    fun fightWithoutExcess() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(stampede)
        bob.battlefield(ogre, trinket)
        alice.casts(stampede, targets = listOf(bear, ogre))
        resolveAll()
        assertEquals(null, pending)
        expect { trinket.zone shouldBe Zone.Battlefield }
    }

    /** It exiles a permanent and a graveyard card; dying, it goes to the bottom and they return to their owners' hands. */
    // CR 607.2a, 603.10a
    @Test
    fun exiledCardsReturnWhenItDies() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(blot, destroy)
        bob.battlefield(trinket)
        bob.graveyard(ogre)
        alice.casts(blot, paying = listOf("Plains", "Plains", "Plains", "Swamp", "Swamp"))
        drive({ d: Decision -> d is Decision.Targets && (d as Decision.Targets).slot == "permanent" } to { targets(trinket) },
            { d: Decision -> d is Decision.Targets } to { targets(ogre) })
        expect {
            bob.exile has trinket
            bob.exile has ogre
        }
        alice.casts(destroy, target = blot, paying = listOf("Swamp#2", "Swamp#3"))
        resolveAll()
        // the returned cards are in bob's hand, hidden from alice (D60)
        assertEquals(2, state.player(bob.id).hand.size)
        assertTrue(state.exile.isEmpty())
        assertEquals(1, state.player(alice.id).library.size)
    }

    /** At the beginning of combat an artifact becomes an untapped 3/3 artifact creature. */
    // CR 205.1b, 613.4b
    @Test
    fun artifactBecomesACreature() = scenario {
        alice.battlefield(cogito)
        alice.battlefield(trinket, tapped = true)
        alice.library(bear, bear)
        bob.library(bear, bear)
        turn(number = 3)
        allPass()
        drive({ d: Decision -> d is Decision.Targets } to { targets(trinket) })
        expect {
            trinket.power shouldBe 3
            trinket.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            trinket.tapped shouldBe false
        }
    }

    /** Its damage to a Spider destroys it. */
    // CR 510.2
    @Test
    fun damageToASpiderDestroys() = scenario {
        alice.battlefield(arachnicide)
        bob.battlefield(bigSpider)
        alice.library(bear, bear)
        bob.library(bear, bear)
        turn(number = 3)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(arachnicide)
        allPass()
        bob.blocks(bigSpider to arachnicide)
        untilStep(TurnStep.PostcombatMain)
        expect { bigSpider.zone shouldBe Zone.Graveyard }
    }

    private val bigSpider = Cards.invent("Hulking Web Spider", "{4}{G}", "Creature — Spider", "", "5/5")

    /** The chosen basic land type gives its mana; paying 2 life it enters untapped. */
    // CR 305.6, 614.12
    @Test
    fun landOfTheChosenType() = scenario {
        alice.hand(archway)
        alice.plays(archway)
        drive(
            { d: Decision -> d is Decision.ChooseSubtype } to { answer(Answer.SubtypeChoice(Subtype("Island"))) },
            { d: Decision -> d is Decision.May } to { yes() },
        )
        expect {
            archway.tapped shouldBe false
            archway.subtypes shouldBe listOf(Subtype("Island"))
        }
        assertEquals(18, alice.life)
    }

    /** The opponent's biggest creature dying draws a card and puts a counter. */
    // CR 603.10a
    @Test
    fun biggestCreatureDies() = scenario {
        alice.battlefield(krovak, "Swamp", "Swamp")
        alice.hand(destroy)
        alice.library(bear)
        bob.battlefield(ogre, bear)
        alice.casts(destroy, target = ogre)
        resolveAll()
        expect {
            krovak.counters(p1p1) shouldBe 1
            alice.hand has bear
        }
    }

    /** Its power is the greatest mana value among your permanents. */
    // CR 604.3
    @Test
    fun powerFromManaValue() = scenario {
        alice.battlefield(apex, giant)
        expect { apex.power shouldBe 5 }
    }

    /** A discard makes a creature unable to block; an opponent's creature dying deals 1 damage to that player. */
    // CR 603.12, 509.1a
    @Test
    fun discardStopsABlocker() = scenario {
        alice.battlefield(screech, "Swamp", "Swamp")
        alice.hand(bear, barb)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        bob.battlefield(ogre)
        turn(number = 3, step = TurnStep.Draw)
        allPass()
        drive(
            { d: Decision -> d is Decision.May } to { yes() },
            { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(bear) },
            { d: Decision -> d is Decision.Targets } to { targets(ogre) },
        )
        expect { ogre hasMechanic { it is Mechanic.CantBlock } }
        alice.casts(barb, target = ogre)
        resolveAll()
        assertEquals(19, bob.life)
    }

    /** The Aura is cast from the graveyard by also discarding a card. */
    // CR 601.2f
    @Test
    fun auraFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.graveyard(pact)
        alice.hand(ogre)
        alice.casts(pact, target = bear, choosing = listOf(ogre))
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Menace
            alice.graveyard has ogre
        }
    }

    /** It enters as a copy of a creature you control, under its own name. */
    // CR 707.9b
    @Test
    fun copyUnderItsOwnName() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", giant)
        alice.hand(varrik)
        alice.casts(varrik)
        drive({ d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(giant) })
        val id = card(varrik, zone = Zone.Battlefield)
        assertEquals(5, view()[id]!!.characteristics!!.power)
        assertEquals(varrik, view()[id]!!.characteristics!!.name)
    }

    /** At the end step: tap two creatures to draw, or sacrifice it. */
    // CR 118.12
    @Test
    fun tapTwoOrSacrifice() = scenario {
        alice.battlefield(knock, bear, ogre)
        alice.library(giant)
        advanceTo(TurnStep.End)
        drive({ d: Decision -> d is Decision.May } to { yes(false) })
        expect { alice.graveyard has knock }
    }

    /** A big creature entering may take the Equipment. */
    // CR 701.3a
    @Test
    fun equipmentJumpsOnABigCreature() = scenario {
        alice.battlefield(limbs, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(giant)
        alice.casts(giant)
        drive({ d: Decision -> d is Decision.May } to { yes() })
        expect { giant.power shouldBe 9 }
    }
}
