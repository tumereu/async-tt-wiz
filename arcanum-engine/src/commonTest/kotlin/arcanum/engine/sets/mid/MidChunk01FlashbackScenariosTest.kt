package arcanum.engine.sets.mid

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MID chunk 01: flashback cards (CR 702.34a) with an any-number sacrifice cost, a flashback cost that removes loyalty counters, a face-down
 * exile you may play, an opponent's choice and returns. Invented names carry the real cards' oracle text. */
class MidChunk01FlashbackScenariosTest {
    private val tail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"
    private val tailAdditional = "(You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)"

    private val patchwork = Cards.invent("Cadaver Patchwork", "{U}{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice any number of creatures.
        Create an X/X blue and black Zombie creature token with menace, where X is the total power of the sacrificed creatures.
        Flashback {3}{U}{B} $tailAdditional
    """)
    private val kindle = Cards.invent("Kindle the Dusk", "{X}{R}", "Sorcery", """
        Kindle the Dusk deals X damage to any target. It deals X plus 1 damage instead if that target is a creature or planeswalker.
        Flashback—{3}{R}, Remove X loyalty counters from among planeswalkers you control. If you cast this spell this way, X can't be 0. $tail
    """)
    private val foresight = Cards.invent("Pilfered Foresight", "{U}{B}", "Instant", """
        Look at the top two cards of target opponent's library. Exile one of them face down and put the other on the bottom of that library. You may play the exiled card for as long as it remains exiled, and you may spend mana as though it were mana of any color to cast that spell.
        Flashback {1}{U}{B}
    """)
    private val butcher = Cards.invent("Rise for the Butcher", "{3}{B}{R}", "Sorcery", """
        Choose up to two target creature cards in your graveyard. An opponent chooses one of them. Return that card to your hand. Return the other to the battlefield under your control. It gains haste. Exile it at the beginning of the next end step.
        Flashback {4}{B}{R} $tail
    """)
    private val interlude = Cards.invent("Sanctified Interlude", "{W}{U}", "Sorcery", """
        Exile target nonlegendary creature, then return it to the battlefield under its owner's control. If it entered under your control, put a +1/+1 counter on it. Otherwise, tap it.
        Flashback {1}{W}{U} $tail
    """)
    private val upheaval = Cards.invent("Feral-Grove Upheaval", "{1}{R}{G}", "Sorcery", """
        Destroy target artifact, enchantment, or land. If a land was destroyed this way, its controller may search their library for up to two basic land cards, put them onto the battlefield tapped, then shuffle. Otherwise, its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
        Flashback {3}{R}{G}
    """)
    private val refuse = Cards.invent("Refuse to Rest", "{W}{B}", "Sorcery", """
        Return target creature card with mana value 3 or less from your graveyard to the battlefield. It gains "If this creature would die, exile it instead."
        Flashback {3}{W}{B} $tail
    """)
    private val double = Cards.invent("Ribbiting Double", "{1}{G}{U}", "Sorcery", """
        Create a token that's a copy of target non-Frog creature, except it's a 1/1 green Frog.
        Flashback {3}{G}{U} $tail
    """)
    private val renewal = Cards.invent("Gravemoor Renewal", "{3}{B}{G}", "Sorcery", """
        This spell costs {1} less to cast for each creature that died this turn.
        Return target creature card from your graveyard to the battlefield.
        Flashback {5}{B}{G} $tail
    """)
    private val epiphany = Cards.invent("Voltaic Epiphany", "{2}{R}", "Instant", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards.
        Flashback {3}{R} $tailAdditional
    """)

    private val brute = Cards.invent("Hollowmoor Brute", "{2}{G}", "Creature — Beast", "", "3/3")
    private val whelp = Cards.invent("Hollowmoor Whelp", "{1}{R}", "Creature — Wolf", "", "2/2")
    private val skyhunter = Cards.invent("Hollowmoor Skyhunter", "{3}{U}", "Creature — Bird", "Flying", "4/4")
    private val toad = Cards.invent("Hollowmoor Toad", "{G}", "Creature — Frog", "", "1/1")
    private val relic = Cards.invent("Hollowmoor Relic", "{2}", "Artifact", "")
    private val adept = Cards.invent("Hollowmoor Pyre Adept", "{2}{R}", "Legendary Planeswalker — Chandra",
        "[+1]: Hollowmoor Pyre Adept deals 1 damage to each opponent.", loyalty = "4")
    private val firebolt = Cards.invent("Hollowmoor Firebolt", "{R}", "Instant", "Hollowmoor Firebolt deals 3 damage to target creature.")
    private val emberstorm = Cards.invent("Hollowmoor Emberstorm", "{R}", "Sorcery", "Hollowmoor Emberstorm deals 3 damage to each creature.")

    // CR 601.2b, 601.2h (any number of creatures sacrificed as the cost), 608.2h (their total power as they last existed), 111.4
    @Test
    fun patchworkMakesAZombieOfTheSacrificedCreaturesTotalPower() = scenario {
        alice.battlefield(brute, whelp, "Island", "Swamp")
        alice.hand(patchwork)
        alice.casts(patchwork, choosing = listOf(brute, whelp))
        resolveAll()
        expect {
            alice.graveyard has brute
            alice.graveyard has whelp
            "Zombie Token".power shouldBe 5
            "Zombie Token".toughness shouldBe 5
            "Zombie Token" hasKeyword Keyword.Menace.kind
        }
    }

    // CR 601.2b ("any number": zero), 704.5f (a 0/0 token dies), 702.34a (cast from the graveyard with the additional cost, then exiled)
    @Test
    fun patchworkWithNothingSacrificedMakesAZeroZeroAndFlashbackPaysTheCostAgain() = scenario {
        alice.battlefield(whelp, "Island", "Swamp", "Island", "Swamp", "Island", "Swamp", "Island")
        alice.hand(patchwork)
        alice.casts(patchwork)
        answer(Answer.Objects(emptyList()))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            alice.battlefield lacks "Zombie Token"
            alice.battlefield has whelp
            alice.graveyard has patchwork
        }
        alice.casts(patchwork, choosing = listOf(whelp))
        resolveAll()
        expect {
            "Zombie Token".power shouldBe 2
            patchwork.zone shouldBe Zone.Exile
        }
    }

    // CR 107.3a, 608.2b (the target's kind as it resolves): X plus 1 to a creature, X to a player
    @Test
    fun kindleDealsOneMoreToACreatureThanToAPlayer() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(skyhunter)
        alice.hand(kindle)
        alice.casts(kindle, target = skyhunter, x = 3)
        resolveAll()
        expect { bob.graveyard has skyhunter; kindle.zone shouldBe Zone.Graveyard }
    }

    // CR 702.34a, 118.3 (the flashback cost removes X loyalty counters), 107.3 (X can't be 0 cast this way), 120.3 (X to a player)
    @Test
    fun kindleFlashbackRemovesLoyaltyAndXCantBeZero() = scenario {
        alice.battlefield(adept, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(kindle)
        alice.casts(kindle, target = "bob", x = 3, choosing = listOf(adept))
        resolveAll()
        expect {
            bob.life shouldBe 17
            adept.counters(CounterKind.Loyalty) shouldBe 1
            kindle.zone shouldBe Zone.Exile
        }
    }

    // CR 107.3: "If you cast this spell this way, X can't be 0"
    @Test
    fun kindleFlashbackRefusesXZero() = scenario {
        alice.battlefield(adept, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(kindle)
        assertFailsWith<IllegalArgumentException> { alice.casts(kindle, target = "bob", x = 0) }
        // the refused answer leaves the announcement open, asking at least 1
        assertEquals(1, decision<Decision.Announce>().min)
    }

    // CR 107.3: from the hand, X may be 0 (the rule is only for a flashback cast)
    @Test
    fun kindleFromHandMayHaveXZero() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield(skyhunter)
        alice.hand(kindle)
        alice.casts(kindle, target = skyhunter)
        assertEquals(0, decision<Decision.Announce>().min)
        answer(Answer.Number(0))
        (pending as? Decision.Targets)?.let { answer(Answer.Targets(listOf(target(skyhunter)))) }
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect { skyhunter.view.obj?.damage shouldBe 1 }
    }

    // CR 406.3, 601.2a, 609.4b: the face-down card Alice exiled from Bob's library is hers to cast, with mana of any color
    @Test
    fun foresightLetsYouCastTheExiledCardWithAnyColor() = scenario {
        alice.battlefield("Island", "Swamp", "Island", "Swamp")
        bob.library(whelp, "Plains", "Forest")
        alice.hand(foresight)
        alice.casts(foresight, target = "bob", paying = listOf("Island", "Swamp"))
        resolveAll()
        alice.chooses(whelp)
        resolveAll()
        expect {
            whelp.zone shouldBe Zone.Exile
            bob.library.names.last() shouldBe "Plains"
        }
        // CR 406.3: Alice, who exiled it face down, may look at it; Bob may not
        val view = arcanum.engine.Engine.view(state)
        assertEquals(true, card(whelp) in arcanum.engine.action.Knowledge.visible(state, view, alice.id))
        assertEquals(false, card(whelp) in arcanum.engine.action.Knowledge.visible(state, view, bob.id))
        alice.casts(whelp)
        resolveAll()
        expect {
            alice.battlefield has whelp
            whelp.controller shouldBe alice.id
        }
    }

    // CR 102.3, 608.2d (Alice picks which opponent chooses), 400.7, 702.10, 603.7 (the returned creature is exiled at the next end step)
    @Test
    fun butcherOpponentPicksTheCardForTheHandTheOtherAttacksAndIsExiled() = scenario(players = 3) {
        alice.battlefield("Swamp", "Mountain", "Swamp", "Mountain", "Swamp")
        alice.graveyard(brute, whelp)
        alice.hand(butcher)
        alice.casts(butcher, targets = listOf(brute, whelp))
        resolveAll()
        val who = decision<Decision.ChoosePlayer>()
        assertEquals(setOf(bob.id, carol.id), who.options.toSet())
        answer(Answer.Player(carol.id))
        val pick = decision<Decision.ChooseObjects>()
        assertEquals(carol.id, pick.player)
        carol.chooses(brute)
        resolveAll()
        expect {
            alice.hand has brute
            alice.battlefield has whelp
            whelp hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { whelp.zone shouldBe Zone.Exile }
    }

    // CR 400.7, 110.2: Alice's creature comes back under her control with a counter; Bob's comes back under his, tapped
    @Test
    fun interludeCountersYoursAndTapsTheirs() = scenario {
        alice.battlefield(brute, "Plains", "Island", "Plains", "Island", "Plains")
        bob.battlefield(skyhunter)
        alice.hand(interlude)
        alice.casts(interlude, target = brute, paying = listOf("Plains", "Island"))
        resolveAll()
        expect { brute.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1; brute.tapped shouldBe false }
        alice.casts(interlude, target = skyhunter)
        resolveAll()
        expect {
            skyhunter.controller shouldBe bob.id
            skyhunter.tapped shouldBe true
            skyhunter.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            interlude.zone shouldBe Zone.Exile
        }
    }

    // CR 608.2h (a land was destroyed), 701.23a: its controller may search for up to two basic lands, put onto the battlefield tapped
    @Test
    fun upheavalOnALandLetsItsControllerFetchTwo() = scenario {
        alice.battlefield("Mountain", "Forest", "Mountain")
        bob.battlefield("Island")
        bob.library("Plains", "Island", "Swamp", "Forest")
        alice.hand(upheaval)
        alice.casts(upheaval, target = "Island")
        resolveAll()
        answer(Answer.YesNo(true))
        val search = decision<Decision.ChooseObjects>()
        assertEquals(bob.id, search.player)
        assertEquals(2, search.max)
        answer(Answer.Objects(search.options.take(2)))
        resolveAll()
        expect {
            bob.battlefield.size shouldBe 2
            bob.library.size shouldBe 2
        }
    }

    // CR 608.2c ("Otherwise"): an artifact destroyed, its controller may fetch only one
    @Test
    fun upheavalOnAnArtifactLetsItsControllerFetchOne() = scenario {
        alice.battlefield("Mountain", "Forest", "Mountain")
        bob.battlefield(relic)
        bob.library("Plains", "Island", "Swamp", "Forest")
        alice.hand(upheaval)
        alice.casts(upheaval, target = relic)
        resolveAll()
        answer(Answer.YesNo(true))
        val search = decision<Decision.ChooseObjects>()
        assertEquals(1, search.max)
        answer(Answer.Objects(search.options.take(1)))
        resolveAll()
        expect { bob.graveyard has relic; bob.battlefield.size shouldBe 1; bob.library.size shouldBe 3 }
    }

    // CR 614.1a, 614.6: the returned creature would die, it's exiled instead
    @Test
    fun refuseToRestExilesTheCreatureWhenItWouldDie() = scenario {
        alice.battlefield("Plains", "Swamp", "Mountain")
        alice.graveyard(brute)
        alice.hand(refuse, firebolt)
        alice.casts(refuse, target = brute, paying = listOf("Plains", "Swamp"))
        resolveAll()
        expect { alice.battlefield has brute }
        alice.casts(firebolt, target = brute)
        resolveAll()
        expect { brute.zone shouldBe Zone.Exile }
    }

    // CR 707.9b, 707.2: the token copies the creature's abilities but is a 1/1 green Frog
    @Test
    fun doubleMakesAOneOneGreenFrogCopy() = scenario {
        alice.battlefield("Forest", "Island", "Forest")
        bob.battlefield(skyhunter, toad)
        alice.hand(double)
        alice.casts(double, target = skyhunter)
        resolveAll()
        val copy = CardRef(tokens(skyhunter).single(), "frog copy")
        expect {
            copy.controller shouldBe alice.id
            copy.power shouldBe 1
            copy.toughness shouldBe 1
            copy.colors shouldBe listOf(arcanum.api.Color.Green)
            copy.subtypes shouldBe listOf(Subtype("Frog"))
            copy hasKeyword Keyword.Flying.kind
        }
    }

    // CR 601.2f, 700.4: one creature died this turn, so it costs {2}{B}{G}: three lands can't pay it
    @Test
    fun renewalWithOneDeathCostsFour() = scenario {
        alice.battlefield("Swamp", "Forest", "Swamp")
        bob.battlefield(whelp)
        alice.graveyard(brute)
        alice.hand(renewal, firebolt)
        alice.castsWithoutCosts(firebolt, target = whelp)
        resolveAll()
        alice.casts(renewal, target = brute)
        assertEquals(true, decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 601.2f, 700.4: two creatures died this turn, so it costs {1}{B}{G}: three lands pay it
    @Test
    fun renewalWithTwoDeathsIsCastForThreeMana() = scenario {
        alice.battlefield("Swamp", "Forest", "Swamp")
        bob.battlefield(whelp, toad)
        alice.graveyard(brute)
        alice.hand(renewal, emberstorm)
        alice.castsWithoutCosts(emberstorm)
        resolveAll()
        alice.casts(renewal, target = brute)
        resolveAll()
        expect { alice.battlefield has brute }
    }

    // CR 702.34a, 601.2b: flashback pays the additional discard cost too
    @Test
    fun epiphanyFlashbackDiscardsAndDraws() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(epiphany)
        alice.hand(whelp, brute)
        alice.library("Island", "Island", "Island")
        alice.casts(epiphany, choosing = listOf(whelp))
        resolveAll()
        expect {
            alice.graveyard has whelp
            alice.hand.size shouldBe 3
            epiphany.zone shouldBe Zone.Exile
        }
    }
}
