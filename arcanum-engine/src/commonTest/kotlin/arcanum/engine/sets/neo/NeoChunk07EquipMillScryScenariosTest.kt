package arcanum.engine.sets.neo

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNull

/** Chunk 7 of NEO: Equipment that attaches on an attack alone, counts its own resolutions, lends the keywords of the cards it exiled and taps on
 * attaching; a dies trigger returning permanents of a limited total mana value with three players, a comparison with the sacrificed permanent, a
 * reflexive trigger for a nonland card exiled, mills, scry on becoming tapped, a modal attack trigger's reflexive trigger and a Saga's chapter
 * condition. */
class NeoChunk07EquipMillScryScenariosTest {
    private val wakizashi = Cards.invent("Forebear's Wakizashi", "{1}{W}", "Artifact — Equipment", """
        Whenever a Samurai or Warrior you control attacks alone, you may pay {1}. When you do, attach this Equipment to it.
        Equipped creature gets +2/+1.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val clubs = Cards.invent("Brass Knuckle Clubs", "{1}", "Artifact — Equipment", """
        {2}: Until end of turn, equipped creature gets +X/+0, where X is the number of times this ability has resolved this turn.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val devourer = Cards.invent("Devourer of Valor", "{1}", "Legendary Artifact — Equipment", """
        Whenever equipped creature dies, exile it.
        Equipped creature gets +2/+0.
        As long as a card exiled with Devourer of Valor has flying, equipped creature has flying. The same is true for first strike, double strike, deathtouch, haste, hexproof, indestructible, lifelink, menace, protection, reach, trample, and vigilance.
        Equip {1}
    """)
    private val edge = Cards.invent("Colossal Plasma Edge", "{2}{B}", "Artifact — Equipment", """
        Equipped creature gets +4/+0.
        Whenever this Equipment becomes attached to a creature, tap that creature.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val sorai = Cards.invent("Sorai, the Whirling Heavens", "{4}{U}{U}", "Legendary Creature — Dragon Spirit", """
        Flying, ward {3}
        When Sorai dies, choose one —
        • Return any number of target nonland permanents with total mana value 6 or less to their owners' hands.
        • Mill six cards, then return up to two instant and/or sorcery cards from your graveyard to your hand.
    """, "6/6")
    private val tether = Cards.invent("Tether to the Present", "{2}{U}{U}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        Search your library for an Equipment or Vehicle card, put that card onto the battlefield, then shuffle. If it has mana value less than the sacrificed permanent's mana value, scry 2.
    """)
    private val masatsugu = Cards.invent("Masatsugu, Ravenous Discord", "{3}{B}", "Legendary Creature — Ogre Demon", """
        {B}, Sacrifice a creature: Scry 2.
        {2}{R}, {T}: Exile the top card of your library. You may play that card this turn. When you exile a nonland card this way, Masatsugu deals damage equal to the exiled card's mana value to any target.
    """, "4/4")
    private val warden = Cards.invent("Kaito Warden", "{3}", "Artifact Creature — Construct", """
        Defender
        When this creature enters, target player mills three cards. (They put the top three cards of their library into their graveyard.)
        {3}: Put target card from a graveyard on the bottom of its owner's library.
    """, "3/3")
    private val riddlewright = Cards.invent("Moonfolk Riddlewright", "{2}{U}", "Artifact Creature — Moonfolk Wizard", """
        Flying
        Whenever this creature becomes tapped, scry 1.
    """, "1/4")
    // the back face of Lessons of the Qilin, on a creature of its own
    private val serpent = Cards.invent("Neo Qilin Serpent", "{1}{G}", "Enchantment Creature — Snake Monk", """
        Whenever this creature attacks, choose one —
        • Exile target creature card from a graveyard. When you do, create a 1/1 colorless Spirit creature token.
        • Exile target noncreature card from a graveyard. When you do, put a +1/+1 counter on target creature you control.
    """, "1/1")
    private val witness = Cards.inventFaces("Transform",
        face("Witness the Unnameable", "{3}{U}{U}", "Enchantment — Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Creatures you don't control get -2/-0 until your next turn.
            II — If you have one or fewer cards in hand, draw four cards. Otherwise, scry 2, then draw two cards.
            III — Exile this Saga, then return it to the battlefield transformed under your control.
        """.trimIndent(), null, null, null),
        face("Vision of the Unnameable", null, "Enchantment Creature — Spirit", "Flying, trample\nThis creature gets +1/+1 for each card in your hand.", "0/0", null, "U"))

    private val samurai = Cards.invent("Neo Equip Samurai", "{1}{W}", "Creature — Human Samurai", "", "2/2")
    private val bear = Cards.invent("Neo Equip Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Neo Equip Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val cub = Cards.invent("Neo Equip Cub", "{G}", "Creature — Bear", "", "1/1")
    private val giant = Cards.invent("Neo Equip Giant", "{3}{G}", "Creature — Giant", "", "4/4")
    private val trinket = Cards.invent("Neo Equip Trinket", "{3}", "Artifact", "")
    private val hawk = Cards.invent("Neo Equip Hawk", "{1}{W}", "Creature — Bird", "Flying\nProtection from red", "1/1")
    private val dagger = Cards.invent("Neo Equip Dagger", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+0.\nEquip {1}")
    private val jolt = Cards.invent("Neo Equip Jolt", "{0}", "Instant", "Neo Equip Jolt deals 3 damage to target creature.")
    private val doom = Cards.invent("Neo Equip Doom", "{0}", "Instant", "Destroy target creature.")
    private val musing = Cards.invent("Neo Equip Musing", "{U}", "Sorcery", "Draw a card.")

    // CR 506.5, 603.12, 701.3a: the Samurai attacking alone; paying {1} attaches the Equipment to it
    @Test
    fun wakizashiAttachesToASamuraiAttackingAlone() = scenario {
        alice.battlefield(wakizashi, samurai, "Plains")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(samurai)
        resolveAll()
        payYes()
        expect {
            samurai.power shouldBe 4
            samurai.toughness shouldBe 3
        }
    }

    // CR 506.5: with another attacker it doesn't attack alone
    @Test
    fun wakizashiDoesNotTriggerWithTwoAttackers() = scenario {
        alice.battlefield(wakizashi, samurai, bear, "Plains")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(samurai, bear)
        resolveAll()
        assertNull(pending)
        expect { samurai.power shouldBe 2 }
    }

    // CR 608.2h: the first resolution gives +1/+0, the second +2/+0 (each X fixed as it resolves): +3/+0 in all
    @Test
    fun clubsCountTheirOwnResolutions() = scenario {
        alice.battlefield(clubs, bear, "Plains", "Plains", "Plains", "Plains")
        attach(clubs, bear)
        alice.activates(clubs)
        allPass()
        expect { bear.power shouldBe 3 }
        alice.activates(clubs)
        allPass()
        expect { bear.power shouldBe 5 }
    }

    // CR 607.2a: the dead creature is exiled with it; CR 702.16a: its protection, quality and all, and its flying go to the next equipped creature
    @Test
    fun devourerLendsTheExiledCardsKeywords() = scenario {
        alice.battlefield(devourer, hawk, bear, "Plains")
        attach(devourer, hawk)
        bob.hand(doom)
        turn(priority = bob)
        bob.casts(doom, target = hawk)
        resolveAll()
        expect {
            hawk.zone shouldBe Zone.Exile
            bear lacksKeyword Keyword.Flying.kind
        }
        alice.activates(devourer, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying.kind
            bear hasKeyword Keyword.Protection.Kind
            bear lacksKeyword Keyword.Hexproof
        }
    }

    // CR 701.3a: equipping the Bear taps it
    @Test
    fun plasmaEdgeTapsTheCreatureItIsAttachedTo() = scenario {
        alice.battlefield(edge, bear, "Swamp", "Swamp")
        alice.activates(edge, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe true
            bear.power shouldBe 6
        }
    }

    // CR 601.2c, 603.3d: Bob's 4-mana-value Giant and Carol's 2-mana-value Bear (6 in all) return to their owners' hands
    @Test
    fun soraiReturnsPermanentsOfTotalManaValueSix() = scenario(players = 3) {
        alice.battlefield(sorai)
        bob.battlefield(giant)
        carol.battlefield(bear, trinket)
        // its own controller's spell: ward doesn't trigger (CR 702.21a)
        alice.hand(doom)
        alice.casts(doom, target = sorai)
        resolveAll()
        modes(0)
        assertFails { targets(giant, trinket) }
        targets(giant, bear)
        resolveAll()
        expect {
            bob.hand has giant
            carol.hand has bear
            trinket.zone shouldBe Zone.Battlefield
        }
    }

    // CR 608.2h: the Dagger (mana value 1) is less than the sacrificed Ogre (3): scry 2
    @Test
    fun tetherScriesWhenTheFoundCardIsCheaper() = scenario {
        alice.battlefield(ogre, "Island", "Island", "Island", "Island")
        alice.hand(tether)
        alice.library(dagger, "Island", "Island", "Island")
        alice.casts(tether, choosing = listOf(ogre))
        allPass()
        alice.chooses(dagger)
        expect { dagger.zone shouldBe Zone.Battlefield }
        decision<Decision.ChooseObjects>()
    }

    // CR 608.2h: equal mana values (1 and 1): no scry
    @Test
    fun tetherDoesNotScryWhenNotCheaper() = scenario {
        alice.battlefield(cub, "Island", "Island", "Island", "Island")
        alice.hand(tether)
        alice.library(dagger, "Island", "Island", "Island")
        alice.casts(tether, choosing = listOf(cub))
        allPass()
        alice.chooses(dagger)
        resolveAll()
        assertNull(pending)
        expect { dagger.zone shouldBe Zone.Battlefield }
    }

    // CR 603.12, 608.2c: a nonland card exiled: Masatsugu deals damage equal to its mana value (3) to the target
    @Test
    fun masatsuguDealsTheExiledCardsManaValue() = scenario {
        alice.battlefield(masatsugu, "Mountain", "Mountain", "Mountain")
        alice.library(ogre, "Island")
        alice.activates(masatsugu, ability = 1)
        allPass()
        targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            ogre.zone shouldBe Zone.Exile
        }
    }

    // CR 603.12: a land exiled triggers nothing; it may be played this turn (CR 305.1)
    @Test
    fun masatsuguExilingALandOnlyLetsItBePlayed() = scenario {
        alice.battlefield(masatsugu, "Mountain", "Mountain", "Mountain")
        alice.library("Island", "Plains")
        alice.activates(masatsugu, ability = 1)
        allPass()
        assertNull(pending)
        alice.plays("Island")
        expect {
            "Island".zone shouldBe Zone.Battlefield
            bob.life shouldBe 20
        }
    }

    // CR 701.17a: the targeted player (Carol, of three) mills three; CR 401.2: a card from Bob's graveyard goes to the bottom of his library
    @Test
    fun wardenMillsTheTargetAndPutsACardOnTheBottom() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(warden)
        carol.library("Island", "Island", "Island", "Swamp")
        bob.graveyard(musing)
        bob.library("Forest")
        alice.casts(warden)
        allPass()
        targets("carol")
        resolveAll()
        expect {
            carol.graveyard.size shouldBe 3
            carol.library.size shouldBe 1
        }
        alice.activates(warden, target = musing)
        allPass()
        expect { bob.library.names shouldBe listOf("Forest", musing) }
    }

    // CR 701.26a: attacking taps it: scry 1 (a decision with two cards in the library)
    @Test
    fun riddlewrightScriesWhenItBecomesTapped() = scenario {
        alice.battlefield(riddlewright)
        alice.library("Island", "Plains")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(riddlewright)
        resolveAll()
        decision<Decision.ChooseObjects>()
    }

    // CR 700.2, 603.12: exiling a noncreature card from Bob's graveyard; the reflexive trigger's counter goes on the Serpent
    @Test
    fun serpentExilesANoncreatureCardThenPutsACounter() = scenario {
        alice.battlefield(serpent)
        bob.graveyard(musing, cub)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(serpent)
        resolveAll()
        modes(1)
        resolveAll()
        expect {
            musing.zone shouldBe Zone.Exile
            cub.zone shouldBe Zone.Graveyard
            serpent.power shouldBe 2
        }
    }

    // CR 714.2b, 608.2c: one card in hand: chapter II draws four
    @Test
    fun witnessChapterTwoDrawsFourWithOneCardInHand() = scenario {
        alice.hand(cub)
        loreChapter(witness, 2, "Island", "Island", "Island", "Island")
        resolveAll()
        expect { alice.hand.size shouldBe 5 }
    }
}
