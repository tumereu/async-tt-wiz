package arcanum.engine.sets.rvr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dgm.ownAlternative
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Ravnica Remastered chunk 02 in play: a fight spell cheaper for a target with a counter, hellbent making a spell uncounterable and
 * a tutor search for any card, a beginning-of-combat bonus that depends on the creature's colours, a hasty token that must attack,
 * an opponent-only mill doubler with 3 players, a land put back on top after milling, a tap cost of four Advisors (itself included,
 * no opponent's), exiled instants and sorceries copied and cast for free (one target or overloaded), overloaded bounce and shrink
 * with 3 players, and a planeswalker's look-at-the-top +1. */
class RvrChunk02FightHellbentMentorMillOverloadScenariosTest {
    private val scuffle = Cards.invent("Colossal Scuffle", "{1}{G}", "Instant", """
        This spell costs {1} less to cast if it targets a creature you control with a +1/+1 counter on it.
        Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
    """)
    private val torrent = Cards.invent("Hellblaze Torrent", "{X}{R}", "Sorcery", """
        Hellblaze Torrent deals X damage to any target. If a creature dealt damage this way would die this turn, exile it instead.
        Hellbent — If you have no cards in hand, this spell can't be countered and the damage can't be prevented.
    """)
    private val tutor = Cards.invent("Pitborn Tutor", "{1}{B}", "Sorcery", """
        Reveal a card from your hand. Search your library for a card with the same name as that card, reveal it, put it into your hand, then shuffle.
        Hellbent — If you have no cards in hand, instead search your library for a card, put it into your hand, then shuffle.
    """)
    private val valeria = Cards.invent("Valeria, Paragon of Order", "{2}{R}{W}", "Legendary Creature — Angel", """
        Flying
        Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
        At the beginning of combat on your turn, choose up to one target creature you control. Until end of turn, that creature gets +2/+0, gains trample if it's red, and gains vigilance if it's white.
    """, "2/5")
    private val drillmaster = Cards.invent("Horde Drillmaster", "{2}{R}", "Creature — Goblin Soldier", """
        Mentor (Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power.)
        At the beginning of combat on your turn, create a 1/1 red Goblin creature token. That token gains haste until end of turn and attacks this combat if able.
    """, "2/2")
    private val orvac = Cards.invent("Orvac the Verbose", "{2}{U}", "Legendary Creature — Human Advisor",
        "If an opponent would mill one or more cards, they mill twice that many cards instead. (To mill a card, a player puts the top card of their library into their graveyard.)", "1/4")
    private val gloomcap = Cards.invent("Gloomcap Shaman", "{B}{G}", "Creature — Elf Shaman",
        "When this creature enters, mill three cards. You may put a land card from your graveyard on top of your library. (To mill a card, put the top card of your library into your graveyard.)", "3/1")
    private val supplicants = Cards.invent("Tireless Supplicants", "{1}{U}", "Creature — Human Advisor", """
        {1}, {T}: Target player mills a card. (They put the top card of their library into their graveyard.)
        Tap four untapped Advisors you control: Target player mills twelve cards.
        A deck can have any number of cards named Tireless Supplicants.
    """, "1/3")
    private val mastery = Cards.invent("Zirrik's Mastery", "{3}{R}", "Sorcery", """
        Exile target card that's an instant or sorcery from your graveyard. For each card exiled this way, copy it, and you may cast the copy without paying its mana cost. Exile Zirrik's Mastery.
        Overload {5}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val vortex = Cards.invent("Squall Vortex", "{1}{U}", "Instant", """
        Return target nonland permanent you don't control to its owner's hand.
        Overload {6}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val hex = Cards.invent("Shrinking Hex", "{U}", "Instant", """
        Target creature you don't control gets -4/-0 until end of turn.
        Overload {2}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val domra = Cards.invent("Domra Wildcaller", "{1}{R}{G}", "Legendary Planeswalker — Domri", """
        [+1]: Look at the top card of your library. If it's a creature card, you may reveal it and put it into your hand.
        [−2]: Target creature you control fights another target creature.
        [−7]: You get an emblem with "Creatures you control have double strike, trample, hexproof, and haste."
    """, loyalty = "3")
    private val raider = Cards.invent("Ashfoot Raider", "{1}{R}", "Creature — Human Warrior", "", "2/2")
    private val squire = Cards.invent("Dawnward Squire", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val grazer = Cards.invent("Mossback Grazer", "{2}{G}", "Creature — Beast", "", "3/3")
    private val clerk = Cards.invent("Ledger Clerk", "{U}", "Creature — Human Advisor", "", "1/1")
    private val spark = Cards.invent("Vellum Spark", "{R}", "Instant", "Vellum Spark deals 2 damage to any target.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // ---- Fight

    /** With a +1/+1 counter on its creature the spell costs {G}; the fight happens. */
    // CR 601.2f, 701.14a
    @Test
    fun scuffleCostsLessForACreatureWithACounter() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(raider, counters = listOf(p1p1 to 1))
        alice.hand(scuffle)
        bob.battlefield(squire)
        alice.casts(scuffle, targets = listOf(raider, squire))
        resolveAll()
        expect {
            bob.graveyard has squire
            alice.battlefield has raider
        }
    }

    /** Without a counter it costs {1}{G}: one Forest can't pay. */
    // CR 601.2f
    @Test
    fun scuffleCostsFullWithoutACounter() = scenario {
        alice.battlefield("Forest", raider)
        alice.hand(scuffle)
        bob.battlefield(squire)
        alice.casts(scuffle, targets = listOf(raider, squire))
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // ---- Hellbent

    /** Cast with its last card, it can't be countered: the counterspell resolves and the creature it kills is exiled. */
    // CR 101.2, 207.2c, 614.1a
    @Test
    fun torrentWithAnEmptyHandCantBeCountered() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(torrent)
        bob.battlefield("Island", "Island", grazer)
        bob.hand("Denial")
        alice.casts(torrent, target = grazer, x = 3)
        alice.passes()
        bob.casts("Denial", target = torrent)
        allPass()
        expect { stack.names shouldBe listOf(torrent) }
        allPass()
        expect {
            bob.exile has grazer
            alice.graveyard has torrent
        }
    }

    /** With a card left in hand it is countered as usual. */
    // CR 701.6a
    @Test
    fun torrentWithACardInHandIsCountered() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(torrent, raider)
        bob.battlefield("Island", "Island", grazer)
        bob.hand("Denial")
        alice.casts(torrent, target = grazer, x = 3)
        alice.passes()
        bob.casts("Denial", target = torrent)
        allPass()
        expect {
            alice.graveyard has torrent
            bob.battlefield has grazer
        }
    }

    /** With a card in hand: it is revealed and a card with its name is found. */
    // CR 701.20a, 701.23a
    @Test
    fun tutorFindsACardWithTheRevealedName() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(tutor, raider)
        alice.library(squire, raider)
        alice.casts(tutor)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(raider)
        expect { alice.hand.names shouldBe listOf(raider, raider) }
    }

    /** Hellbent: with no cards in hand it finds any card. */
    // CR 207.2c, 608.2c
    @Test
    fun tutorWithAnEmptyHandFindsAnyCard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(tutor)
        alice.library(raider, squire)
        alice.casts(tutor)
        resolveAll()
        alice.chooses(squire)
        expect { alice.hand.names shouldBe listOf(squire) }
    }

    // ---- Beginning of combat and mentor

    /** A red creature gets +2/+0 and trample, not vigilance. */
    // CR 608.2c: the colour is checked as the ability resolves
    @Test
    fun valeriaGivesARedCreatureTrample() = scenario {
        alice.battlefield(valeria, raider)
        advanceTo(TurnStep.BeginningOfCombat)
        targets(raider)
        resolveAll()
        expect {
            raider.power shouldBe 4
            raider hasKeyword Keyword.Trample.kind
            raider lacksKeyword Keyword.Vigilance.kind
        }
    }

    /** Red and white, she gets both keywords herself. */
    // CR 105.2, 608.2c
    @Test
    fun valeriaTargetingHerselfGetsBoth() = scenario {
        alice.battlefield(valeria, squire)
        advanceTo(TurnStep.BeginningOfCombat)
        targets(valeria)
        resolveAll()
        expect {
            valeria.power shouldBe 4
            valeria hasKeyword Keyword.Trample.kind
            valeria hasKeyword Keyword.Vigilance.kind
            squire.power shouldBe 2
        }
    }

    /** The token has haste and must attack; the Drillmaster mentors it. */
    // CR 508.1d, 702.134a
    @Test
    fun drillmasterTokenAttacksAndIsMentored() = scenario {
        alice.battlefield(drillmaster)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { "Goblin Token" hasKeyword Keyword.Haste.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(drillmaster, "Goblin Token")
        resolveAll()
        expect { "Goblin Token".counters(p1p1) shouldBe 1 }
    }

    // ---- Mill

    /** Each opponent's mill is doubled, its controller's own isn't (3 players). */
    // CR 614.1a, 701.17a
    @Test
    fun orvacDoublesOnlyOpponentsMills() = scenario(players = 3) {
        alice.battlefield(orvac, supplicants, "Island", "Island")
        alice.library("Island", "Island", "Island")
        carol.library("Plains", "Plains", "Plains")
        alice.activates(supplicants, target = "carol")
        resolveAll()
        expect {
            carol.graveyard.size shouldBe 2
            carol.library.size shouldBe 1
        }
    }

    @Test
    fun orvacDoesntDoubleItsControllersMill() = scenario(players = 3) {
        alice.battlefield(orvac, supplicants, "Island")
        alice.library("Island", "Island", "Island")
        alice.activates(supplicants, target = "alice")
        resolveAll()
        expect { alice.graveyard.size shouldBe 1 }
    }

    /** Three cards milled, then a land among them goes back on top. */
    // CR 701.17a, 608.2d
    @Test
    fun gloomcapPutsAMilledLandBackOnTop() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(gloomcap)
        alice.library(raider, "Forest", squire, "Plains")
        alice.casts(gloomcap)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.library.names shouldBe listOf("Forest", "Plains")
            alice.graveyard.names.toSet() shouldBe setOf(raider, squire)
        }
    }

    /** Audit false alarm: the four Advisors are untapped ones its controller controls, itself included (no {T} symbol); an
     * opponent's Advisor can't be tapped. */
    // CR 118.3, 302.6, 601.2h
    @Test
    fun supplicantsTapFourOfTheirControllersAdvisors() = scenario {
        alice.battlefield(supplicants, clerk, clerk, clerk)
        bob.battlefield(clerk)
        bob.library(*Array(13) { "Island" })
        alice.activates(supplicants, ability = 1, target = "bob", choosing = listOf(supplicants, "$clerk#0", "$clerk#1", "$clerk#2"))
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 12
            supplicants.tapped shouldBe true
        }
    }

    @Test
    fun supplicantsCantTapAnOpponentsAdvisor() = scenario {
        alice.battlefield(supplicants, clerk, clerk)
        bob.battlefield(clerk)
        assertFails { alice.activates(supplicants, ability = 1, target = "bob") }
    }

    // ---- Overload

    /** One target: the instant is exiled, copied and its copy cast for free. */
    // CR 707.12
    @Test
    fun masteryCastsACopyOfTheExiledCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(mastery)
        alice.graveyard(spark)
        alice.casts(mastery, target = spark)
        resolveAll()
        may(true)
        if (pending is Decision.Targets) targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has spark
            alice.exile has mastery
        }
    }

    /** Overloaded: every instant and sorcery card in its owner's graveyard, each copy cast. */
    // CR 702.96b, 707.12
    @Test
    fun overloadedMasteryCastsEveryCopy() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(mastery)
        alice.graveyard(spark, spark, raider)
        alice.casts(mastery, way = ownAlternative(mastery))
        var guard = 10
        while (guard-- > 0) {
            when (pending) {
                is Decision.May -> may(true)
                is Decision.Targets -> targets("bob")
                null -> if (state.stack.isEmpty()) break else allPass()
                else -> break
            }
        }
        expect {
            bob.life shouldBe 16
            alice.graveyard.names shouldBe listOf(raider)
        }
    }

    /** Overloaded with 3 players: each opponent's nonland permanents return, the caster's stay. */
    // CR 702.96b
    @Test
    fun overloadedVortexBouncesEachOpponent() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", raider)
        alice.hand(vortex)
        bob.battlefield(squire, "Plains")
        carol.battlefield(grazer)
        alice.casts(vortex, way = ownAlternative(vortex))
        resolveAll()
        expect {
            bob.hand has squire
            carol.hand has grazer
            bob.battlefield has "Plains"
            alice.battlefield has raider
        }
    }

    /** One target: only that creature shrinks. */
    // CR 702.96a
    @Test
    fun vortexReturnsItsTarget() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(vortex)
        bob.battlefield(squire, grazer)
        alice.casts(vortex, target = grazer)
        resolveAll()
        expect {
            bob.hand has grazer
            bob.battlefield has squire
        }
    }

    @Test
    fun overloadedHexShrinksEachOpponentsCreatures() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", raider)
        alice.hand(hex)
        bob.battlefield(squire)
        carol.battlefield(grazer)
        alice.casts(hex, way = ownAlternative(hex))
        resolveAll()
        expect {
            squire.power shouldBe -2
            grazer.power shouldBe -1
            raider.power shouldBe 2
        }
    }

    @Test
    fun hexShrinksItsTarget() = scenario {
        alice.battlefield("Island")
        alice.hand(hex)
        bob.battlefield(squire, grazer)
        alice.casts(hex, target = grazer)
        resolveAll()
        expect {
            grazer.power shouldBe -1
            squire.power shouldBe 2
        }
    }

    // ---- Planeswalker

    /** +1: a creature card on top may go to hand; a land stays on top. */
    // CR 606.3, 701.20a
    @Test
    fun domraTakesACreatureFromTheTop() = scenario {
        alice.battlefield(domra, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(grazer, "Forest")
        alice.activates(domra, ability = 0)
        resolveAll()
        may(true)
        expect {
            alice.hand.names shouldBe listOf(grazer)
            alice.library.names shouldBe listOf("Forest")
        }
    }

    @Test
    fun domraLeavesALandOnTop() = scenario {
        alice.battlefield(domra, counters = listOf(CounterKind.Loyalty to 3))
        alice.library("Forest", grazer)
        alice.activates(domra, ability = 0)
        resolveAll()
        expect {
            alice.hand.isEmpty()
            alice.library.names shouldBe listOf("Forest", grazer)
        }
    }
}
