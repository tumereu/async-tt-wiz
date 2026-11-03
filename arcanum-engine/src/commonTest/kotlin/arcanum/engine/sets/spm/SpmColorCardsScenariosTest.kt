package arcanum.engine.sets.spm

import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Marvel's Spider-Man cards of parser chunks 07–11 in play (the backfill of 2026-11-03): the white, blue, black, red and
 * green cards. Arachnosaur, Bold Beast has only covered keywords (reach, trample, ward).
 */
class SpmColorCardsScenariosTest {
    private val plazaPigeon = Cards.invent("Plaza Pigeon", "{W}", "Creature — Bird", """
        Flying
        When this creature leaves the battlefield, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """, "1/1")
    private val welcomingBlock = Cards.invent("Welcoming Block", "{3}{W}", "Enchantment — Aura", """
        Enchant land
        When this Aura enters, create three 1/1 green and white Human Citizen creature tokens.
        Enchanted land has "{1}, {T}: Target creature gets +1/+1 until end of turn for each creature you control. Activate only as a sorcery."
    """)
    private val dazzlingWebcrawler = Cards.invent("Dazzling Webcrawler", "{1}{W}", "Legendary Creature — Spider Human Hero", """
        Flash
        {1}: Dazzling Webcrawler gains flying until end of turn.
        {1}, Sacrifice Dazzling Webcrawler: Creatures you control gain hexproof and indestructible until end of turn.
    """, "3/2")
    private val swiftwing = Cards.invent("Swiftwing, Skyborne Friend", "{4}{W}", "Legendary Creature — Human Hero", """
        Flying
        When Swiftwing enters, another target creature you control gains flying until end of turn.
    """, "3/4")
    private val abruptTakedown = Cards.invent("Abrupt Takedown", "{1}{W}", "Instant", "Destroy target attacking or blocking creature.")
    private val cocoonedAway = Cards.invent("Cocooned Away", "{2}{W}", "Enchantment", "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.")
    private val wristSpinnerets = Cards.invent("Wrist Spinnerets", "{1}{W}", "Artifact — Equipment", """
        Equipped creature gets +1/+1 and has reach and "Whenever this creature attacks, tap target creature an opponent controls."
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val grimsbyLabResearchers = Cards.invent("Grimsby Lab Researchers", "{3}{U}", "Creature — Human Scientist", "{6}{U}: Draw two cards.", "1/5")
    private val automatonExpertise = Cards.invent("Automaton Expertise", "{4}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, create two 1/1 colorless Robot artifact creature tokens with flying.
        Enchanted creature gets +2/+2.
    """)
    private val bitweaver = Cards.invent("Bitweaver, Net Sentinel", "{2}{U}", "Legendary Creature — Spider Avatar Hero", "When Bitweaver enters, return up to one target nonland permanent to its owner's hand.", "2/2")
    private val swoosh = Cards.invent("Swoosh!", "{1}{U}", "Instant", """
        Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
        Return target nonland permanent to its owner's hand. If this spell was kicked, draw a card.
    """)
    private val backAlleyPickpocket = Cards.invent("Back-Alley Pickpocket", "{1}{B}", "Creature — Human Rogue Villain", "When this creature dies, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "2/2")
    private val edwinBrask = Cards.inventFaces("ModalDoubleFaced",
        face("Edwin Brask", "{2}{B}", "Legendary Creature — Human Hero Villain", """
            When Edwin Brask enters, return target creature card with mana value 1 or less from your graveyard to the battlefield.
            {3}{B}{R}{G}: Transform Edwin Brask. Activate only as a sorcery.
        """, "3/3", null, null),
        face("Ink Fiend, Deadly Guardian", "{3}{B}{R}{G}", "Legendary Creature — Symbiote Hero Villain", """
            Menace, trample, haste
            Whenever Ink Fiend attacks, you may sacrifice another creature. If you do, draw X cards, then you may put a permanent card with mana value X or less from your hand onto the battlefield, where X is the sacrificed creature's mana value.
        """, "5/5", null, null),
    )
    private val innerFiendsRacketeers = Cards.invent("Inner Fiends Racketeers", "{3}{B}", "Creature — Human Rogue Villain", "Discard a card: This creature gets +1/+0 and gains menace until end of turn. Activate only as a sorcery. (It can't be blocked except by two or more creatures.)", "3/4")
    private val pitilessMuscle = Cards.invent("Pitiless Muscle", "{1}{B}", "Creature — Human Mercenary Villain", """
        Lifelink
        {3}{B}: This creature deals 1 damage to each opponent.
    """, "2/1")
    private val mordrake = Cards.invent("Mordrake, Eater of Weavers", "{X}{B}{B}", "Legendary Creature — Vampire Villain", """
        Lifelink
        Mordrake enters with X +1/+1 counters on him.
        When Mordrake enters, he deals X damage to target opponent.
    """, "2/1")
    private val recklessInquiry = Cards.invent("Reckless Inquiry", "{2}{B}", "Sorcery", "Surveil 2, then draw two cards. You lose 2 life. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)")
    private val theSpiritGem = Cards.invent("The Spirit Gem", "{1}{B}", "Legendary Artifact — Infinity Stone", """
        Indestructible
        {T}: Add {B}.
        {6}{B}, {T}, Exile a creature you control: Harness The Spirit Gem. (Once harnessed, its ∞ ability is active.)
        ∞ — At the beginning of your upkeep, return target creature card from your graveyard to the battlefield.
    """)
    private val theBlotsGateway = Cards.invent("The Blot's Gateway", "{2}{B}", "Instant", "Put target creature on the bottom of its owner's library. You lose 2 life unless you control a Villain.")
    private val inkFiendMaliceLoosed = Cards.invent("Ink Fiend, Malice Loosed", "{4}{B}", "Legendary Creature — Symbiote Villain", """
        Deathtouch
        {2}{B}, Exile this card from your graveyard: Put two +1/+1 counters on target creature. It gains deathtouch until end of turn. Activate only as a sorcery.
    """, "4/5")
    private val inkSoakedTabby = Cards.invent("Ink-Soaked Tabby", "{2}{B}", "Creature — Symbiote Cat Villain", """
        Deathtouch
        When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
    """, "2/3")
    private val furiousThrong = Cards.invent("Furious Throng", "{1}{R}", "Creature — Human Citizen", """
        Trample
        Whenever you cast a spell with mana value 4 or greater, this creature deals 1 damage to each opponent.
        {5}{R}: Put two +1/+1 counters on this creature. Activate only as a sorcery.
    """, "2/2")
    private val hobgremlin = Cards.invent("Hobgremlin, Cloaked Raider", "{1}{R}", "Legendary Creature — Goblin Human Villain", """
        Flying, haste
        Whenever you discard a card, Hobgremlin gets +2/+0 until end of turn.
    """, "1/2")
    private val maskedMouser = Cards.invent("Masked Mouser", "{R}", "Creature — Spider Cat Hero", """
        Haste
        Discard a card, Sacrifice this creature: Draw a card.
    """, "1/1")
    private val slagform = Cards.invent("Slagform, Living Furnace", "{2}{R}", "Legendary Creature — Elemental Villain", """
        When Slagform enters, search your library for a basic Mountain card, put it onto the battlefield tapped, then shuffle.
        Slagform gets +1/+1 for each Mountain you control.
        When Slagform leaves the battlefield, sacrifice a land.
    """, "0/0")
    private val drumbeatWeaver = Cards.invent("Drumbeat Weaver, Wild Heart", "{2}{R}", "Legendary Creature — Spider Human Hero", """
        Reach
        Whenever Drumbeat Weaver becomes tapped, you may discard a card. If you do, draw a card.
    """, "2/3")
    private val cabHustler = Cards.invent("Cab Hustler", "{1}{R}", "Creature — Human Pilot", "{1}, {T}: Target creature gains haste until end of turn.", "3/1")
    private val ezraHolt = Cards.invent("Ezra Holt, Weave Totem", "{4}{G}", "Legendary Creature — Spider Human Advisor", """
        Reach
        At the beginning of combat on your turn, target Spider you control gets +2/+2 until end of turn.
    """, "3/5")
    private val basementHacker = Cards.invent("Basement Hacker", "{2}{G}", "Creature — Human Advisor", """
        {T}: Add one mana of any color.
        Web Support — {2}{G}, {T}: Put a +1/+1 counter on target Spider. Activate only as a sorcery.
    """, "2/3")
    private val kablam = Cards.invent("Kablam!", "{2}{G}", "Sorcery", "Put a +1/+1 counter on target creature you control. It fights target creature an opponent controls. (Each deals damage equal to its power to the other.)")
    private val huntersPanthers = Cards.invent("Hunter's Panthers", "{1}{G}", "Creature — Cat Villain", "{2}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.", "2/2")
    private val sewerSkulkLizards = Cards.invent("Sewer Skulk Lizards", "{1}{G}", "Creature — Lizard Villain", """
        Trample
        Whenever you cast a spell with mana value 4 or greater, put a +1/+1 counter on this creature.
    """, "1/3")
    private val snapshots = Cards.invent("Snapshots of the Masked Climber", "{2}{G}", "Artifact", """
        When this artifact enters, look at the top five cards of your library. You may reveal up to two creature cards from among them and put them into your hand. Put the rest on the bottom of your library in a random order.
        {1}, {T}, Sacrifice this artifact: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val ringBrawler = Cards.invent("Ring Brawler", "{3}{G}", "Creature — Human Warrior Performer", """
        When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        This creature can't be blocked by more than one creature.
    """, "4/4")
    private val irradiatedArachnid = Cards.invent("Irradiated Arachnid", "{G}", "Creature — Spider", """
        Reach, deathtouch
        Fateful Bite — {2}, Sacrifice this creature: Search your library for a Spider Hero card, reveal it, put it into your hand, then shuffle. Activate only as a sorcery.
    """, "1/1")
    private val encouragingGuardians = Cards.invent("Encouraging Guardians", "{2}{G}", "Creature — Human Citizen", "Tap two untapped creatures you control: Add one mana of any color.", "3/3")
    private val brickfaceClimb = Cards.invent("Brickface Climb", "{3}{G}", "Enchantment", """
        When this enchantment enters, create a 2/1 green Spider creature token with reach, then you gain 1 life for each Spider you control.
        Spiders you control get +1/+1 and can't be blocked by creatures with defender.
    """)
    private val loomOfFateAndBeing = Cards.invent("Loom of Fate and Being", "{6}{G}{G}", "Enchantment", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        At the beginning of combat on your turn, look at the top five cards of your library. You may put a creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
    """)
    private val purge = Cards.invent("Rooftop Purge", "{W}", "Instant", "Destroy target enchantment.")
    private val oneDrop = Cards.invent("Rooftop Squire", "{W}", "Creature — Human", "", "1/1")
    private val bear = spmBear
    private val ogre = spmOgre
    private val giant = spmGiant
    private val spider = spmSpider
    private val villain = spmVillain

    // ---- Chunk 07: white

    /** Leaving the battlefield, it leaves a Food. */
    // CR 603.6c, 111.10b
    @Test
    fun plazaPigeonLeavesAFood() = scenario {
        alice.battlefield(plazaPigeon, "Swamp")
        alice.hand(Spm.doom)
        alice.casts(Spm.doom, target = plazaPigeon)
        playOut()
        expect { alice.battlefield has "Food Token" }
    }

    /** Three Citizens as it enters; the enchanted land pumps a creature by the creatures you control. */
    // CR 303.4, 613.1f
    @Test
    fun welcomingBlockMakesCitizensAndAPumpingLand() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Forest", "Island", bear)
        alice.hand(welcomingBlock)
        alice.casts(welcomingBlock, target = "Forest", paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        playOut()
        expect { alice.battlefield has "Human Citizen Token" }
        alice.activates("Forest", ability = 1, target = bear, paying = listOf("Island"))
        playOut()
        expect { bear.power shouldBe 6 }
    }

    /** {1}: flying; {1} and itself: the team gains hexproof and indestructible. */
    // CR 702.11a, 702.12b
    @Test
    fun dazzlingWebcrawlerFliesAndShields() = scenario {
        alice.battlefield(dazzlingWebcrawler, bear, "Plains", "Plains")
        alice.activates(dazzlingWebcrawler)
        playOut()
        expect { dazzlingWebcrawler hasKeyword Keyword.Flying }
        alice.activates(dazzlingWebcrawler, ability = 1)
        playOut()
        expect {
            alice.graveyard has dazzlingWebcrawler
            bear hasKeyword Keyword.Hexproof
            bear hasKeyword Keyword.Indestructible
        }
    }

    /** As it enters, another creature gains flying. */
    // CR 611.2c
    @Test
    fun swiftwingLendsFlight() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(swiftwing)
        alice.casts(swiftwing)
        playOut(bear)
        expect { bear hasKeyword Keyword.Flying }
    }

    /** Only an attacking or blocking creature can be destroyed. */
    // CR 506.4
    @Test
    fun abruptTakedownDestroysAnAttacker() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(abruptTakedown)
        bob.battlefield(ogre, giant)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre)
        bob.passes()
        assertFailsWith<Throwable> { alice.casts(abruptTakedown, target = giant) }
    }

    @Test
    fun abruptTakedownTakesTheAttackerDown() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(abruptTakedown)
        bob.battlefield(ogre, giant)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(abruptTakedown, target = ogre)
        playOut()
        expect { bob.graveyard has ogre }
    }

    /** The exiled permanent returns when the enchantment leaves. */
    // CR 610.3
    @Test
    fun cocoonedAwayExilesUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(cocoonedAway, purge)
        bob.battlefield(giant)
        alice.casts(cocoonedAway, paying = listOf("Plains", "Plains#1", "Plains#2"))
        playOut(giant)
        expect { bob.exile has giant }
        alice.casts(purge, target = cocoonedAway)
        playOut()
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    /** Equipped: +1/+1, reach, and attacking taps an opposing creature. */
    // CR 301.5, 702.17a
    @Test
    fun wristSpinneretsTapABlocker() = scenario {
        alice.battlefield(wristSpinnerets, bear, "Plains", "Plains")
        bob.battlefield(giant)
        alice.activates(wristSpinnerets, target = bear)
        playOut()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Reach
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        playOut(giant)
        expect { giant.tapped shouldBe true }
    }

    // ---- Chunk 08: blue

    /** Seven mana: two cards. */
    // CR 602.2
    @Test
    fun grimsbyLabResearchersDrawTwo() = scenario {
        alice.battlefield(grimsbyLabResearchers, *Array(7) { "Island" })
        alice.library(ogre, giant)
        alice.activates(grimsbyLabResearchers)
        playOut()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Two flying Robots and +2/+2 for the enchanted creature. */
    // CR 303.4
    @Test
    fun automatonExpertiseMakesRobots() = scenario {
        alice.battlefield(*Array(5) { "Island" }, bear)
        alice.hand(automatonExpertise)
        alice.casts(automatonExpertise, target = bear)
        playOut()
        expect {
            bear.power shouldBe 4
            card("Robot Token", 1).zone shouldBe Zone.Battlefield
            "Robot Token" hasKeyword Keyword.Flying
        }
    }

    /** Up to one nonland permanent back to its owner's hand. */
    // CR 115.1
    @Test
    fun bitweaverBouncesAPermanent() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(bitweaver)
        bob.battlefield(giant)
        alice.casts(bitweaver)
        playOut(giant)
        expect { bob.hand has giant }
    }

    /** Kicked: the bounce and a card. */
    // CR 702.33d
    @Test
    fun swooshKickedDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(swoosh)
        alice.library(ogre)
        bob.battlefield(giant)
        alice.casts(swoosh, target = giant, kicked = 1)
        playOut()
        expect {
            bob.hand has giant
            alice.hand has ogre
        }
    }

    // ---- Chunk 09: black

    /** Dying, it leaves a Treasure. */
    // CR 700.4
    @Test
    fun backAlleyPickpocketLeavesATreasure() = scenario {
        alice.battlefield(backAlleyPickpocket, "Swamp")
        alice.hand(Spm.doom)
        alice.casts(Spm.doom, target = backAlleyPickpocket)
        playOut()
        expect { alice.battlefield has "Treasure Token" }
    }

    /** A creature card of mana value 1 or less returns to the battlefield. */
    // CR 115.1
    @Test
    fun edwinBraskReturnsACheapCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(oneDrop)
        alice.hand(edwinBrask)
        alice.casts(edwinBrask)
        playOut(oneDrop)
        expect { oneDrop.zone shouldBe Zone.Battlefield }
    }

    /** A discard: +1/+0 and menace. */
    // CR 702.111a
    @Test
    fun innerFiendsRacketeersDiscardForMenace() = scenario {
        alice.battlefield(innerFiendsRacketeers)
        alice.hand(bear)
        alice.activates(innerFiendsRacketeers, choosing = listOf(bear))
        playOut()
        expect {
            innerFiendsRacketeers.power shouldBe 4
            innerFiendsRacketeers hasKeyword Keyword.Menace
            alice.graveyard has bear
        }
    }

    /** {3}{B}: 1 damage to each opponent, gaining life by lifelink. */
    // CR 702.15b
    @Test
    fun pitilessMusclePings() = scenario {
        alice.battlefield(pitilessMuscle, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(pitilessMuscle)
        playOut()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** X = 3: three counters, 3 damage to the opponent, 3 life by lifelink. */
    // CR 107.3a, 614.1c
    @Test
    fun mordrakeEntersWithXAndDrains() = scenario {
        alice.battlefield(*Array(5) { "Swamp" })
        alice.hand(mordrake)
        alice.casts(mordrake, x = 3)
        playOut("bob")
        expect {
            mordrake.counters(p1p1) shouldBe 3
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    /** Surveil 2 (both to the graveyard), then two cards, and 2 life. */
    // CR 701.25a
    @Test
    fun recklessInquirySurveilsAndDraws() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(recklessInquiry)
        alice.library(Spm.shock, Spm.study, ogre, giant)
        alice.casts(recklessInquiry)
        playOut(Spm.shock, Spm.study)
        expect {
            alice.graveyard has Spm.shock
            alice.hand has ogre
            alice.hand has giant
            alice.life shouldBe 18
        }
    }

    /** Harnessed by exiling a creature; then each upkeep a creature card returns. */
    // CR 701.64a, 702.186b
    @Test
    fun theSpiritGemHarnessedReanimates() = scenario {
        alice.battlefield(theSpiritGem, bear, *Array(7) { "Swamp" })
        alice.graveyard(giant)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        alice.activates(theSpiritGem, ability = abilityIndex(theSpiritGem, "Harness"), choosing = listOf(bear))
        playOut()
        assertTrue(ObjectMark.Harnessed in state.obj(card(theSpiritGem))!!.marks)
        expect { alice.exile has bear }
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(giant)
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    /** The creature goes to the bottom; with no Villain, 2 life lost. */
    // CR 401.4
    @Test
    fun theBlotsGatewayBottomsACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(theBlotsGateway)
        bob.battlefield(giant)
        bob.library(ogre)
        alice.casts(theBlotsGateway, target = giant)
        playOut()
        expect {
            bob.library has giant
            alice.life shouldBe 18
        }
    }

    @Test
    fun theBlotsGatewayFreeWithAVillain() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", villain)
        alice.hand(theBlotsGateway)
        bob.battlefield(giant)
        alice.casts(theBlotsGateway, target = giant)
        playOut()
        expect { alice.life shouldBe 20 }
    }

    /** From the graveyard: two counters and deathtouch. */
    // CR 602.2
    @Test
    fun inkFiendMaliceLoosedFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.graveyard(inkFiendMaliceLoosed)
        alice.activates(inkFiendMaliceLoosed, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Deathtouch
            alice.exile has inkFiendMaliceLoosed
        }
    }

    /** As it enters, two cards milled. */
    // CR 701.17a
    @Test
    fun inkSoakedTabbyMillsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(inkSoakedTabby)
        alice.library(ogre, giant, bear)
        alice.casts(inkSoakedTabby)
        playOut()
        expect {
            alice.graveyard has ogre
            alice.graveyard has giant
            alice.library has bear
        }
    }

    // ---- Chunk 10: red

    /** A big spell pings each opponent; six mana grow it. */
    // CR 202.3, 603.2
    @Test
    fun furiousThrongPingsForBigSpells() = scenario {
        alice.battlefield(furiousThrong, *Array(4) { "Forest" }, *Array(6) { "Mountain" })
        alice.hand(Spm.bigSpell)
        alice.casts(Spm.bigSpell, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3"))
        playOut()
        expect { bob.life shouldBe 19 }
        alice.activates(furiousThrong)
        playOut()
        expect { furiousThrong.counters(p1p1) shouldBe 2 }
    }

    /** A discard gives +2/+0. */
    // CR 701.9a
    @Test
    fun hobgremlinGrowsOnDiscard() = scenario {
        alice.battlefield(hobgremlin, maskedMouser)
        alice.hand(bear)
        alice.library(ogre)
        alice.activates(maskedMouser, choosing = listOf(bear))
        playOut()
        expect {
            hobgremlin.power shouldBe 3
            alice.hand has ogre
            alice.graveyard has maskedMouser
        }
    }

    /** A Mountain fetched tapped makes it bigger; leaving, it takes a land with it. */
    // CR 613.4c, 701.21a
    @Test
    fun slagformGrowsWithMountainsAndTakesALand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Swamp")
        alice.hand(slagform, Spm.doom)
        alice.library("Mountain")
        alice.casts(slagform, paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        playOut("Mountain")
        expect {
            slagform.power shouldBe 4
            slagform.toughness shouldBe 4
        }
        alice.casts(Spm.doom, target = slagform)
        playOut("Mountain")
        expect { alice.graveyard has "Mountain" }
    }

    /** Becoming tapped (attacking), she rummages. */
    // CR 701.26a
    @Test
    fun drumbeatWeaverRummagesWhenTapped() = scenario {
        alice.battlefield(drumbeatWeaver)
        alice.hand(bear)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(drumbeatWeaver)
        playOut(bear)
        expect {
            alice.graveyard has bear
            alice.hand has ogre
        }
    }

    /** A creature that just came gains haste and can attack. */
    // CR 702.10b
    @Test
    fun cabHustlerHastesACreature() = scenario {
        alice.battlefield(cabHustler, "Mountain")
        alice.battlefield(ogre, sick = true)
        alice.activates(cabHustler, target = ogre)
        playOut()
        expect { ogre hasKeyword Keyword.Haste }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
    }

    // ---- Chunk 11: green

    /** At the beginning of combat, a Spider gets +2/+2. */
    // CR 507.1
    @Test
    fun ezraHoltPumpsASpider() = scenario {
        alice.battlefield(ezraHolt, spider)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(spider)
        expect { spider.power shouldBe 4 }
    }

    /** Any colour; Web Support puts a counter on a Spider. */
    // CR 605.1a
    @Test
    fun basementHackerSupportsASpider() = scenario {
        alice.battlefield(basementHacker, spider, "Forest", "Forest", "Forest")
        alice.activates(basementHacker, ability = 1, target = spider)
        playOut()
        expect { spider.counters(p1p1) shouldBe 1 }
    }

    /** A counter, then a fight. */
    // CR 701.14a
    @Test
    fun kablamCountersAndFights() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(kablam)
        bob.battlefield(spider)
        alice.casts(kablam, targets = listOf(bear, spider))
        playOut()
        expect {
            bob.graveyard has spider
            bear.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 1
        }
    }

    /** +2/+2 once a turn only. */
    // CR 602.5b
    @Test
    fun huntersPanthersPumpOnce() = scenario {
        alice.battlefield(huntersPanthers, *Array(6) { "Forest" })
        alice.activates(huntersPanthers)
        playOut()
        expect { huntersPanthers.power shouldBe 4 }
        assertFailsWith<Throwable> { alice.activates(huntersPanthers) }
    }

    /** A spell of mana value 4 or more: a counter. */
    // CR 202.3
    @Test
    fun sewerSkulkLizardsGrowOnBigSpells() = scenario {
        alice.battlefield(sewerSkulkLizards, *Array(4) { "Forest" })
        alice.hand(Spm.bigSpell)
        alice.casts(Spm.bigSpell)
        playOut()
        expect { sewerSkulkLizards.counters(p1p1) shouldBe 1 }
    }

    /** Two creature cards kept from five; sacrificed, a Treasure. */
    // CR 701.20a
    @Test
    fun snapshotsKeepTwoCreatures() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains")
        alice.hand(snapshots)
        alice.library(ogre, Spm.shock, giant, bear, Spm.study)
        alice.casts(snapshots, paying = listOf("Forest", "Forest#1", "Forest#2"))
        playOut(ogre, giant)
        expect {
            alice.hand has ogre
            alice.hand has giant
            alice.library has bear
        }
        alice.activates(snapshots, paying = listOf("Plains"))
        playOut()
        expect { alice.battlefield has "Treasure Token" }
    }

    /** A Treasure as it enters; two blockers can't block it. */
    // CR 509.1b
    @Test
    fun ringBrawlerMakesTreasureAndStandsAlone() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(ringBrawler)
        alice.casts(ringBrawler)
        playOut()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun ringBrawlerCantBeDoubleBlocked() = scenario {
        alice.battlefield(ringBrawler)
        bob.battlefield(ogre, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ringBrawler)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(ogre to ringBrawler, bear to ringBrawler) }
    }

    /** Sacrificed, it finds a Spider Hero card. */
    // CR 701.23a
    @Test
    fun irradiatedArachnidFindsASpiderHero() = scenario {
        alice.battlefield(irradiatedArachnid, "Forest", "Forest")
        alice.library(ogre, Spm.hero)
        alice.activates(irradiatedArachnid)
        playOut(Spm.hero)
        expect {
            alice.hand has Spm.hero
            alice.graveyard has irradiatedArachnid
        }
    }

    /** Two untapped creatures tapped: one mana of any colour pays for a spell. */
    // CR 605.1a
    @Test
    fun encouragingGuardiansTapTwoForMana() = scenario {
        alice.battlefield(encouragingGuardians, bear, ogre)
        alice.hand(Spm.shock)
        alice.activates(encouragingGuardians, choosing = listOf(bear, ogre), color = arcanum.api.Color.Red)
        alice.casts(Spm.shock, target = "bob")
        playOut()
        expect {
            bob.life shouldBe 18
            bear.tapped shouldBe true
            ogre.tapped shouldBe true
        }
    }

    /** A Spider token and a life per Spider; Spiders get +1/+1 and slip past defenders. */
    // CR 613.4c, 509.1b
    @Test
    fun brickfaceClimbMakesASpiderAndPumpsSpiders() = scenario {
        alice.battlefield(*Array(4) { "Forest" }, spider)
        alice.hand(brickfaceClimb)
        alice.casts(brickfaceClimb)
        playOut()
        expect {
            alice.life shouldBe 22
            spider.power shouldBe 3
            "Spider Token".power shouldBe 3
        }
    }

    @Test
    fun brickfaceClimbSpidersEvadeDefenders() = scenario {
        alice.battlefield(brickfaceClimb, spider)
        bob.battlefield(Spm.wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(spider)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(Spm.wall to spider) }
    }

    /** At the beginning of combat, a creature card from the top five onto the battlefield. */
    // CR 507.1, 401.4
    @Test
    fun loomOfFateAndBeingFindsACreature() = scenario {
        alice.battlefield(loomOfFateAndBeing)
        alice.library(Spm.shock, giant, Spm.study, bear, "Forest")
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(giant)
        expect { giant.zone shouldBe Zone.Battlefield }
    }
}
