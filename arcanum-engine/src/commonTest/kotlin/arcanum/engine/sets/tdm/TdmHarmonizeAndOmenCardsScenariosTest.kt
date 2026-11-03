package arcanum.engine.sets.tdm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tarkir: Dragonstorm cards of parser chunks 07–08 in play: harmonize (CR 702.180) and the Omen cards (CR 720), both halves of each.
 */
class TdmHarmonizeAndOmenCardsScenariosTest {
    private val focusedWyrmfire = Cards.invent("Focused Wyrmfire", "{R}", "Sorcery", """
        Focused Wyrmfire deals 2 damage to any target.
        Harmonize {5}{R}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val frostboundWyrmhunt = Cards.invent("Frostbound Wyrmhunt", "{U}{R}", "Sorcery", """
        Draw a card, then you may discard a card. When you discard a nonland card this way, Frostbound Wyrmhunt deals 3 damage to target creature.
        Harmonize {4}{U}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val mastodonBellow = Cards.invent("Mastodon Bellow", "{2}{G}{U}{R}", "Sorcery", """
        Create a 5/5 green Elephant creature token.
        Harmonize {5}{G}{U}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val wildCadence = Cards.invent("Wild Cadence", "{X}{G}{G}", "Sorcery", """
        Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle.
        Harmonize {X}{G}{G}{G}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by an amount of generic mana equal to its power. Then exile this spell.)
    """)
    private val wanderersHabit = Cards.invent("Wanderer's Habit", "{2}{G}", "Sorcery", """
        Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        Harmonize {4}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val hymnweaverMage = Cards.invent("Hymnweaver Mage", "{G}{U}{R}", "Creature — Human Bard", """
        Flash
        When this creature enters, target instant or sorcery card in your graveyard gains harmonize until end of turn. Its harmonize cost is equal to its mana cost. (You may cast that card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile the spell.)
    """, "3/2")
    private val unisonCharge = Cards.invent("Unison Charge", "{1}{G}", "Sorcery", """
        Distribute two +1/+1 counters among one or two target creatures you control. Creatures you control with counters on them gain vigilance and trample until end of turn.
        Harmonize {4}{G} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val endlessMurmur = Cards.invent("Endless Murmur", "{U}", "Sorcery", """
        Draw a card.
        Harmonize {5}{U} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val sorrelsRebuke = Cards.invent("Sorrel's Rebuke", "{1}{U}", "Sorcery", """
        Return target creature to its owner's hand.
        Harmonize {5}{U} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val recklessGallop = Cards.invent("Reckless Gallop", "{R}", "Sorcery", """
        Target creature gets +3/+0 and gains haste until end of turn.
        Harmonize {4}{R} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val longnightTales = Cards.invent("Longnight Tales", "{2}{U}", "Sorcery", """
        Draw three cards. Then discard two cards unless you discard a creature card.
        Harmonize {4}{U} (You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {X}, where X is its power. Then exile this spell.)
    """)
    private val thornbloomRegent = Cards.inventFaces("Omen",
        face("Thornbloom Regent", "{3}{G}{G}", "Creature — Dragon", """
        Flying
        Whenever this creature or another Dragon you control enters, you gain 3 life.
    """, "4/5", null, null),
        face("Stake the Wilds", "{2}{G}", "Sorcery — Omen", "Search your library for up to two basic Forest cards, reveal them, put one onto the battlefield tapped and the other into your hand, then shuffle. (Also shuffle this card.)", null, null, null),
    )
    private val shoalIsleDragon = Cards.inventFaces("Omen",
        face("Shoal Isle Dragon", "{5}{U}", "Creature — Dragon", """
        Flying
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
    """, "4/4", null, null),
        face("Glancing Strike", "{1}{U}", "Instant — Omen", "Tap up to one target creature. Draw a card. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val meddlingStormbrood = Cards.inventFaces("Omen",
        face("Meddling Stormbrood", "{4}{G}", "Creature — Dragon", """
        Flying
        When this creature enters, destroy up to one target artifact or enchantment.
    """, "3/3", null, null),
        face("Spiteful Payback", "{1}{B}", "Sorcery — Omen", "Destroy target creature with power 3 or less. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val savageGravegorger = Cards.inventFaces("Omen",
        face("Savage Gravegorger", "{5}{B}", "Creature — Dragon", """
        Flying, deathtouch
        When this creature enters, exile up to two target cards from a single graveyard.
    """, "3/5", null, null),
        face("Gloam Sight", "{1}{B}", "Sorcery — Omen", "Put a +1/+1 counter on up to one target creature. Draw a card. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val deltaRiverRegent = Cards.inventFaces("Omen",
        face("Delta River Regent", "{4}{U}{U}", "Creature — Dragon", """
        Flying
        When this creature enters, return up to two other target nonland permanents to their owners' hands.
    """, "6/7", null, null),
        face("Wrap and Snare", "{3}{U}", "Instant — Omen", "Draw three cards, then discard a card. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val cleansingStormbrood = Cards.inventFaces("Omen",
        face("Cleansing Stormbrood", "{4}{B}", "Creature — Dragon", """
        Flying
        Ward—Pay 2 life.
        When this creature enters, remove all counters from up to one target creature.
    """, "4/4", null, null),
        face("Drink Vigor", "{1}{W}", "Instant — Omen", "Target creature gets +2/+2 and gains lifelink and hexproof until end of turn. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val dawnrilerWyrm = Cards.inventFaces("Omen",
        face("Dawnriler Wyrm", "{4}{W}", "Creature — Dragon", """
        Flying, vigilance
        At the beginning of combat on your turn, another target creature you control gets +1/+0 until end of turn.
    """, "3/4", null, null),
        face("Muster Roar", "{1}{W}", "Sorcery — Omen", "Create a 2/2 white Soldier creature token. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val glyphscaleStormbrood = Cards.inventFaces("Omen",
        face("Glyphscale Stormbrood", "{3}{R}", "Creature — Dragon", """
        Flying
        Whenever you cast a noncreature spell or a Dragon spell, this creature gets +2/+0 until end of turn.
    """, "2/4", null, null),
        face("Freezing Shriek", "{1}{U}", "Instant — Omen", "Counter target spell with mana value 2 or less. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val jungleWildling = Cards.inventFaces("Omen",
        face("Jungle Wildling", "{4}{G}", "Creature — Dragon", """
        Flying
        When this creature enters, you gain 3 life.
    """, "3/3", null, null),
        face("Nest Seek", "{G}", "Sorcery — Omen", "Search your library for a basic land card, reveal it, put it into your hand, then shuffle. (Also shuffle this card.)", null, null, null),
    )
    private val carrionRegent = Cards.inventFaces("Omen",
        face("Carrion Regent", "{3}{B}", "Creature — Dragon", """
        Flying
        Ward—Discard a card.
    """, "4/4", null, null),
        face("Seep Venom", "{X}{B}{B}", "Sorcery — Omen", "Each non-Dragon creature gets -X/-X until end of turn. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val galeshriekFeral = Cards.inventFaces("Omen",
        face("Galeshriek Feral", "{4}{R}", "Creature — Dragon", """
        Flying, haste
        {1}{R}: This creature gets +1/+0 until end of turn.
    """, "3/3", null, null),
        face("Roust Out", "{1}{R}", "Sorcery — Omen", "Discard a card. If you do, draw two cards. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val doublemawStormbrood = Cards.inventFaces("Omen",
        face("Doublemaw Stormbrood", "{5}{W}", "Creature — Dragon", """
        Flying
        When this creature enters, you gain 5 life.
    """, "5/4", null, null),
        face("Scorching Bite", "{1}{R}", "Sorcery — Omen", "Scorching Bite deals 5 damage to target creature without flying. (Then shuffle this card into its owner's library.)", null, null, null),
    )
    private val spinwingStormbrood = Cards.inventFaces("Omen",
        face("Spinwing Stormbrood", "{4}{U}", "Creature — Dragon", """
        Flash
        Flying
        You may cast sorcery spells and Dragon spells as though they had flash.
    """, "4/3", null, null),
        face("Soaring Surge", "{2}{G}", "Sorcery — Omen", "Put three +1/+1 counters on target creature you control. (Then shuffle this card into its owner's library.)", null, null, null),
    )

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1

    // ---- Chunk 07: harmonize

    /** Cast from the graveyard for its harmonize cost, a 5-power creature tapped to pay {5}; then exiled. */
    // CR 702.180a, b
    @Test
    fun endlessMurmurHarmonized() = scenario {
        alice.battlefield("Island", giant)
        alice.graveyard(endlessMurmur)
        alice.library(ogre)
        alice.casts(endlessMurmur, choosing = listOf(giant))
        playOut()
        expect {
            alice.hand has ogre
            alice.exile has endlessMurmur
            giant.tapped shouldBe true
        }
    }

    /** Cast normally: a creature back to its owner's hand. */
    @Test
    fun sorrelsRebukeBounces() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(sorrelsRebuke)
        bob.battlefield(giant)
        alice.casts(sorrelsRebuke, target = giant)
        playOut()
        expect { bob.hand has giant }
    }

    /** Three cards, then two discarded unless a creature card is. */
    // CR 118.12
    @Test
    fun longnightTalesDiscardsACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(longnightTales)
        alice.library(ogre, Plain.study, Plain.heal)
        alice.casts(longnightTales)
        playOut(ogre)
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun longnightTalesDiscardsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(longnightTales)
        alice.library(Plain.shock, Plain.study, Plain.heal)
        alice.casts(longnightTales)
        playOut(Plain.shock, Plain.study, may = false)
        expect { alice.hand.size shouldBe 1 }
    }

    /** 2 damage; harmonized later with a 3-power creature tapped. */
    // CR 702.180a
    @Test
    fun focusedWyrmfireBurnsAgain() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", ogre)
        alice.graveyard(focusedWyrmfire)
        alice.casts(focusedWyrmfire, target = "bob", choosing = listOf(ogre))
        playOut()
        expect {
            bob.life shouldBe 18
            alice.exile has focusedWyrmfire
        }
    }

    /** Draw, discard a nonland card: 3 damage to a creature. */
    // CR 603.12
    @Test
    fun frostboundWyrmhuntDiscardsToBurn() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(frostboundWyrmhunt, bear)
        alice.library(giant)
        bob.battlefield(ogre)
        alice.casts(frostboundWyrmhunt)
        playOut(bear, ogre)
        expect { bob.graveyard has ogre }
    }

    /** A 5/5 Elephant. */
    @Test
    fun mastodonBellowMakesAnElephant() = scenario {
        alice.battlefield("Forest", "Island", "Mountain", "Forest", "Forest")
        alice.hand(mastodonBellow)
        alice.casts(mastodonBellow)
        playOut()
        expect { "Elephant Token".power shouldBe 5 }
    }

    /** X = 3: a creature card of mana value 3 or less onto the battlefield. */
    // CR 701.23a
    @Test
    fun wildCadenceFetchesACreature() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(wildCadence)
        alice.library(giant, ogre)
        alice.casts(wildCadence, x = 3)
        playOut(ogre)
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** A basic land onto the battlefield tapped. */
    // CR 701.23a
    @Test
    fun wanderersHabitRamps() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(wanderersHabit)
        alice.library("Island")
        alice.casts(wanderersHabit)
        playOut("Island")
        expect { card("Island").tapped shouldBe true }
    }

    /** As it enters, a sorcery in the graveyard gains harmonize for its mana cost. */
    // CR 702.180a
    @Test
    fun hymnweaverMageGivesHarmonize() = scenario {
        alice.battlefield("Forest", "Island", "Mountain", "Island")
        alice.hand(hymnweaverMage)
        alice.graveyard(Plain.study)
        alice.library(ogre)
        alice.casts(hymnweaverMage, paying = listOf("Forest", "Island", "Mountain"))
        playOut(Plain.study)
        alice.casts(Plain.study, way = permitted(Plain.study))
        playOut()
        expect {
            alice.hand has ogre
            alice.exile has Plain.study
        }
    }

    /** Two counters divided; creatures with counters gain vigilance and trample. */
    // CR 601.2d
    @Test
    fun unisonChargeCountersAndTramples() = scenario {
        alice.battlefield("Forest", "Forest", bear, ogre)
        alice.hand(unisonCharge)
        alice.casts(unisonCharge, targets = listOf(bear, ogre))
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            ogre hasKeyword Keyword.Trample
            bear hasKeyword Keyword.Vigilance
        }
    }

    /** +3/+0 and haste. */
    @Test
    fun recklessGallopHastens() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(bear, sick = true)
        alice.hand(recklessGallop)
        alice.casts(recklessGallop, target = bear)
        playOut()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Haste
        }
    }

    // ---- Chunk 08: Omens

    /** The Omen half: two basic Forests, one onto the battlefield tapped, one to hand; the card shuffled into the library. */
    // CR 720.3d
    @Test
    fun thornbloomRegentStakesTheWilds() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(thornbloomRegent)
        alice.library("Forest", "Forest", ogre)
        alice.casts(thornbloomRegent, way = omen)
        playOut("Forest", "Forest")
        expect {
            alice.library has thornbloomRegent
            alice.hand has "Forest"
        }
    }

    /** The Dragon: it and other Dragons entering gain 3 life. */
    @Test
    fun thornbloomRegentGainsLife() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(thornbloomRegent)
        alice.casts(thornbloomRegent)
        playOut()
        expect { alice.life shouldBe 23 }
    }

    /** Omen: a creature tapped and a card; then shuffled into the library, not the graveyard. */
    // CR 720.3d
    @Test
    fun shoalIsleDragonGlances() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(shoalIsleDragon)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(shoalIsleDragon, way = omen, target = giant)
        playOut()
        expect {
            giant.tapped shouldBe true
            alice.library has shoalIsleDragon
            alice.graveyard lacks shoalIsleDragon
        }
    }

    /** Omen: a small creature destroyed. Dragon: an artifact destroyed as it enters. */
    @Test
    fun meddlingStormbroodPaysBack() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(meddlingStormbrood)
        bob.battlefield(ogre)
        alice.casts(meddlingStormbrood, way = omen, target = ogre)
        playOut()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun meddlingStormbroodDestroysAnArtifact() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(meddlingStormbrood)
        bob.battlefield(Plain.idol)
        alice.casts(meddlingStormbrood)
        playOut(Plain.idol)
        expect { bob.graveyard has Plain.idol }
    }

    /** Dragon: up to two cards from one graveyard exiled. Omen: a counter and a card. */
    // CR 115.1
    @Test
    fun savageGravegorgerExilesFromOneGraveyard() = scenario {
        alice.battlefield(*Array(6) { "Swamp" })
        alice.hand(savageGravegorger)
        bob.graveyard(ogre, giant)
        alice.casts(savageGravegorger)
        playOut(ogre, giant)
        expect { bob.exile.size shouldBe 2 }
    }

    @Test
    fun savageGravegorgerGloamSight() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(savageGravegorger)
        alice.library(ogre)
        alice.casts(savageGravegorger, way = omen, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.hand has ogre
        }
    }

    /** Dragon: up to two other nonland permanents bounced. Omen: three cards and a discard. */
    @Test
    fun deltaRiverRegentBounces() = scenario {
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(deltaRiverRegent)
        bob.battlefield(ogre, giant)
        alice.casts(deltaRiverRegent)
        playOut(ogre, giant)
        expect { bob.hand.size shouldBe 2 }
    }

    @Test
    fun deltaRiverRegentWrapsAndSnares() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(deltaRiverRegent)
        alice.library(ogre, giant, bear)
        alice.casts(deltaRiverRegent, way = omen)
        playOut(bear)
        expect {
            alice.hand.size shouldBe 2
            alice.library has deltaRiverRegent
        }
    }

    /** Dragon: all counters removed from a creature. Omen: +2/+2, lifelink and hexproof. */
    // CR 122.8
    @Test
    fun cleansingStormbroodRemovesCounters() = scenario {
        alice.battlefield(*Array(5) { "Swamp" })
        alice.hand(cleansingStormbrood)
        bob.battlefield(ogre, counters = listOf(p1p1 to 2))
        alice.casts(cleansingStormbrood)
        playOut(ogre)
        expect { ogre.counters(p1p1) shouldBe 0 }
    }

    @Test
    fun cleansingStormbroodDrinksVigor() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(cleansingStormbrood)
        alice.casts(cleansingStormbrood, way = omen, target = bear)
        playOut()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Lifelink
            bear hasKeyword Keyword.Hexproof
        }
    }

    /** Omen: a 2/2 Soldier. Dragon: at combat, another creature +1/+0. */
    @Test
    fun dawnrilerWyrmMusters() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(dawnrilerWyrm)
        alice.casts(dawnrilerWyrm, way = omen)
        playOut()
        expect {
            "Soldier Token".power shouldBe 2
            alice.library has dawnrilerWyrm
        }
    }

    @Test
    fun dawnrilerWyrmRallies() = scenario {
        alice.battlefield(dawnrilerWyrm, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(bear)
        expect { bear.power shouldBe 3 }
    }

    /** Omen: a spell of mana value 2 or less countered. Dragon: +2/+0 for noncreature or Dragon spells. */
    @Test
    fun glyphscaleStormbroodShrieks() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(glyphscaleStormbrood)
        bob.battlefield("Mountain")
        bob.hand(Plain.shock)
        turn(active = bob)
        bob.casts(Plain.shock, target = "alice")
        bob.passes()
        alice.casts(glyphscaleStormbrood, way = omen, target = Plain.shock)
        playOut()
        expect { alice.life shouldBe 20 }
    }

    @Test
    fun glyphscaleStormbroodGrows() = scenario {
        alice.battlefield(glyphscaleStormbrood, "Island")
        alice.hand(Plain.study)
        alice.library(ogre)
        alice.casts(Plain.study)
        playOut()
        expect { glyphscaleStormbrood.power shouldBe 4 }
    }

    /** Omen: a basic land to the hand. Dragon: 3 life as it enters. */
    @Test
    fun jungleWildlingSeeksANest() = scenario {
        alice.battlefield("Forest")
        alice.hand(jungleWildling)
        alice.library(ogre, "Island")
        alice.casts(jungleWildling, way = omen)
        playOut("Island")
        expect { alice.hand has "Island" }
    }

    /** Omen: -X/-X to each non-Dragon creature. Dragon: ward—discard a card. */
    // CR 107.3a
    @Test
    fun carrionRegentSeepsVenom() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", dragon)
        alice.hand(carrionRegent)
        bob.battlefield(ogre, bear)
        alice.casts(carrionRegent, way = omen, x = 2)
        playOut()
        expect {
            bob.graveyard has bear
            ogre.power shouldBe 1
            dragon.power shouldBe 4
        }
    }

    @Test
    fun carrionRegentWardDiscard() = scenario {
        alice.battlefield(carrionRegent)
        bob.battlefield("Swamp")
        bob.hand(Plain.doom, bear)
        turn(active = bob)
        bob.casts(Plain.doom, target = carrionRegent)
        bob.passes()
        playOut(may = false)
        expect { carrionRegent.zone shouldBe Zone.Battlefield }
    }

    /** Omen: a discard for two cards. Dragon: firebreathing. */
    @Test
    fun galeshriekFeralRoustsOut() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(galeshriekFeral, bear)
        alice.library(ogre, giant)
        alice.casts(galeshriekFeral, way = omen)
        playOut(bear)
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun galeshriekFeralBreathesFire() = scenario {
        alice.battlefield(galeshriekFeral, "Mountain", "Mountain")
        alice.activates(galeshriekFeral)
        playOut()
        expect { galeshriekFeral.power shouldBe 4 }
    }

    /** Omen: 5 damage to a creature without flying. Dragon: 5 life. */
    @Test
    fun doublemawStormbroodBites() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(doublemawStormbrood)
        bob.battlefield(giant, Plain.flyer)
        assertFailsWith<Throwable> { alice.casts(doublemawStormbrood, way = omen, target = Plain.flyer) }
    }

    @Test
    fun doublemawStormbroodScorches() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(doublemawStormbrood)
        bob.battlefield(giant)
        alice.casts(doublemawStormbrood, way = omen, target = giant)
        playOut()
        expect { bob.graveyard has giant }
    }

    /** Omen: three counters. Dragon: sorceries and Dragons as though they had flash. */
    // CR 702.8a
    @Test
    fun spinwingStormbroodSoars() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(spinwingStormbrood)
        alice.casts(spinwingStormbrood, way = omen, target = bear)
        playOut()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun spinwingStormbroodFlashesSorceries() = scenario {
        alice.battlefield(spinwingStormbrood, "Island")
        alice.hand(Plain.study)
        alice.library(ogre)
        bob.library(ogre)
        turn(active = bob)
        bob.passes()
        alice.casts(Plain.study)
        playOut()
        expect { alice.hand has ogre }
    }
}
