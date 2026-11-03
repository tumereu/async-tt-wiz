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
 * Tarkir: Dragonstorm cards of parser chunks 03–04 in play: renew from the graveyard (an ability word, CR 207.2c), beholding a Dragon (CR 701.4) as a cost, for flash and for a bigger effect, and the Dragon cards around them.
 */
class TdmRenewBeholdAndDragonCardsScenariosTest {
    private val bejeweledCrocodile = Cards.invent("Bejeweled Crocodile", "{4}{B}", "Creature — Crocodile", """
        When this creature dies, create a 2/2 black Zombie Druid creature token.
        Renew — {B}, Exile this card from your graveyard: Put a +1/+1 counter on target creature. Activate only as a sorcery.
    """, "5/3")
    private val envoyOfTheFangs = Cards.invent("Envoy of the Fangs", "{1}{U}", "Creature — Human Rogue", "Renew — {3}{U}, Exile this card from your graveyard: Put two +1/+1 counters on target creature. Activate only as a sorcery.", "2/1")
    private val apothecarysHelper = Cards.invent("Apothecary's Helper", "{1}{B}", "Creature — Monkey", """
        Lifelink
        Renew — {1}{B}, Exile this card from your graveyard: Put a lifelink counter on target creature. Activate only as a sorcery.
    """, "2/1")
    private val championOfTheTusks = Cards.invent("Champion of the Tusks", "{2}{G}", "Creature — Human Warrior", """
        Trample
        Renew — {1}{G}, Exile this card from your graveyard: Put a +1/+1 counter and a trample counter on target creature. Activate only as a sorcery.
    """, "4/2")
    private val coilingSage = Cards.invent("Coiling Sage", "{4}{U}", "Creature — Snake Wizard", """
        When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Renew — {2}{U}, Exile this card from your graveyard: Tap target creature an opponent controls and put a stun counter on it. Activate only as a sorcery.
    """, "4/4")
    private val hoardscaleKeeper = Cards.invent("Hoardscale Keeper", "{1}{B}{G}{U}", "Creature — Dragon", """
        Flying
        Whenever one or more cards leave your graveyard during your turn, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        Renew — {2}{B}{G}{U}, Exile this card from your graveyard: Put two +1/+1 counters and a flying counter on target creature. Activate only as a sorcery.
    """, "3/3")
    private val mirefenProwler = Cards.invent("Mirefen Prowler", "{2}{G}{G}", "Creature — Snake Ranger", """
        When this creature enters, you may mill cards equal to the number of lands you control.
        Renew — {1}{G}, Exile this card from your graveyard: Put X +1/+1 counters on target creature, where X is the number of land cards in your graveyard. Activate only as a sorcery.
    """, "5/5")
    private val nagaSkinshaper = Cards.invent("Naga Skinshaper", "{3}{U}", "Creature — Snake Shapeshifter", """
        You may have this creature enter as a copy of any creature on the battlefield.
        Renew — {2}{U}, Exile this card from your graveyard: Put a +1/+1 counter on target nonlegendary creature you control. Each other creature you control becomes a copy of that creature until end of turn. Activate only as a sorcery.
    """, "0/0")
    private val fenmarshRevenant = Cards.invent("Fenmarsh Revenant", "{1}{B}{B}", "Creature — Vampire", """
        Flying, deathtouch, lifelink
        Renew — {2}{B}, Exile this card from your graveyard: Put a flying counter, a deathtouch counter, and a lifelink counter on target creature. Activate only as a sorcery.
    """, "3/3")
    private val blightHexRakshasa = Cards.invent("Blight-Hex Rakshasa", "{1}{B}", "Creature — Demon", """
        Trample
        Decayed (This creature can't block. When it attacks, sacrifice it at end of combat.)
        Renew — {X}{B}{B}, Exile this card from your graveyard: Put a decayed counter on each of X target creatures. Activate only as a sorcery.
    """, "5/5")
    private val sageOfTheCoil = Cards.invent("Sage of the Coil", "{2}{G}", "Creature — Human Druid", """
        When this creature enters, put a +1/+1 counter on target creature.
        Renew — {3}{G}, Exile this card from your graveyard: Put a +1/+1 counter on target creature, then double the number of +1/+1 counters on that creature. Activate only as a sorcery.
    """, "2/2")
    private val junglePummeler = Cards.invent("Jungle Pummeler", "{3}{G}", "Creature — Beast", """
        Reach
        Renew — {4}{G}, Exile this card from your graveyard: Put two +1/+1 counters and a reach counter on target creature. Activate only as a sorcery.
    """, "4/4")
    private val rockhewnDragon = Cards.invent("Rockhewn Dragon", "{5}", "Artifact Creature — Dragon", """
        Flying, vigilance
        Whenever this creature attacks, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "3/3")
    private val summonTheElderWyrms = Cards.invent("Summon the Elder Wyrms", "{W}{U}{B}{R}{G}", "Enchantment", """
        Dragons you control have indestructible.
        At the beginning of your upkeep, for each color, put a +1/+1 counter on a Dragon you control of that color. If you put +1/+1 counters on five Dragons this way, you win the game.
    """)
    private val acridBreath = Cards.invent("Acrid Breath", "{B}", "Instant", """
        As an additional cost to cast this spell, behold a Dragon or pay {1}. (To behold a Dragon, choose a Dragon you control or reveal a Dragon card from your hand.)
        Target creature gets -3/-3 until end of turn.
    """)
    private val silencingConqueror = Cards.invent("Silencing Conqueror", "{2}{W}", "Creature — Dragon", """
        Flying
        Activated abilities of artifacts, creatures, and planeswalkers can't be activated.
    """, "3/3")
    private val unmakingBreath = Cards.invent("Unmaking Breath", "{1}{U}", "Instant", """
        As an additional cost to cast this spell, you may behold a Dragon. (You may choose a Dragon you control or reveal a Dragon card from your hand.)
        Counter target spell unless its controller pays {2}. If a Dragon was beheld, counter that spell unless its controller pays {4} instead.
    """)
    private val wyrmgenesis = Cards.invent("Wyrmgenesis", "{6}{R}{R}", "Enchantment", "You may cast Dragon spells without paying their mana costs.")
    private val wyrmsQuarry = Cards.invent("Wyrm's Quarry", "{2}{B}", "Instant", """
        This spell costs {2} more to cast if it targets a Dragon.
        Destroy target creature.
    """)
    private val wyrmScholar = Cards.invent("Wyrm Scholar", "{2}{U}", "Creature — Human Wizard", """
        When this creature enters, look at the top six cards of your library. You may reveal an instant, sorcery, or Dragon card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        Untapped Dragons you control have hexproof.
    """, "1/3")
    private val stormbroodAugur = Cards.invent("Stormbrood Augur", "{U}", "Creature — Human Scout", "{2}, {T}: Search your library for a card named Stormbrood Orb or Rockhewn Dragon, reveal it, put it into your hand, then shuffle.", "0/3")
    private val stormbroodOrb = Cards.invent("Stormbrood Orb", "{3}", "Artifact", """
        Each Dragon you control enters with an additional +1/+1 counter on it.
        {T}: Add one mana of any color.
    """)
    private val cindermawSentry = Cards.invent("Cindermaw Sentry", "{2}", "Artifact Creature — Chimera", "When this creature enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top. If you control a Dragon, put that card onto the battlefield tapped instead.", "2/1")
    private val vortexOfTheElderWyrm = Cards.invent("Vortex of the Elder Wyrm", null, "Land", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a Dragon spell or an Omen spell.
        {4}, {T}, Sacrifice this land: Search your library for a Dragon card, reveal it, put it into your hand, then shuffle.
    """)
    private val lavabornHellkite = Cards.invent("Lavaborn Hellkite", "{2}{R}{R}", "Creature — Dragon", """
        Flying
        When this creature enters, destroy target nonbasic land an opponent controls. Its controller searches their library for a basic land card, puts it onto the battlefield tapped with a stun counter on it, then shuffles. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "4/5")
    private val searingBreath = Cards.invent("Searing Breath", "{1}{R}", "Sorcery", """
        You may cast this spell as though it had flash if you behold a Dragon as an additional cost to cast it. (To behold a Dragon, choose a Dragon you control or reveal a Dragon card from your hand.)
        Searing Breath deals 4 damage to target creature or planeswalker.
    """)
    private val moxCarnelian = Cards.invent("Mox Carnelian", "{0}", "Legendary Artifact", "{T}: Add one mana of any color. Activate only if you control a Dragon.")
    private val marrowBreath = Cards.invent("Marrow Breath", "{1}{W}", "Instant", """
        As an additional cost to cast this spell, you may behold a Dragon. (You may choose a Dragon you control or reveal a Dragon card from your hand.)
        Marrow Breath deals 5 damage to target attacking or blocking creature. If a Dragon was beheld, you gain 2 life.
    """)
    private val lancingBreath = Cards.invent("Lancing Breath", "{1}{G}", "Instant", """
        As an additional cost to cast this spell, you may behold a Dragon. (You may choose a Dragon you control or reveal a Dragon card from your hand.)
        Target creature you control deals damage equal to its power to target creature or planeswalker. If a Dragon was beheld, surveil 2.
    """)
    private val rhazirWyrmAscendant = Cards.invent("Rhazir, Wyrm Ascendant", "{1}{R}", "Legendary Creature — Human Druid", """
        When Rhazir enters, you may behold a Dragon. If you do, create a Treasure token. (To behold a Dragon, choose a Dragon you control or reveal a Dragon card from your hand.)
        Whenever a Dragon you control enters, put a +1/+1 counter on Rhazir. Until end of turn, Rhazir becomes a Dragon in addition to his other types and gains flying.
    """, "2/2")
    private val thunderscaleHeir = Cards.invent("Thunderscale Heir", "{4}{R}{R}", "Creature — Dragon", """
        Flying
        Other Dragons you control get +1/+1.
        Storm (When you cast this spell, copy it for each spell cast before it this turn. Copies become tokens.)
    """, "4/4")

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1
    private val blueDragon = Cards.invent("Steppe Tide Wyrm", "{3}{U}", "Creature — Dragon", "Flying", "3/3")
    private val blackDragon = Cards.invent("Steppe Bone Wyrm", "{3}{B}", "Creature — Dragon", "Flying", "3/3")
    private val steppeTower = Cards.invent("Steppe Watchtower", null, "Land", "{T}: Add {C}.")
    private val greenDragon = Cards.invent("Steppe Moss Wyrm", "{3}{G}", "Creature — Dragon", "Flying", "3/3")

    // ---- Chunk 03: renew

    /** Dying, it leaves a Zombie Druid; renewed from the graveyard, a +1/+1 counter. */
    // CR 702.2, 113.6
    @Test
    fun bejeweledCrocodileLeavesAZombieAndRenews() = scenario {
        alice.battlefield(bejeweledCrocodile, bear, "Swamp", "Swamp")
        alice.hand(Plain.doom)
        alice.casts(Plain.doom, target = bejeweledCrocodile, paying = listOf("Swamp"))
        playOut()
        expect { alice.battlefield has "Zombie Druid Token" }
        alice.activates(bejeweledCrocodile, target = bear, paying = listOf("Swamp#1"))
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.exile has bejeweledCrocodile
        }
    }

    /** Renew: two counters, as a sorcery only. */
    // CR 602.5d
    @Test
    fun envoyOfTheFangsRenews() = scenario {
        alice.battlefield(bear, "Island", "Island", "Island", "Island")
        alice.graveyard(envoyOfTheFangs)
        alice.activates(envoyOfTheFangs, target = bear)
        playOut()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun envoyOfTheFangsOnlyAsASorcery() = scenario {
        alice.battlefield(bear, "Island", "Island", "Island", "Island")
        alice.graveyard(envoyOfTheFangs)
        turn(active = bob)
        assertFailsWith<Throwable> { alice.activates(envoyOfTheFangs, target = bear) }
    }

    /** Renew: a lifelink counter gives lifelink. */
    // CR 122.1b
    @Test
    fun apothecarysHelperGivesLifelink() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp")
        alice.graveyard(apothecarysHelper)
        alice.activates(apothecarysHelper, target = bear)
        playOut()
        expect { bear hasKeyword Keyword.Lifelink }
    }

    /** Renew: a +1/+1 counter and a trample counter. */
    // CR 122.1b
    @Test
    fun championOfTheTusksRenews() = scenario {
        alice.battlefield(bear, "Forest", "Forest")
        alice.graveyard(championOfTheTusks)
        alice.activates(championOfTheTusks, target = bear)
        playOut()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
        }
    }

    /** As it enters, a creature is tapped and stunned: it stays tapped through an untap step. */
    // CR 122.1d
    @Test
    fun coilingSageStunsACreature() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(coilingSage)
        alice.library(ogre)
        bob.battlefield(giant)
        bob.library(ogre)
        alice.casts(coilingSage)
        playOut(giant)
        expect { giant.tapped shouldBe true }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect {
            giant.tapped shouldBe true
            giant.counters(arcanum.api.CounterKind.Stun) shouldBe 0
        }
    }

    /** A card leaving its controller's graveyard on their turn makes a Treasure (renew exiles one). */
    // CR 603.2
    @Test
    fun hoardscaleKeeperTreasureWhenCardsLeave() = scenario {
        alice.battlefield(hoardscaleKeeper, bear, "Island", "Island", "Island", "Island")
        alice.graveyard(envoyOfTheFangs)
        alice.activates(envoyOfTheFangs, target = bear)
        playOut()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun hoardscaleKeeperRenews() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp", "Forest", "Island", "Island")
        alice.graveyard(hoardscaleKeeper)
        alice.activates(hoardscaleKeeper, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Flying
        }
    }

    /** As it enters, a mill as long as the lands; renewed, a counter for each land card in the graveyard. */
    // CR 701.17a
    @Test
    fun mirefenProwlerMillsAndRenews() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(mirefenProwler)
        alice.library("Island", "Island", ogre, "Island", giant)
        alice.casts(mirefenProwler)
        playOut()
        expect { alice.graveyard.size shouldBe 4 }
    }

    @Test
    fun mirefenProwlerRenewsByLandCards() = scenario {
        alice.battlefield(bear, "Forest", "Forest")
        alice.graveyard(mirefenProwler, "Island", "Island", "Swamp")
        alice.activates(mirefenProwler, target = bear)
        playOut()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    /** It may enter as a copy of a creature; renewed, the other creatures become copies of the countered one. */
    // CR 707.2, 706.2
    @Test
    fun nagaSkinshaperEntersAsACopy() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(nagaSkinshaper)
        bob.battlefield(giant)
        alice.casts(nagaSkinshaper)
        playOut(giant)
        expect { card(nagaSkinshaper).view.name shouldBe giant }
    }

    @Test
    fun nagaSkinshaperRenewsIntoCopies() = scenario {
        alice.battlefield(giant, bear, "Island", "Island", "Island")
        alice.graveyard(nagaSkinshaper)
        alice.activates(nagaSkinshaper, target = giant)
        playOut()
        expect {
            giant.counters(p1p1) shouldBe 1
            card(bear).view.name shouldBe giant
            card(bear).power shouldBe 5
        }
    }

    /** Renew: flying, deathtouch and lifelink counters. */
    // CR 122.1b
    @Test
    fun fenmarshRevenantRenews() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp", "Swamp")
        alice.graveyard(fenmarshRevenant)
        alice.activates(fenmarshRevenant, target = bear)
        playOut()
        expect {
            bear hasKeyword Keyword.Flying
            bear hasKeyword Keyword.Deathtouch
            bear hasKeyword Keyword.Lifelink
        }
    }

    /** Renew with X = 2: decayed counters on two creatures, which can't block. */
    // CR 702.147a, 122.1b
    @Test
    fun blightHexRakshasaDecaysX() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(blightHexRakshasa)
        bob.battlefield(ogre, giant)
        alice.activates(blightHexRakshasa, x = 2, targets = listOf(ogre, giant))
        playOut()
        expect {
            ogre hasKeyword Keyword.Decayed
            giant hasKeyword Keyword.Decayed
        }
    }

    /** A counter as it enters; renewed: a counter, then the counters doubled. */
    // CR 701.10e
    @Test
    fun sageOfTheCoilDoubles() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(ogre, counters = listOf(p1p1 to 2))
        alice.graveyard(sageOfTheCoil)
        alice.activates(sageOfTheCoil, target = ogre)
        playOut()
        expect { ogre.counters(p1p1) shouldBe 6 }
    }

    /** Renew: two counters and a reach counter. */
    // CR 122.1b
    @Test
    fun junglePummelerRenews() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(junglePummeler)
        alice.activates(junglePummeler, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Reach
        }
    }

    // ---- Chunk 04: behold and Dragons

    /** Attacking, it surveils 1. */
    // CR 701.25a
    @Test
    fun rockhewnDragonSurveils() = scenario {
        alice.battlefield(rockhewnDragon)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rockhewnDragon)
        playOut(ogre)
        expect { alice.graveyard has ogre }
    }

    /** Dragons are indestructible; each upkeep, a counter on a Dragon of each colour; five Dragons win the game. */
    // CR 104.2b
    @Test
    fun summonTheElderWyrmsCountersAndWins() = scenario {
        alice.battlefield(summonTheElderWyrms, Plain.whiteDragon, blueDragon, blackDragon, dragon, greenDragon)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        expect { dragon hasKeyword Keyword.Indestructible }
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(Plain.whiteDragon, blueDragon, blackDragon, dragon, greenDragon)
        expect { dragon.counters(p1p1) shouldBe 1 }
        assertTrue(state.player(bob.id).lost)
    }

    @Test
    fun summonTheElderWyrmsCountersWithoutWinning() = scenario {
        alice.battlefield(summonTheElderWyrms, dragon)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(dragon)
        expect { dragon.counters(p1p1) shouldBe 1 }
        assertTrue(!state.player(bob.id).lost)
    }

    /** Beholding a Dragon pays the additional cost: -3/-3. */
    // CR 701.4a, 601.2b
    @Test
    fun acridBreathBeholdsADragon() = scenario {
        alice.battlefield("Swamp", dragon)
        alice.hand(acridBreath)
        bob.battlefield(ogre)
        alice.casts(acridBreath, target = ogre, option = "Behold", choosing = listOf(dragon))
        playOut(dragon)
        expect { bob.graveyard has ogre }
    }

    @Test
    fun acridBreathPaysInstead() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(acridBreath)
        bob.battlefield(ogre)
        alice.casts(acridBreath, target = ogre, option = "Pay")
        playOut()
        expect { bob.graveyard has ogre }
    }

    /** Activated abilities of artifacts and creatures can't be activated; lands' still can. */
    // CR 602.5
    @Test
    fun silencingConquerorStopsActivations() = scenario {
        alice.battlefield(silencingConqueror, Plain.looter, "Island")
        alice.library(ogre)
        assertFailsWith<Throwable> { alice.activates(Plain.looter) }
    }

    /** Counter unless {2}; with a Dragon beheld, unless {4}: three lands aren't enough. */
    // CR 118.12a
    @Test
    fun unmakingBreathAsksMoreWithADragon() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(unmakingBreath, dragon)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(Plain.shock)
        turn(active = bob)
        bob.casts(Plain.shock, target = "alice", paying = listOf("Mountain"))
        bob.passes()
        alice.casts(unmakingBreath, target = Plain.shock, kicked = 1, choosing = listOf(dragon))
        playOut(dragon, may = true)
        expect {
            alice.life shouldBe 20
            bob.graveyard has Plain.shock
        }
    }

    @Test
    fun unmakingBreathTwoWithoutADragon() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(unmakingBreath)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(Plain.shock)
        turn(active = bob)
        bob.casts(Plain.shock, target = "alice", paying = listOf("Mountain"))
        bob.passes()
        alice.casts(unmakingBreath, target = Plain.shock, kicked = 0)
        playOut(may = true)
        expect { alice.life shouldBe 18 }
    }

    /** Dragon spells cast without paying their mana costs. */
    // CR 118.9
    @Test
    fun wyrmgenesisCastsDragonsFree() = scenario {
        alice.battlefield(wyrmgenesis)
        alice.hand(dragon)
        alice.casts(dragon, way = permitted(dragon))
        playOut()
        expect { dragon.zone shouldBe Zone.Battlefield }
    }

    /** {2} more to target a Dragon. */
    // CR 601.2f
    @Test
    fun wyrmsQuarryCostsMoreForDragons() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(wyrmsQuarry)
        bob.battlefield(dragon, ogre)
        alice.casts(wyrmsQuarry, target = dragon)
        assertTrue(decision<Decision.ManaPayment>().cost.toString() == "{4}{B}")
    }

    @Test
    fun wyrmsQuarryDestroysACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(wyrmsQuarry)
        bob.battlefield(dragon, ogre)
        alice.casts(wyrmsQuarry, target = ogre)
        playOut()
        expect { bob.graveyard has ogre }
    }

    /** An instant, sorcery or Dragon from the top six; untapped Dragons have hexproof. */
    // CR 702.11a
    @Test
    fun wyrmScholarFindsADragon() = scenario {
        alice.battlefield("Island", "Island", "Island", dragon)
        alice.hand(wyrmScholar)
        alice.library(ogre, giant, dragon, bear, ogre, giant)
        alice.casts(wyrmScholar)
        playOut(dragon)
        expect {
            alice.hand has dragon
            card(dragon, 0).hasKeyword(Keyword.Hexproof)
        }
    }

    /** It finds a card named as one of two cards. */
    // CR 201.2
    @Test
    fun stormbroodAugurFindsTheOrb() = scenario {
        alice.battlefield(stormbroodAugur, "Island", "Island")
        alice.library(ogre, stormbroodOrb)
        alice.activates(stormbroodAugur)
        playOut(stormbroodOrb)
        expect { alice.hand has stormbroodOrb }
    }

    /** Each Dragon enters with an extra counter. */
    // CR 614.1c
    @Test
    fun stormbroodOrbGrowsDragons() = scenario {
        alice.battlefield(stormbroodOrb, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dragon)
        alice.casts(dragon)
        playOut()
        expect { dragon.counters(p1p1) shouldBe 1 }
    }

    /** A basic land to the top; with a Dragon, onto the battlefield tapped instead. */
    // CR 701.23a
    @Test
    fun cindermawSentryWithADragon() = scenario {
        alice.battlefield("Plains", "Plains", dragon)
        alice.hand(cindermawSentry)
        alice.library(ogre, "Forest")
        alice.casts(cindermawSentry)
        playOut("Forest")
        expect {
            card("Forest").zone shouldBe Zone.Battlefield
            card("Forest").tapped shouldBe true
        }
    }

    @Test
    fun cindermawSentryWithoutADragon() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(cindermawSentry)
        alice.library(ogre, "Forest")
        alice.casts(cindermawSentry)
        playOut("Forest")
        expect { alice.library has "Forest" }
    }

    /** Its coloured mana only for a Dragon or an Omen spell; sacrificed, it finds a Dragon. */
    // CR 106.6
    @Test
    fun vortexOfTheElderWyrmFindsADragon() = scenario {
        alice.battlefield(vortexOfTheElderWyrm, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library(ogre, dragon)
        alice.activates(vortexOfTheElderWyrm, ability = 2)
        playOut(dragon)
        expect { alice.hand has dragon }
    }

    @Test
    fun vortexOfTheElderWyrmPaysForADragon() = scenario {
        alice.battlefield(vortexOfTheElderWyrm, "Swamp", "Swamp", "Swamp")
        alice.hand(dragon)
        alice.casts(dragon, paying = listOf("Swamp", "Swamp#1", "Swamp#2", vortexOfTheElderWyrm + ":R"))
        playOut()
        expect { dragon.zone shouldBe Zone.Battlefield }
    }

    /** A nonbasic land destroyed; its controller fetches a basic land tapped with a stun counter. */
    // CR 701.23a, 122.1d
    @Test
    fun lavabornHellkiteBreaksALand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(lavabornHellkite)
        bob.battlefield(steppeTower)
        bob.library("Forest")
        alice.casts(lavabornHellkite)
        playOut(steppeTower, "Forest")
        expect {
            bob.graveyard has steppeTower
            card("Forest").tapped shouldBe true
            card("Forest").counters(arcanum.api.CounterKind.Stun) shouldBe 1
        }
    }

    /** Cast at instant speed by beholding a Dragon; it can't be without one. */
    // CR 702.8a, 701.4a
    @Test
    fun searingBreathFlashedByBeholding() = scenario {
        alice.battlefield("Mountain", "Mountain", dragon)
        alice.hand(searingBreath)
        bob.battlefield(ogre)
        turn(active = bob)
        bob.passes()
        alice.casts(searingBreath, target = ogre, kicked = 1, choosing = listOf(dragon))
        playOut(dragon)
        expect { bob.graveyard has ogre }
    }

    @Test
    fun searingBreathNotFlashedWithoutBeholding() = scenario {
        alice.battlefield("Mountain", "Mountain", dragon)
        alice.hand(searingBreath)
        bob.battlefield(ogre)
        turn(active = bob)
        bob.passes()
        assertFailsWith<Throwable> { alice.casts(searingBreath, target = ogre, kicked = 0) }
    }

    @Test
    fun searingBreathAsASorcery() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(searingBreath)
        bob.battlefield(ogre)
        alice.casts(searingBreath, target = ogre, kicked = 0)
        playOut()
        expect { bob.graveyard has ogre }
    }

    /** Mana only while you control a Dragon. */
    // CR 605.1a
    @Test
    fun moxCarnelianNeedsADragon() = scenario {
        alice.battlefield(moxCarnelian)
        alice.hand(Plain.shock)
        assertFailsWith<Throwable> { alice.casts(Plain.shock, target = "bob", paying = listOf(moxCarnelian + ":R")) }
    }

    @Test
    fun moxCarnelianWithADragon() = scenario {
        alice.battlefield(moxCarnelian, dragon)
        alice.hand(Plain.shock)
        alice.casts(Plain.shock, target = "bob", paying = listOf(moxCarnelian + ":R"))
        playOut()
        expect { bob.life shouldBe 18 }
    }

    /** 5 damage to a blocker; beholding a Dragon from the hand gains 2 life too. */
    // CR 701.4a
    @Test
    fun marrowBreathWithADragonGainsLife() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(marrowBreath, dragon)
        bob.battlefield(giant)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(giant)
        bob.passes()
        alice.casts(marrowBreath, target = giant, kicked = 1, choosing = listOf(dragon))
        playOut(dragon)
        expect {
            bob.graveyard has giant
            alice.life shouldBe 22
        }
    }

    /** A creature deals damage equal to its power; beheld, surveil 2. */
    // CR 120.3
    @Test
    fun lancingBreathBites() = scenario {
        alice.battlefield("Forest", "Forest", giant, dragon)
        alice.hand(lancingBreath)
        alice.library(ogre, ogre)
        bob.battlefield(Plain.bear)
        alice.casts(lancingBreath, targets = listOf(giant, Plain.bear), kicked = 1, choosing = listOf(dragon))
        playOut(dragon, ogre, ogre)
        expect {
            bob.graveyard has Plain.bear
            alice.graveyard.size shouldBe 3
        }
    }

    /** Beholding a Dragon as it enters: a Treasure; a Dragon entering makes it a flying Dragon with a counter. */
    // CR 701.4a, 205.1b
    @Test
    fun rhazirBeholdsAndBecomesADragon() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(rhazirWyrmAscendant, dragon)
        alice.casts(rhazirWyrmAscendant, paying = listOf("Mountain", "Mountain#1"))
        playOut(dragon)
        expect { alice.battlefield has "Treasure Token" }
        alice.casts(dragon, paying = listOf("Mountain#2", "Mountain#3", "Mountain#4", "Mountain#5"))
        playOut()
        expect {
            rhazirWyrmAscendant.counters(p1p1) shouldBe 1
            rhazirWyrmAscendant hasKeyword Keyword.Flying
            rhazirWyrmAscendant.subtypes.contains(arcanum.api.Subtype("Dragon")) shouldBe true
        }
    }

    /** Other Dragons +1/+1; storm copies it for an earlier spell. */
    // CR 702.40a
    @Test
    fun thunderscaleHeirStorms() = scenario {
        alice.battlefield(*Array(7) { "Mountain" }, dragon)
        alice.hand(thunderscaleHeir, Plain.bolt)
        alice.casts(Plain.bolt, target = "bob", paying = listOf("Mountain"))
        playOut()
        alice.casts(thunderscaleHeir)
        playOut()
        expect {
            dragon.power shouldBe 6
            card(thunderscaleHeir, 1).zone shouldBe Zone.Battlefield
        }
    }
}
