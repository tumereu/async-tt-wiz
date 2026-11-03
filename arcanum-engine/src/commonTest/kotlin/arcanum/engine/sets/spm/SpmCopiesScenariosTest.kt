package arcanum.engine.sets.spm

import arcanum.api.CounterKind
import arcanum.api.Keyword
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

/** Marvel's Spider-Man cards in play that copy and play from elsewhere: tokens and spells copied without being legendary, a copy of a
 * graveyard card that exiles it, another card's abilities given away, an ability copied, the legend rule for Spiders, cards cast from
 * the top of the library and from exile (one paid with life, one with any mana), first-or-second-card triggers, a cast restriction by
 * turn count. */
class SpmCopiesScenariosTest {
    private val doppel = Cards.invent("Doubting Doppelgangers", "{4}{U}{U}", "Enchantment",
        "Whenever a nontoken creature you control deals combat damage to a player, create a token that's a copy of it, except it isn't legendary.")
    private val hyena = Cards.invent("Hyena, Gene Splicer", "{G}{U}", "Legendary Creature — Human Scientist Villain", """
        Trample
        Whenever you cast a creature spell with mana value equal to Hyena's power, copy that spell, except the copy isn't legendary. Then put a +1/+1 counter on Hyena. (The copy becomes a token.)
    """, "1/1")
    private val usurper = Cards.invent("Usurper of the Mask", "{2}{U}{B}", "Legendary Creature — Spider Human Hero",
        "Mind Swap — You may have Usurper of the Mask enter as a copy of any creature card in a graveyard, except his name is Usurper of the Mask and he's a 4/4 Spider Human Hero in addition to his other types. When you do, exile that card.", "4/4")
    private val inkbound = Cards.invent("Inkbound Webslinger", "{2}{U/B}", "Legendary Creature — Symbiote Spider Hero", """
        Whenever this creature deals combat damage to a player, look at that many cards from the top of your library. Put one of them into your hand and the rest into your graveyard.
        Find New Host — {2}{U/B}, Exile this card from your graveyard: Put a +1/+1 counter on target creature you control. It gains this card's other abilities. Activate only as a sorcery.
    """, "2/4")
    private val shutterbox = Cards.invent("Freelancer's Shutterbox", "{1}", "Artifact", """
        This artifact enters with three film counters on it.
        {2}, {T}, Remove a film counter from this artifact: Copy target activated or triggered ability you control. You may choose new targets for the copy.
    """)
    private val weave = Cards.invent("Weave of Worlds", "{3}{R}{R}", "Enchantment", """
        The "legend rule" doesn't apply to Spiders you control.
        Whenever you cast a spell from anywhere other than your hand, you may copy it. If you do, you may choose new targets for the copy. If the copy is a permanent spell, it gains haste. Do this only once each turn.
    """)
    private val tinker = Cards.invent("Madame Grapple, Brilliant Tinker", "{U}", "Legendary Creature — Human Scientist Villain", """
        Whenever you draw your first or second card each turn, put an ingenuity counter on Madame Grapple.
        {T}: You may cast an artifact spell from your hand with mana value less than or equal to the number of ingenuity counters on Madame Grapple without paying its mana cost.
    """, "0/2")
    private val oracle = Cards.invent("Oracle of Threads, Seer", "{4}{U}{U}", "Legendary Creature — Mutant Advisor", """
        You may look at the top card of your library any time.
        You may cast Spider spells and noncreature spells from the top of your library.
        Whenever you attack, you may mill a card. (You may put the top card of your library into your graveyard.)
    """, "4/4")
    private val prowl = Cards.invent("Velvet Prowl, Sly Burglar", "{3}{B}{B}", "Legendary Creature — Human Rogue Villain",
        "When Velvet Prowl enters, look at the top nine cards of target opponent's library, exile two of them face down, then put the rest on the bottom of their library in a random order. You may play the exiled cards for as long as they remain exiled. Mana of any type can be spent to cast spells this way.", "2/3")
    private val inkspider = Cards.invent("Inkspider, Without Mercy", "{3}{B}{B}", "Legendary Creature — Symbiote Spider Hero", """
        Deathtouch, lifelink
        Whenever Inkspider attacks, until end of turn, you may look at the top card of your library any time and you may play cards from the top of your library. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost.
    """, "4/4")
    private val webdancer = Cards.invent("Phantom Webdancer", "{2}{U}{R}{W}", "Legendary Creature — Spider Human Hero", """
        Flying, vigilance, haste
        Whenever you play a land from exile or cast a spell from exile, put a +1/+1 counter on Phantom Webdancer.
        Remove two counters from Phantom Webdancer: Exile the top card of your library. You may play that card this turn.
    """, "4/4")
    private val rivals = Cards.invent("Lesser Rivals of the Weave", "{2}{R}", "Creature — Human Rogue Villain", """
        Trample
        Whenever you cast a spell with mana value 4 or greater, you may exile the top card of your library. If you do, you may play that card until you exile another card with this creature.
    """, "3/3")
    private val loomwright = Cards.invent("Loomwright and Hatchling", "{R}", "Legendary Creature — Spider Human Hero", """
        Whenever you attack with two or more Spiders, put a +1/+1 counter on Loomwright and Hatchling.
        Whenever Loomwright and Hatchling deals 4 or more damage, exile the top card of your library. Until the end of your next turn, you may play that card.
    """, "1/2")
    private val tomorrow = Cards.invent("Webrunner of Tomorrow", "{U}{R}", "Legendary Creature — Spider Human Hero", """
        From the Future — You can't cast Webrunner of Tomorrow during your first, second, or third turns of the game.
        Double strike, vigilance
        At the beginning of your end step, if you've played a land or cast a spell this turn from anywhere other than your hand, Webrunner of Tomorrow deals damage equal to his power to any target.
    """, "2/3")
    private val dread = Cards.invent("Gremlin's Lingering Dread", "{1}{R}", "Enchantment", """
        Unreliable Visions — At the beginning of your first main phase, discard a card. If you do, draw a card.
        Undying Vengeance — Whenever you play a land or cast a spell from anywhere other than your hand, this enchantment deals 1 damage to each opponent.
    """)
    private val chronometer = Cards.invent("Crossworld Chronometer", "{4}", "Artifact", """
        When this artifact enters, exile the top two cards of your library. Until the end of your next turn, you may play those cards.
        {T}: Add two mana in any combination of colors. Spend this mana only to cast spells from exile.
    """)
    private val trinket = Cards.invent("Brass Trinket", "{2}", "Artifact", "")
    private val jolt = Cards.invent("Static Jolt", "{R}", "Instant", "Static Jolt deals 2 damage to any target.")
    private val bear = spmBear
    private val ogre = spmOgre
    private val giant = spmGiant
    private val spider = spmSpider
    private val hero = spmHero

    /** Combat damage by a nontoken creature makes a token copy of it that isn't legendary. */
    // CR 707.9b, 111.4
    @Test
    fun combatDamageMakesANonlegendaryTokenCopy() = scenario {
        alice.battlefield(doppel, hero)
        alice.library(bear, bear)
        bob.library(bear, bear)
        turn(number = 3, step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hero)
        allPass()
        if (state.priority.holder == null) bob.blocks()
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        val tokens = state.battlefield.filter { state.obj(it)!!.token }
        assertEquals(1, tokens.size)
        assertTrue(arcanum.api.Supertype.Legendary !in view()[tokens.single()]!!.characteristics!!.supertypes)
    }

    /** A creature spell with mana value equal to its power is copied (the copy isn't legendary), then a counter on it. */
    // CR 707.10, 707.10c, 707.9b
    @Test
    fun matchingManaValueIsCopied() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(hyena, counters = listOf(p1p1 to 1))
        alice.hand(hero)
        alice.casts(hero, paying = listOf("Plains", "Plains"))
        resolveAll()
        expect { hyena.counters(p1p1) shouldBe 2 }
        val heroes = state.battlefield.filter { view()[it]!!.characteristics!!.name == hero }
        assertEquals(2, heroes.size)
    }

    /** Entering as a copy of a creature card in a graveyard: its name and a 4/4 Spider Human Hero, and that card is exiled. */
    // CR 707.9b, 614.12, 603.12
    @Test
    fun copyOfAGraveyardCardExilesIt() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp")
        alice.hand(usurper)
        bob.graveyard(doppelCreature)
        alice.casts(usurper)
        resolveAll()
        drive(
            { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(doppelCreature) },
            { d: Decision -> d is Decision.May } to { yes() },
        )
        resolveAll()
        val id = card(usurper, zone = Zone.Battlefield)
        val c = view()[id]!!.characteristics!!
        assertEquals(usurper, c.name)
        assertEquals(4, c.power)
        assertTrue(c.keywords.any { it is Keyword.Flying })
        assertTrue(Subtype("Spider") in c.subtypes)
        expect { bob.exile has doppelCreature }
    }

    private val doppelCreature = Cards.invent("Graveyard Wyvern", "{4}{U}", "Creature — Drake", "Flying", "3/3")

    /** Exiled from the graveyard, it gives a creature a counter and its other ability for good. */
    // CR 113.10, 611.2c
    @Test
    fun findNewHostGivesItsAbility() = scenario {
        alice.battlefield("Island", "Island", "Swamp", bear)
        alice.graveyard(inkbound)
        alice.library(ogre, ogre, ogre, giant)
        bob.library(ogre, ogre)
        turn(number = 3)
        alice.activates(inkbound, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.exile has inkbound
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        untilStep(TurnStep.PostcombatMain, { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(ogre) })
        expect { alice.hand has ogre }
        assertEquals(2, alice.graveyard.size)
    }

    /** A triggered ability copied by the artifact resolves twice. */
    // CR 707.10
    @Test
    fun abilityCopied() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", pinger)
        alice.battlefield(shutterbox, counters = listOf(CounterKind.Named("film") to 3))
        alice.activates(pinger)
        alice.activates(shutterbox)
        drive(
            { d: Decision -> d is Decision.Targets } to { answer(Answer.Targets(listOf((pending as Decision.Targets).options.first()))) },
            { d: Decision -> d is Decision.ManaPayment } to { answer(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion)) },
        )
        assertEquals(18, bob.life)
        expect { shutterbox.counters(CounterKind.Named("film")) shouldBe 2 }
    }

    private val pinger = Cards.invent("Spark Turret", "{1}", "Artifact", "{T}: This artifact deals 1 damage to each opponent.")

    /** Two legendary Spiders stay; a spell cast from elsewhere than the hand is copied, the permanent copy with haste. */
    // CR 704.5j, 707.10, 707.10c
    @Test
    fun legendRuleAndCopiesFromElsewhere() = scenario {
        alice.battlefield(weave, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.battlefield(legendarySpider, legendarySpider)
        alice.hand(tryst, bolt)
        alice.library(bear, bear)
        expect { alice.battlefield.names.count { it == legendarySpider } shouldBe 2 }
        bob.battlefield(giant)
        alice.casts(tryst, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        if (pending != null) alice.chooses(bolt)
        resolveAll()
        alice.casts(bolt, target = giant, paying = listOf("Mountain#2", "Mountain#3"))
        drive({ d: Decision -> d is Decision.May } to { yes() }, { d: Decision -> d is Decision.Targets } to { targets(giant) })
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    private val legendarySpider = Cards.invent("Twin Weaver, Echoed", "{1}{R}", "Legendary Creature — Spider Hero", "", "2/2")
    private val tryst = Cards.invent("Moonlit Tryst", "{1}{R}", "Sorcery", "Discard a card, then draw two cards.")
    private val bolt = Cards.invent("Galvanic Lash", "{2}{R}", "Sorcery", """
        Galvanic Lash deals 4 damage to target creature.
        Mayhem {1}{R} (You may cast this card from your graveyard for {1}{R} if you discarded it this turn. Timing rules still apply.)
    """)

    /** The first and second cards drawn each turn put counters; the third doesn't; then an artifact cast free. */
    // CR 121.2, 118.9
    @Test
    fun firstOrSecondDrawAndFreeArtifact() = scenario {
        alice.battlefield(tinker, "Island", "Island", "Island")
        alice.hand(trinket, threeCards)
        alice.library(bear, bear, bear, bear)
        alice.casts(threeCards)
        resolveAll()
        expect { tinker.counters(CounterKind.Named("ingenuity")) shouldBe 2 }
        alice.activates(tinker)
        drive({ d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(trinket) }, { d: Decision -> d is Decision.May } to { yes() })
        resolveAll()
        expect { alice.battlefield has trinket }
    }

    private val threeCards = Cards.invent("Triple Study", "{2}{U}", "Sorcery", "Draw three cards.")

    /** A noncreature spell is cast from the top of the library; a non-Spider creature isn't. */
    // CR 601.2a, 401.5
    @Test
    fun castFromTheTopOfTheLibrary() = scenario {
        alice.battlefield(oracle, "Mountain")
        alice.library(jolt, bear)
        alice.casts(jolt, target = "bob")
        resolveAll()
        assertEquals(18, bob.life)
        assertTrue(runCatching { alice.casts(bear) }.isFailure)
    }

    /** Two of the opponent's cards exiled face down; one of them cast with mana of any type. */
    // CR 406.3, 609.4b
    @Test
    fun stolenCardsCastWithAnyMana() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(prowl)
        bob.library(jolt, bear, ogre, ogre, ogre, ogre, ogre, ogre, ogre, giant)
        alice.casts(prowl, paying = listOf("Swamp", "Swamp", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        drive(
            { d: Decision -> d is Decision.Targets } to { targets("bob") },
            { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(jolt, bear) },
        )
        expect { bob.exile has jolt }
        alice.casts(jolt, target = "bob", paying = listOf("Plains"))
        resolveAll()
        assertEquals(18, bob.life)
        expect { bob.library has giant }
    }

    /** Attacking, a spell from the top of the library is cast for life equal to its mana value. */
    // CR 118.9
    @Test
    fun castFromTheTopForLife() = scenario {
        alice.battlefield(inkspider)
        alice.library(jolt, bear, bear)
        bob.library(bear, bear)
        turn(number = 3)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(inkspider)
        resolveAll()
        alice.casts(jolt, target = "bob")
        resolveAll()
        assertEquals(19, alice.life)
        assertEquals(18, bob.life)
    }

    /** Casting a spell from exile puts a counter on it; its counters exile a card to play this turn. */
    // CR 601.2a, 305.1
    @Test
    fun castFromExileCounts() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(webdancer, counters = listOf(p1p1 to 2))
        alice.library(jolt)
        alice.activates(webdancer)
        resolveAll()
        expect { alice.exile has jolt }
        alice.casts(jolt, target = "bob")
        resolveAll()
        expect { webdancer.counters(p1p1) shouldBe 1 }
        expect { webdancer.power shouldBe 5 }
        assertEquals(18, bob.life)
    }

    /** A big spell exiles a card that may be played until the next one replaces it. */
    // CR 607.2a
    @Test
    fun exiledCardPlayableUntilTheNext() = scenario {
        alice.battlefield(rivals, "Forest", "Forest", "Forest", "Forest", "Forest", "Mountain")
        alice.hand(giant)
        alice.library(jolt, bear)
        alice.casts(giant, paying = listOf("Forest", "Forest", "Forest", "Forest", "Forest"))
        drive({ d: Decision -> d is Decision.May } to { yes() })
        resolveAll()
        expect { alice.exile has jolt }
        alice.casts(jolt, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        assertEquals(18, bob.life)
    }

    /** Attacking with two Spiders puts a counter; dealing 4 or more damage at once exiles a card to play. */
    // CR 508.1, 603.2c
    @Test
    fun spidersAttackAndBigDamage() = scenario {
        alice.battlefield(spider)
        alice.battlefield(loomwright, counters = listOf(p1p1 to 2))
        alice.library(jolt, bear, bear)
        bob.library(bear, bear)
        turn(number = 3)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(loomwright, spider)
        resolveAll()
        expect { loomwright.counters(p1p1) shouldBe 3 }
        if (state.priority.holder == null) bob.blocks()
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        assertEquals(14, bob.life)
        expect { alice.exile has jolt }
    }

    /** The cast restriction: not during your first three turns; later it may be. */
    // CR 601.3
    @Test
    fun cantCastDuringTheFirstTurns() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(tomorrow)
        alice.library(bear, bear, bear, bear, bear)
        bob.library(bear, bear, bear, bear, bear)
        assertTrue(runCatching { alice.casts(tomorrow) }.isFailure)
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        alice.casts(tomorrow)
        resolveAll()
        expect { tomorrow.zone shouldBe Zone.Battlefield }
    }

    /** A spell cast from elsewhere than the hand: 1 damage to each opponent; at the end step, damage equal to its power. */
    // CR 601.2a
    @Test
    fun castFromElsewhereHurts() = scenario {
        alice.battlefield(dread, tomorrow, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(tryst, bolt)
        alice.library(bear, bear)
        bob.battlefield(giant)
        alice.casts(tryst, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        if (pending != null) alice.chooses(bolt)
        resolveAll()
        alice.casts(bolt, target = giant)
        resolveAll()
        assertEquals(19, bob.life)
        advanceTo(TurnStep.End)
        drive({ d: Decision -> d is Decision.Targets } to { targets("bob") })
        assertEquals(17, bob.life)
    }

    /** Its mana pays only for spells cast from exile. */
    // CR 106.6
    @Test
    fun manaOnlyForSpellsFromExile() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(chronometer, jolt)
        alice.library(jolt, bear)
        alice.casts(chronometer)
        resolveAll()
        expect { alice.exile has jolt }
        assertTrue(runCatching { alice.casts(jolt, target = "bob", paying = listOf(chronometer)) }.isFailure)
    }
}
