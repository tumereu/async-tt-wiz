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
 * Tarkir: Dragonstorm cards of parser chunks 13 and 16 in play: the green, colorless and land cards. Bloodrock Grotto stands for the set's gain lands, Frontier Encampment Grounds and the tri-lands only enter tapped and tap for mana (covered templates).
 */
class TdmGreenArtifactAndLandCardsScenariosTest {
    private val keenSensedHunter = Cards.invent("Keen-Sensed Hunter", "{2}{G}", "Creature — Human Ranger", """
        Trample
        Whenever one or more cards leave your graveyard during your turn, put a +1/+1 counter on this creature.
    """, "3/3")
    private val wyrmbroodsHeirloom = Cards.invent("Wyrmbroods' Heirloom", "{1}{G}", "Artifact", """
        {T}, Tap an untapped creature you control: Add one mana of any color.
        {3}{W}{U}{B}{R}{G}, Sacrifice this artifact: Create a 4/4 Dragon creature token named Reliquary Dragon that's all colors. It has flying, lifelink, and "When this token enters, it deals 3 damage to any target." Activate only as a sorcery.
    """)
    private val phalanxBreaker = Cards.invent("Phalanx Breaker", "{1}{G}", "Creature — Beast", """
        Creatures with power less than this creature's power can't block it.
        As long as you control a creature with a counter on it, this creature gets +1/+2.
    """, "2/1")
    private val herdKeepsake = Cards.invent("Herd Keepsake", "{1}{G}", "Artifact", """
        {T}: Add one mana of any color. Spend this mana only to cast a creature spell.
        {T}: Until end of turn, target creature you control with power 4 or greater gains trample and "Whenever this creature deals combat damage to a player, draw a card."
    """)
    private val legacyReclamation = Cards.invent("Legacy Reclamation", "{1}{G}", "Instant", """
        Choose one —
        • Destroy target artifact.
        • Destroy target enchantment.
        • Exile up to one target card from a graveyard. Draw a card.
    """)
    private val haymakerManeuver = Cards.invent("Haymaker Maneuver", "{2}{G}", "Sorcery", "Put a +1/+1 counter on target creature you control, then it deals damage equal to its power to target creature an opponent controls.")
    private val chitinNestguard = Cards.invent("Chitin Nestguard", "{2}{G}", "Creature — Insect", """
        Defender
        {2}{G}: This creature can attack this turn as though it didn't have defender.
    """, "4/4")
    private val mistveilRejuvenator = Cards.invent("Mistveil Rejuvenator", "{3}{G}", "Creature — Elephant Druid", """
        When this creature enters, you may mill three cards. (You may put the top three cards of your library into your graveyard.)
        {T}: Add an amount of {G} equal to this creature's power.
    """, "2/4")
    private val riteOfReturn = Cards.invent("Rite of Return", "{3}{G}", "Sorcery", "Return up to two target permanent cards from your graveyard to your hand. Target player shuffles up to four target cards from their graveyard into their library. Exile Rite of Return.")
    private val rhazirsResolve = Cards.invent("Rhazir's Resolve", "{1}{G}", "Instant", """
        Choose one —
        • Target creature gets +3/+3 until end of turn.
        • Destroy target creature with flying.
    """)
    private val scaleskinVeil = Cards.invent("Scaleskin Veil", "{G}", "Instant", "Put a +1/+1 counter on target creature you control. It gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)")
    private val serpentCourtDevotee = Cards.invent("Serpent Court Devotee", "{1}{G}", "Creature — Zombie Snake Druid", """
        Deathtouch
        {1}: Add {B}, {G}, or {U}. Activate only once each turn.
    """, "2/1")
    private val torvaldElusiveHunter = Cards.invent("Torvald, Elusive Hunter", "{2}{G}", "Legendary Creature — Human Warrior", """
        This spell can't be countered.
        Trample
        Whenever a creature you control or a creature spell you control becomes the target of a spell or ability an opponent controls, draw a card.
    """, "4/3")
    private val tradeRoadEnvoy = Cards.invent("Trade Road Envoy", "{3}{G}", "Creature — Dog Soldier", "When this creature enters, draw a card if you control a creature with a counter on it. If you don't draw a card this way, put a +1/+1 counter on this creature.", "4/3")
    private val wanderingBotanist = Cards.invent("Wandering Botanist", "{1}{G}", "Creature — Dog Scout", "Whenever this creature becomes tapped, look at the top card of your library. If it's a land card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.", "2/3")
    private val underbrushLeopard = Cards.invent("Underbrush Leopard", "{1}{G}", "Creature — Cat", """
        Vigilance
        {1}, Sacrifice this creature: Destroy target artifact or enchantment.
    """, "2/2")
    private val bloodrockGrotto = Cards.invent("Bloodrock Grotto", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {B} or {R}.
    """)
    private val summitMonastery = Cards.invent("Summit Monastery", null, "Land", """
        This land enters tapped unless you control a Plains or an Island.
        {T}: Add {R}.
        {3}{R}, {T}: Exile the top card of your library. Until the end of your next turn, you may play that card.
    """)
    private val wyrmfireBlade = Cards.invent("Wyrmfire Blade", "{1}", "Artifact — Equipment", """
        Equipped creature gets +2/+2 and has hexproof from monocolored.
        Equip {4}. This ability costs {1} less to activate for each color of the creature it targets.
    """)
    private val frontierEncampmentGrounds = Cards.invent("Frontier Encampment Grounds", null, "Land", """
        This land enters tapped.
        {T}: Add {G}, {U}, or {R}.
    """)
    private val greatDawnwallCity = Cards.invent("Great Dawnwall City", null, "Land", """
        This land enters tapped unless you control a Forest or a Plains.
        {T}: Add {B}.
        {1}{B}, {T}, Exile a creature card from your graveyard: Create a 1/1 white Spirit creature token.
    """)
    private val jadeForgedSentinel = Cards.invent("Jade-Forged Sentinel", "{4}", "Artifact Creature — Ape Snake", """
        Reach
        {2}, {T}: Put target card from a graveyard on the bottom of its owner's library.
    """, "1/5")
    private val riverbankVillage = Cards.invent("Riverbank Village", null, "Land", """
        This land enters tapped unless you control an Island or a Swamp.
        {T}: Add {G}.
        {3}{G}, {T}: Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """)
    private val fogriseVillage = Cards.invent("Fogrise Village", null, "Land", """
        This land enters tapped unless you control a Mountain or a Forest.
        {T}: Add {U}.
        {U}, {T}: The next spell you cast this turn can't be countered.
    """)
    private val orvaxEyeOfTheTempest = Cards.invent("Orvax, Eye of the Tempest", "{7}", "Legendary Planeswalker — Ugin", """
        When you cast this spell, exile up to one target permanent that's one or more colors.
        Whenever you cast a colorless spell, exile up to one target permanent that's one or more colors.
        [+2]: You gain 3 life and draw a card.
        [0]: Add {C}{C}{C}.
        [−11]: Search your library for any number of colorless nonland cards, exile them, then shuffle. Until end of turn, you may cast those cards without paying their mana costs.
    """, loyalty = "7")
    private val watcherOfTheRoadside = Cards.invent("Watcher of the Roadside", "{3}", "Artifact Creature — Golem", "When this creature enters, target player mills two cards. You gain 2 life. (To mill two cards, a player puts the top two cards of their library into their graveyard.)", "3/2")

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1

    private val renewer = Cards.invent("Steppe Renewer", "{1}{G}", "Creature — Human", "{G}, Exile this card from your graveyard: Put a +1/+1 counter on target creature.", "1/1")
    private val multicolor = Cards.invent("Steppe Twin-Hued Knight", "{W}{U}", "Creature — Human Knight", "", "2/2")

    // ---- Chunk 13: green

    /** Cards leaving the graveyard on its controller's turn: a counter. */
    @Test
    fun keenSensedHunterGrows() = scenario {
        alice.battlefield(keenSensedHunter, bear, "Forest", "Forest")
        alice.graveyard(renewer)
        alice.activates(renewer, target = bear)
        playOut()
        expect { keenSensedHunter.counters(p1p1) shouldBe 1 }
    }

    /** Tapping an untapped creature: mana of any colour; the Reliquary Dragon of all colours burns as it enters. */
    // CR 111.4, 105.2
    @Test
    fun wyrmbroodsHeirloomMakesTheReliquaryDragon() = scenario {
        alice.battlefield(wyrmbroodsHeirloom, "Plains", "Island", "Swamp", "Mountain", "Forest", "Forest", "Forest", "Forest")
        alice.activates(wyrmbroodsHeirloom, ability = 1)
        playOut("bob")
        expect {
            bob.life shouldBe 17
            "Reliquary Dragon" hasKeyword Keyword.Lifelink
            "Reliquary Dragon".colors.size shouldBe 5
        }
    }

    @Test
    fun wyrmbroodsHeirloomTapsACreatureForMana() = scenario {
        alice.battlefield(wyrmbroodsHeirloom, bear)
        alice.hand(Plain.shock)
        alice.activates(wyrmbroodsHeirloom, choosing = listOf(bear), color = arcanum.api.Color.Red)
        alice.casts(Plain.shock, target = "bob")
        playOut()
        expect {
            bob.life shouldBe 18
            bear.tapped shouldBe true
        }
    }

    /** Smaller creatures can't block it; with a countered creature, +1/+2. */
    // CR 509.1b
    @Test
    fun phalanxBreakerSlipsPastSmallBlockers() = scenario {
        alice.battlefield(phalanxBreaker)
        bob.battlefield(Plain.elf, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(phalanxBreaker)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(Plain.elf to phalanxBreaker) }
    }

    @Test
    fun phalanxBreakerGrowsWithCounters() = scenario {
        alice.battlefield(phalanxBreaker)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        expect { phalanxBreaker.toughness shouldBe 3 }
    }

    /** Mana only for creature spells; a big creature gains trample and draws on hitting. */
    @Test
    fun herdKeepsakeGivesTrampleAndCards() = scenario {
        alice.battlefield(herdKeepsake, giant)
        alice.library(ogre)
        alice.activates(herdKeepsake, ability = 1, target = giant)
        playOut()
        expect { giant hasKeyword Keyword.Trample }
        hits(giant)
        expect { alice.hand has ogre }
    }

    @Test
    fun herdKeepsakeManaOnlyForCreatures() = scenario {
        alice.battlefield(herdKeepsake)
        alice.hand(Plain.shock)
        assertFailsWith<Throwable> { alice.casts(Plain.shock, target = "bob", paying = listOf(herdKeepsake + ":R")) }
    }

    /** An artifact destroyed; or a graveyard card exiled and a card drawn. */
    @Test
    fun legacyReclamationExilesAndDraws() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(legacyReclamation)
        alice.library(ogre)
        bob.graveyard(giant)
        alice.casts(legacyReclamation, modes = listOf(2), target = giant)
        playOut()
        expect {
            bob.exile has giant
            alice.hand has ogre
        }
    }

    /** A counter, then damage equal to its power to an opposing creature. */
    @Test
    fun haymakerManeuverPunches() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", ogre)
        alice.hand(haymakerManeuver)
        bob.battlefield(Plain.flyer)
        alice.casts(haymakerManeuver, targets = listOf(ogre, Plain.flyer))
        playOut()
        expect {
            bob.graveyard has Plain.flyer
            ogre.view.obj!!.damage shouldBe 0
        }
    }

    /** {2}{G}: it can attack despite defender. */
    // CR 702.3b
    @Test
    fun chitinNestguardAttacks() = scenario {
        alice.battlefield(chitinNestguard, "Forest", "Forest", "Forest")
        bob.library(ogre)
        alice.activates(chitinNestguard)
        playOut()
        hits(chitinNestguard)
        expect { bob.life shouldBe 16 }
    }

    /** May mill three as it enters; taps for {G} equal to its power. */
    @Test
    fun mistveilRejuvenatorMakesGreen() = scenario {
        alice.battlefield(mistveilRejuvenator)
        alice.hand(bear)
        alice.activates(mistveilRejuvenator)
        alice.casts(bear)
        playOut()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    /** Two permanent cards back; a player shuffles graveyard cards into the library; it exiles itself. */
    @Test
    fun riteOfReturnRecovers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(riteOfReturn)
        alice.graveyard(ogre, Plain.idol)
        bob.graveyard(giant)
        alice.casts(riteOfReturn, targets = listOf(ogre, Plain.idol, "bob", giant))
        playOut()
        expect {
            alice.hand has ogre
            bob.library has giant
            alice.exile has riteOfReturn
        }
    }

    /** +3/+3; or a flyer destroyed. */
    @Test
    fun rhazirsResolveBringsDownAFlyer() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(rhazirsResolve)
        bob.battlefield(dragon)
        alice.casts(rhazirsResolve, modes = listOf(1), target = dragon)
        playOut()
        expect { bob.graveyard has dragon }
    }

    /** A counter and hexproof. */
    @Test
    fun scaleskinVeilShields() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(scaleskinVeil)
        alice.casts(scaleskinVeil, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Hexproof
        }
    }

    /** Targeted by an opponent: a card; it can't be countered. */
    // CR 702.21a
    @Test
    fun torvaldDrawsWhenTargeted() = scenario {
        alice.battlefield(torvaldElusiveHunter, bear)
        alice.library(ogre)
        bob.battlefield("Mountain")
        bob.hand(Plain.shock)
        turn(active = bob)
        bob.casts(Plain.shock, target = bear)
        playOut()
        expect { alice.hand has ogre }
    }

    @Test
    fun torvaldCantBeCountered() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(torvaldElusiveHunter)
        bob.battlefield("Island", "Island")
        bob.hand(Plain.counterspell)
        alice.casts(torvaldElusiveHunter)
        alice.passes()
        bob.casts(Plain.counterspell, target = torvaldElusiveHunter)
        playOut()
        expect { torvaldElusiveHunter.zone shouldBe Zone.Battlefield }
    }

    /** With a countered creature, a card; otherwise a counter on itself. */
    @Test
    fun tradeRoadEnvoyDrawsWithCounters() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(tradeRoadEnvoy)
        alice.library(ogre)
        alice.casts(tradeRoadEnvoy)
        playOut()
        expect { alice.hand has ogre }
    }

    @Test
    fun tradeRoadEnvoyCountersWithout() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(tradeRoadEnvoy)
        alice.library(ogre)
        alice.casts(tradeRoadEnvoy)
        playOut()
        expect { tradeRoadEnvoy.counters(p1p1) shouldBe 1 }
    }

    /** Tapped: a land on top goes to the hand. */
    @Test
    fun wanderingBotanistFindsLand() = scenario {
        alice.battlefield(wanderingBotanist)
        alice.library("Forest", ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wanderingBotanist)
        playOut()
        expect { alice.hand has "Forest" }
    }

    @Test
    fun wanderingBotanistMillsANonland() = scenario {
        alice.battlefield(wanderingBotanist)
        alice.library(ogre, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wanderingBotanist)
        playOut()
        expect { alice.graveyard has ogre }
    }

    /** Sacrificed: an artifact destroyed. */
    @Test
    fun underbrushLeopardBreaksAnArtifact() = scenario {
        alice.battlefield(underbrushLeopard, "Forest")
        bob.battlefield(Plain.idol)
        alice.activates(underbrushLeopard, target = Plain.idol)
        playOut()
        expect { bob.graveyard has Plain.idol }
    }

    // ---- Chunk 16: colorless and lands

    /** Enters tapped and gains 1 life; the gain lands of the set repeat it. */
    @Test
    fun bloodrockGrottoGainsLife() = scenario {
        alice.hand(bloodrockGrotto)
        alice.plays(bloodrockGrotto)
        playOut()
        expect {
            bloodrockGrotto.tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    /** Untapped with a Plains; {3}{R}: the top card playable until the end of the next turn. */
    @Test
    fun summitMonasteryEntersUntappedWithAPlains() = scenario {
        alice.battlefield("Plains")
        alice.hand(summitMonastery)
        alice.plays(summitMonastery)
        expect { summitMonastery.tapped shouldBe false }
    }

    @Test
    fun summitMonasteryExilesToPlay() = scenario {
        alice.battlefield(summitMonastery, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library("Forest")
        alice.activates(summitMonastery, ability = 1)
        playOut()
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    /** +2/+2 and hexproof from monocolored; equip cheaper for each colour of the creature. */
    // CR 702.11d, 601.2f
    @Test
    fun wyrmfireBladeEquipsCheaply() = scenario {
        alice.battlefield(wyrmfireBlade, multicolor, "Plains", "Plains", "Plains")
        alice.activates(wyrmfireBlade, target = multicolor, paying = listOf("Plains", "Plains#1"))
        playOut()
        expect { multicolor.power shouldBe 4 }
    }

    @Test
    fun wyrmfireBladeHexproofFromMonocolored() = scenario {
        alice.battlefield(wyrmfireBlade, bear, "Plains", "Plains", "Plains", "Plains")
        bob.battlefield("Mountain")
        bob.hand(Plain.shock)
        alice.activates(wyrmfireBlade, target = bear)
        playOut()
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(Plain.shock, target = bear) }
    }

    /** {1}{B}, a creature card exiled from the graveyard: a Spirit. */
    @Test
    fun greatDawnwallCityMakesSpirits() = scenario {
        alice.battlefield(greatDawnwallCity, "Swamp", "Swamp")
        alice.graveyard(ogre)
        alice.activates(greatDawnwallCity, ability = 1, choosing = listOf(ogre))
        playOut()
        expect { alice.battlefield has "Spirit Token" }
    }

    /** A graveyard card to the bottom of its owner's library. */
    @Test
    fun jadeForgedSentinelTucks() = scenario {
        alice.battlefield(jadeForgedSentinel, "Plains", "Plains")
        bob.graveyard(giant)
        alice.activates(jadeForgedSentinel, target = giant)
        playOut()
        expect { bob.library has giant }
    }

    /** {3}{G}: surveil 2. */
    @Test
    fun riverbankVillageSurveils() = scenario {
        alice.battlefield(riverbankVillage, "Forest", "Forest", "Forest", "Forest")
        alice.library(ogre, giant)
        alice.activates(riverbankVillage, ability = 1)
        playOut(ogre, giant)
        expect { alice.graveyard.size shouldBe 2 }
    }

    /** {U}: the next spell this turn can't be countered. */
    @Test
    fun fogriseVillageProtects() = scenario {
        alice.battlefield(fogriseVillage, "Island", "Forest", "Forest")
        alice.hand(bear)
        bob.battlefield("Island", "Island")
        bob.hand(Plain.counterspell)
        alice.activates(fogriseVillage, ability = 1, paying = listOf("Island"))
        playOut()
        alice.casts(bear)
        alice.passes()
        bob.casts(Plain.counterspell, target = bear)
        playOut()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    /** Cast, it exiles a coloured permanent; a colorless spell exiles another; +2: 3 life and a card. */
    // CR 105.2c
    @Test
    fun orvaxExilesColoredPermanents() = scenario {
        alice.battlefield(*Array(10) { "Plains" })
        alice.hand(orvaxEyeOfTheTempest, Plain.golem)
        bob.battlefield(ogre, giant)
        alice.casts(orvaxEyeOfTheTempest)
        playOut(ogre)
        expect { bob.exile has ogre }
        alice.casts(Plain.golem)
        playOut(giant)
        expect { bob.exile has giant }
    }

    @Test
    fun orvaxPlusTwo() = scenario {
        alice.battlefield(orvaxEyeOfTheTempest, counters = listOf(arcanum.api.CounterKind.Loyalty to 7))
        alice.library(ogre)
        alice.activates(orvaxEyeOfTheTempest, ability = 0)
        playOut()
        expect {
            alice.life shouldBe 23
            alice.hand has ogre
        }
    }

    /** As it enters, a player mills two and you gain 2 life. */
    @Test
    fun watcherOfTheRoadsideMills() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(watcherOfTheRoadside)
        bob.library(ogre, giant)
        alice.casts(watcherOfTheRoadside)
        playOut("bob")
        expect {
            bob.graveyard.size shouldBe 2
            alice.life shouldBe 22
        }
    }
}
