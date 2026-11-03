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
 * Tarkir: Dragonstorm cards of parser chunks 11–12 in play: the black and red cards.
 */
class TdmBlackAndRedCardsScenariosTest {
    private val houselordDevotee = Cards.invent("Houselord Devotee", "{1}{B}", "Creature — Dog Cleric", """
        {1}: Add {W}, {B}, or {G}. Activate only once each turn.
        {2}{B}: Return this card from your graveyard to your hand.
    """, "2/1")
    private val hostileParley = Cards.invent("Hostile Parley", "{2}{B}", "Sorcery", "Target opponent reveals their hand. You choose a nonland card from it and exile that card. Put a +1/+1 counter on up to one target creature you control.")
    private val vanyasLegacy = Cards.invent("Vanya's Legacy", "{1}{B}", "Instant", "Target creature you control gains deathtouch and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)")
    private val marshBloodflies = Cards.invent("Marsh Bloodflies", "{1}{B}", "Creature — Insect", """
        Flying
        Whenever this creature attacks, if you control a creature with a counter on it, each opponent loses 1 life.
    """, "1/2")
    private val direRecourse = Cards.invent("Dire Recourse", "{B}", "Instant", "Target creature gets +1/-1 until end of turn. When it dies under your control this turn, draw two cards.")
    private val fenmireRakshasa = Cards.invent("Fenmire Rakshasa", "{4}{B}{B}", "Creature — Demon", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters, target creature an opponent controls gets -2/-2 until end of turn and target creature you control gets +2/+2 until end of turn.
    """, "5/5")
    private val thousandScarVeteran = Cards.invent("Thousand-Scar Veteran", "{3}{B}", "Creature — Zombie Warrior", """
        As long as there are three or more different kinds of counters among creatures you control, this creature gets +2/+4.
        You may cast this card from your graveyard. If you do, it enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
    """, "4/2")
    private val caravanSkirmish = Cards.invent("Caravan Skirmish", "{3}{B}", "Sorcery", "Destroy target creature. Create two 1/1 red Warrior creature tokens. They gain haste until end of turn. Sacrifice them at the beginning of the next end step.")
    private val ssessaRegentOfTheFen = Cards.invent("Ssessa, Regent of the Fen", "{1}{B}", "Legendary Creature — Zombie Snake Warlock", "{T}, Sacrifice a creature you control with mana value X other than Ssessa: Return target creature card with mana value X plus 1 from your graveyard to the battlefield. Activate only as a sorcery.", "1/3")
    private val calculatedBetrayal = Cards.invent("Calculated Betrayal", "{1}{B}", "Sorcery", "Target opponent exiles a creature they control and their graveyard.")
    private val theGravecourtRite = Cards.invent("The Gravecourt Rite", "{B}{B}{B}", "Legendary Enchantment", """
        Creature spells you cast cost {2} less to cast.
        Whenever a creature you control enters, if you cast it, destroy that creature, then create a 2/2 black Zombie Druid creature token.
    """)
    private val unearthedGravedigger = Cards.invent("Unearthed Gravedigger", "{1}{B}", "Creature — Human Warrior", "{2}, Sacrifice another creature: Put a +1/+1 counter on this creature.", "2/2")
    private val uprootedAncestor = Cards.invent("Uprooted Ancestor", "{2}{B}", "Creature — Spirit Cleric", """
        Flash
        {1}, Sacrifice another creature: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it.)
    """, "3/2")
    private val keeningOfWar = Cards.invent("Keening of War", "{2}{B}", "Instant", """
        Choose one —
        • Creatures target opponent controls get -1/-1 until end of turn.
        • Return up to two target creature cards from your graveyard to your hand.
    """)
    private val fittingPrice = Cards.invent("Fitting Price", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature.
        Exile target creature or planeswalker.
    """)
    private val barrowTombguard = Cards.invent("Barrow Tombguard", "{2}{B}", "Creature — Human Warrior", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever a creature you control with a counter on it deals combat damage to a player, you draw a card and you lose 1 life.
    """, "2/3")
    private val flameEdgeForm = Cards.invent("Flame-Edge Form", "{1}{R}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, enchanted creature gains first strike until end of turn.
        Enchanted creature gets +2/+0.
    """)
    private val passingEffigy = Cards.invent("Passing Effigy", "{R}", "Creature — Elemental", """
        Haste
        At the beginning of your end step, return this creature to its owner's hand. (Return it only if it's on the battlefield.)
        {2}{R}: This creature gets +2/+0 until end of turn.
    """, "2/2")
    private val prismaticTiger = Cards.invent("Prismatic Tiger", "{4}{R}", "Creature — Cat", "When this creature enters, if you cast it, add {W}{U}{B}{R}{G}.", "3/4")
    private val painstakingArtisan = Cards.invent("Painstaking Artisan", "{3}{R}", "Creature — Djinn Artificer", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "3/3")
    private val liriensReproof = Cards.invent("Lirien's Reproof", "{4}{R}", "Instant", "Lirien's Reproof deals 5 damage to target creature. Add {U}{R}{W}. If that creature would die this turn, exile it instead.")
    private val crushingSurge = Cards.invent("Crushing Surge", "{2}{R}", "Instant", """
        Choose one or both —
        • Crushing Surge deals 3 damage to target creature.
        • Destroy target noncreature artifact.
    """)
    private val rescuingLeopard = Cards.invent("Rescuing Leopard", "{2}{R}", "Creature — Cat", "Whenever this creature becomes tapped, you may discard a card. If you do, draw a card.", "4/2")
    private val graspTheMoment = Cards.invent("Grasp the Moment", "{2}{R}", "Instant", """
        Choose one —
        • Exile the top two cards of your library. Until the end of your next turn, you may play those cards.
        • Up to two target creatures each get +2/+1 until end of turn.
    """)
    private val startlingSharpshooter = Cards.invent("Startling Sharpshooter", "{1}{R}", "Creature — Human Archer", """
        Reach
        Whenever another creature you control enters, this creature deals 1 damage to target opponent.
    """, "1/3")
    private val peakIntimidator = Cards.invent("Peak Intimidator", "{3}{R}", "Creature — Yeti", """
        Reach
        When this creature enters, target creature can't block this turn.
    """, "4/3")
    private val dusklightStrikemaster = Cards.invent("Dusklight Strikemaster", "{1}{R}", "Creature — Human Monk", """
        {T}: Add {R}.
        {2}{R}, {T}, Sacrifice this creature: It deals 6 damage to target creature with flying.
    """, "3/1")
    private val ovraLightbreaker = Cards.invent("Ovra Lightbreaker", "{2}{R}", "Legendary Creature — Orc Wizard", """
        Haste
        When Ovra Lightbreaker enters, discard up to two cards, then draw that many cards.
        Whenever Ovra Lightbreaker attacks, if there are seven or more cards in your graveyard, exile a card at random from your graveyard. You may play that card this turn.
    """, "3/3")
    private val pairedBolt = Cards.invent("Paired Bolt", "{1}{R}", "Instant", "Paired Bolt deals 2 damage divided as you choose among one or two targets.")
    private val underheelScrappers = Cards.invent("Underheel Scrappers", "{2}{R}", "Creature — Goblin Warrior", """
        When this creature enters, create a 1/1 red Goblin creature token.
        {1}, {T}: Target creature you control with power 2 or less can't be blocked this turn.
    """, "1/2")
    private val mercilessBoltcaster = Cards.invent("Merciless Boltcaster", "{2}{R}", "Creature — Ogre Wizard", "When this creature enters, it deals 5 damage to target creature an opponent controls that was dealt damage this turn.", "3/3")

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1

    // ---- Chunk 11: black

    /** From the graveyard, {2}{B}: back to the hand. */
    // CR 113.6m
    @Test
    fun houselordDevoteeReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(houselordDevotee)
        alice.activates(houselordDevotee, ability = 1)
        playOut()
        expect { alice.hand has houselordDevotee }
    }

    /** A nonland card from the revealed hand exiled; a counter on a creature. */
    // CR 701.20a
    @Test
    fun hostileParleyExilesFromTheHand() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(hostileParley)
        bob.hand(ogre, "Forest")
        alice.casts(hostileParley, targets = listOf("bob", bear))
        playOut(ogre)
        expect {
            bob.exile has ogre
            bear.counters(p1p1) shouldBe 1
        }
    }

    /** Deathtouch and indestructible for a creature. */
    @Test
    fun vanyasLegacyProtects() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(vanyasLegacy)
        alice.casts(vanyasLegacy, target = bear)
        playOut()
        expect {
            bear hasKeyword Keyword.Deathtouch
            bear hasKeyword Keyword.Indestructible
        }
    }

    /** Attacking while a creature has a counter: each opponent loses 1. */
    // CR 603.4
    @Test
    fun marshBloodfliesDrain() = scenario {
        alice.battlefield(marshBloodflies)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(marshBloodflies)
        playOut()
        expect { bob.life shouldBe 19 }
    }

    /** +1/-1; when it dies under your control this turn, two cards. */
    // CR 603.7c
    @Test
    fun direRecourseDrawsOnDeath() = scenario {
        alice.battlefield("Swamp", Plain.elf)
        alice.hand(direRecourse)
        alice.library(ogre, giant)
        alice.casts(direRecourse, target = Plain.elf)
        playOut()
        expect {
            alice.graveyard has Plain.elf
            alice.hand.size shouldBe 2
        }
    }

    /** One creature -2/-2, another +2/+2. */
    @Test
    fun fenmireRakshasaSwings() = scenario {
        alice.battlefield(*Array(6) { "Swamp" }, bear)
        alice.hand(fenmireRakshasa)
        bob.battlefield(Plain.elf)
        alice.casts(fenmireRakshasa)
        playOut(Plain.elf, bear)
        expect {
            bob.graveyard has Plain.elf
            bear.power shouldBe 4
        }
    }

    /** Three kinds of counters among creatures: +2/+4; cast from the graveyard, it enters with a finality counter. */
    // CR 122.1
    @Test
    fun thousandScarVeteranCountsKinds() = scenario {
        alice.battlefield(thousandScarVeteran)
        alice.battlefield(bear, counters = listOf(p1p1 to 1, arcanum.api.CounterKind.Stun to 1))
        alice.battlefield(ogre, counters = listOf(arcanum.api.CounterKind.Keyword(Keyword.Flying.kind) to 1))
        expect { thousandScarVeteran.power shouldBe 6 }
    }

    @Test
    fun thousandScarVeteranFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(thousandScarVeteran)
        alice.casts(thousandScarVeteran)
        playOut()
        expect { thousandScarVeteran.counters(arcanum.api.CounterKind.Finality) shouldBe 1 }
    }

    /** A creature destroyed; two hasty Warriors sacrificed at the end step. */
    @Test
    fun caravanSkirmishRaids() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(caravanSkirmish)
        bob.battlefield(giant)
        alice.casts(caravanSkirmish, target = giant)
        playOut()
        expect {
            bob.graveyard has giant
            "Warrior Token" hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.battlefield lacks "Warrior Token" }
    }

    /** X = 2: a mana value 2 creature sacrificed for a mana value 3 creature card. */
    // CR 107.3a
    @Test
    fun ssessaTradesUp() = scenario {
        alice.battlefield(ssessaRegentOfTheFen, bear)
        alice.graveyard(ogre)
        alice.activates(ssessaRegentOfTheFen, x = 2, target = ogre, choosing = listOf(bear))
        playOut()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            alice.graveyard has bear
        }
    }

    /** The opponent exiles a creature of theirs and their graveyard. */
    @Test
    fun calculatedBetrayalExiles() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(calculatedBetrayal)
        bob.battlefield(giant)
        bob.graveyard(ogre)
        alice.casts(calculatedBetrayal, target = "bob")
        playOut(giant)
        expect {
            bob.exile has giant
            bob.exile has ogre
        }
    }

    /** Creature spells {2} cheaper; a creature cast enters and is destroyed for a Zombie Druid. */
    // CR 603.4
    @Test
    fun theGravecourtRiteTradesCreatures() = scenario {
        alice.battlefield(theGravecourtRite, "Mountain")
        alice.hand(ogre)
        alice.casts(ogre, paying = listOf("Mountain"))
        playOut()
        expect {
            alice.graveyard has ogre
            alice.battlefield has "Zombie Druid Token"
        }
    }

    /** {2}, another creature sacrificed: a counter. */
    @Test
    fun unearthedGravediggerGrows() = scenario {
        alice.battlefield(unearthedGravedigger, bear, "Swamp", "Swamp")
        alice.activates(unearthedGravedigger, choosing = listOf(bear))
        playOut()
        expect { unearthedGravedigger.counters(p1p1) shouldBe 1 }
    }

    /** {1}, another creature sacrificed: indestructible, then tapped. */
    @Test
    fun uprootedAncestorShields() = scenario {
        alice.battlefield(uprootedAncestor, bear, "Swamp")
        alice.activates(uprootedAncestor, choosing = listOf(bear))
        playOut()
        expect {
            uprootedAncestor hasKeyword Keyword.Indestructible
            uprootedAncestor.tapped shouldBe true
        }
    }

    /** The opponent's creatures -1/-1; or two creature cards back. */
    @Test
    fun keeningOfWarShrinks() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(keeningOfWar)
        bob.battlefield(Plain.elf, ogre)
        alice.casts(keeningOfWar, modes = listOf(0), target = "bob")
        playOut()
        expect {
            bob.graveyard has Plain.elf
            bear.power shouldBe 2
        }
    }

    @Test
    fun keeningOfWarReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(keeningOfWar)
        alice.graveyard(ogre, giant)
        alice.casts(keeningOfWar, modes = listOf(1), targets = listOf(ogre, giant))
        playOut()
        expect { alice.hand.size shouldBe 2 }
    }

    /** A creature sacrificed to exile a creature. */
    @Test
    fun fittingPriceExiles() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(fittingPrice)
        bob.battlefield(giant)
        alice.casts(fittingPrice, target = giant, choosing = listOf(bear))
        playOut()
        expect { bob.exile has giant }
    }

    /** A countered creature hitting a player: a card and 1 life. */
    @Test
    fun barrowTombguardDraws() = scenario {
        alice.battlefield(barrowTombguard)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.library(ogre)
        hits(bear)
        expect {
            alice.hand has ogre
            alice.life shouldBe 19
        }
    }

    // ---- Chunk 12: red

    /** Flash: first strike as it enters, +2/+0. */
    @Test
    fun flameEdgeFormEmpowers() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(flameEdgeForm)
        alice.casts(flameEdgeForm, target = bear)
        playOut()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.FirstStrike
        }
    }

    /** At its controller's end step, back to the hand. */
    @Test
    fun passingEffigyReturns() = scenario {
        alice.battlefield(passingEffigy)
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.hand has passingEffigy }
    }

    /** Cast, it adds one mana of each colour. */
    // CR 603.4
    @Test
    fun prismaticTigerAddsMana() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(prismaticTiger)
        alice.casts(prismaticTiger)
        playOut()
        expect { alice.pool.size shouldBe 5 }
    }

    /** A Treasure as it enters; prowess. */
    @Test
    fun painstakingArtisanMakesTreasure() = scenario {
        alice.battlefield(*Array(4) { "Mountain" })
        alice.hand(painstakingArtisan)
        alice.casts(painstakingArtisan)
        playOut()
        expect { alice.battlefield has "Treasure Token" }
    }

    /** 5 damage, {U}{R}{W} added, and the creature is exiled if it would die. */
    // CR 614.1a
    @Test
    fun liriensReproofExiles() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(liriensReproof)
        bob.battlefield(giant)
        alice.casts(liriensReproof, target = giant)
        playOut()
        expect {
            bob.exile has giant
            alice.pool.size shouldBe 3
        }
    }

    /** Both modes: 3 damage and a noncreature artifact destroyed. */
    @Test
    fun crushingSurgeBoth() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(crushingSurge)
        bob.battlefield(ogre, Plain.idol)
        alice.casts(crushingSurge, modes = listOf(0, 1), targets = listOf(ogre, Plain.idol))
        playOut()
        expect {
            bob.graveyard has ogre
            bob.graveyard has Plain.idol
        }
    }

    /** Becoming tapped, it rummages. */
    @Test
    fun rescuingLeopardRummages() = scenario {
        alice.battlefield(rescuingLeopard)
        alice.hand(bear)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rescuingLeopard)
        playOut(bear)
        expect { alice.hand has ogre }
    }

    /** Two cards exiled, playable until the end of the next turn; or two creatures pumped. */
    @Test
    fun graspTheMomentExiles() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(graspTheMoment)
        alice.library("Forest", ogre)
        alice.casts(graspTheMoment, modes = listOf(0))
        playOut()
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    @Test
    fun graspTheMomentPumps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear, ogre)
        alice.hand(graspTheMoment)
        alice.casts(graspTheMoment, modes = listOf(1), targets = listOf(bear, ogre))
        playOut()
        expect {
            bear.power shouldBe 4
            ogre.toughness shouldBe 4
        }
    }

    /** Another creature entering: 1 damage to an opponent. */
    @Test
    fun startlingSharpshooterPings() = scenario {
        alice.battlefield(startlingSharpshooter, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        playOut("bob")
        expect { bob.life shouldBe 19 }
    }

    /** As it enters, a creature can't block. */
    @Test
    fun peakIntimidatorStopsABlocker() = scenario {
        alice.battlefield(*Array(4) { "Mountain" }, bear)
        alice.hand(peakIntimidator)
        bob.battlefield(giant)
        alice.casts(peakIntimidator)
        playOut(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(giant to bear) }
    }

    /** Sacrificed: 6 damage to a flyer. */
    @Test
    fun dusklightStrikemasterShootsAFlyer() = scenario {
        alice.battlefield(dusklightStrikemaster, "Mountain", "Mountain", "Mountain")
        bob.battlefield(dragon)
        alice.activates(dusklightStrikemaster, ability = 1, target = dragon)
        playOut()
        expect { bob.graveyard has dragon }
    }

    /** As it enters, two cards discarded and two drawn; attacking with seven graveyard cards, one exiled at random to play. */
    @Test
    fun ovraLightbreakerRummages() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(ovraLightbreaker, bear, Plain.elf)
        alice.library(ogre, giant)
        alice.casts(ovraLightbreaker)
        playOut(bear, Plain.elf)
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun ovraLightbreakerPlaysFromTheGraveyard() = scenario {
        alice.battlefield(ovraLightbreaker)
        alice.graveyard("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ovraLightbreaker)
        playOut()
        expect { alice.exile.size shouldBe 1 }
    }

    /** 2 damage divided between two targets. */
    // CR 601.2d
    @Test
    fun pairedBoltSplits() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(pairedBolt)
        bob.battlefield(Plain.elf)
        alice.casts(pairedBolt, targets = listOf(Plain.elf, "bob"))
        playOut()
        expect {
            bob.graveyard has Plain.elf
            bob.life shouldBe 19
        }
    }

    /** A Goblin as it enters; a small creature made unblockable. */
    @Test
    fun underheelScrappersSneak() = scenario {
        alice.battlefield(underheelScrappers, bear, "Mountain")
        bob.battlefield(giant)
        alice.activates(underheelScrappers, target = bear)
        playOut()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(giant to bear) }
    }

    /** As it enters, 5 damage to a creature that was dealt damage this turn. */
    @Test
    fun mercilessBoltcasterFinishes() = scenario {
        alice.battlefield(*Array(4) { "Mountain" })
        alice.hand(Plain.bolt, mercilessBoltcaster)
        bob.battlefield(giant)
        alice.casts(Plain.bolt, target = giant, paying = listOf("Mountain"))
        playOut()
        alice.casts(mercilessBoltcaster)
        playOut(giant)
        expect { bob.graveyard has giant }
    }
}
