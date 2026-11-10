package arcanum.engine.sets.fdn

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The FDN cards that move cards between zones, played: the invented names carry the oracle text of Ambush Wolf, Angel of Finality, Driver of
 * the Dead, Elvish Regrower, Immersturm Predator, Kykar Zephyr Awakener, Macabre Waltz, Raise the Past, Scavenging Ooze, Soul-Guide Lantern
 * and Zombify.
 */
class FdnChunk20MoveScenariosTest {
    private val prowler = Cards.invent("Thicket Prowler", "{2}{G}", "Creature — Wolf", """
        Flash (You may cast this spell any time you could cast an instant.)
        When this creature enters, exile up to one target card from a graveyard.
    """, "4/2")
    private val seraph = Cards.invent("Seraph of Last Rites", "{3}{W}", "Creature — Angel", """
        Flying
        When this creature enters, exile target player's graveyard.
    """, "3/4")
    private val coachman = Cards.invent("Hearse Coachman", "{3}{B}", "Creature — Vampire",
        "When this creature dies, return target creature card with mana value 2 or less from your graveyard to the battlefield.", "3/2")
    private val reclaimer = Cards.invent("Fernroot Reclaimer", "{2}{G}{G}", "Creature — Elf Druid",
        "When this creature enters, return target permanent card from your graveyard to your hand.", "4/3")
    private val gorger = Cards.invent("Cindermaw Gorger", "{2}{B}{R}", "Creature — Vampire Dragon", """
        Flying
        Whenever this creature becomes tapped, exile up to one target card from a graveyard and put a +1/+1 counter on this creature.
        Sacrifice another creature: This creature gains indestructible until end of turn. Tap it.
    """, "3/3")
    private val ozzik = Cards.invent("Ozzik, Gale Caller", "{2}{W}{U}", "Legendary Creature — Bird Wizard", """
        Flying
        Whenever you cast a noncreature spell, choose one —
        • Exile another target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        • Create a 1/1 white Spirit creature token with flying.
    """, "3/4")
    private val minuet = Cards.invent("Graveside Minuet", "{1}{B}", "Sorcery",
        "Return up to two target creature cards from your graveyard to your hand, then discard a card.")
    private val muster = Cards.invent("Bygone Muster", "{2}{W}{W}", "Sorcery",
        "Return all creature cards with mana value 2 or less from your graveyard to the battlefield.")
    private val slime = Cards.invent("Marrowsop Slime", "{1}{G}", "Creature — Ooze",
        "{G}: Exile target card from a graveyard. If it was a creature card, put a +1/+1 counter on this creature and you gain 1 life.", "2/2")
    private val lamp = Cards.invent("Wraithwick Lamp", "{1}", "Artifact", """
        When this artifact enters, exile target card from a graveyard.
        {T}, Sacrifice this artifact: Exile each opponent's graveyard.
        {1}, {T}, Sacrifice this artifact: Draw a card.
    """)
    private val reanimation = Cards.invent("Cold Reanimation", "{3}{B}", "Sorcery",
        "Return target creature card from your graveyard to the battlefield.")

    private val cub = Cards.invent("Ashpit Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val colt = Cards.invent("Ashpit Colt", "{G}", "Creature — Horse", "", "1/1")
    private val brute = Cards.invent("Ashpit Brute", "{1}{G}{G}", "Creature — Ogre", "", "3/3")
    private val pup = Cards.invent("Ashpit Pup", "{G}", "Creature — Dog", "", "1/1")
    private val hex = Cards.invent("Bonecrack Hex", "{B}", "Instant", "Destroy target creature.")
    private val erasure = Cards.invent("Sudden Erasure", "{B}", "Instant", "Exile target card from a graveyard.")
    private val idea = Cards.invent("Stray Idea", "{U}", "Instant", "Draw a card.")
    private val seize = Cards.invent("Sudden Seizure", "{R}", "Sorcery", "Gain control of target creature until end of turn.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** The legal targets of the target decision, in any order. */
    private fun Scenario.targetOptions() = decision<Decision.Targets>().options.toSet()

    /** Answers the mana payment a spell cast without its targets given is left at. */
    private fun Scenario.payAsSuggested() {
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 702.8a: cast in the opponent's turn. CR 404.1: any graveyard
    @Test
    fun prowlerComesInOnTheOpponentsTurnAndExilesFromTheirGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(prowler)
        bob.graveyard(cub, colt)
        turn(number = 2, active = bob, step = TurnStep.Upkeep, priority = alice)
        alice.casts(prowler)
        allPass()
        alice.answers(Answer.Targets(listOf(target(cub))))
        allPass()
        expect {
            alice.battlefield has prowler
            bob.exile has cub
            bob.graveyard has colt
        }
    }

    // CR 601.2c, 115.1d: "up to one": no target is a legal choice
    @Test
    fun prowlerMayExileNothing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(prowler)
        bob.graveyard(cub)
        alice.casts(prowler)
        allPass()
        alice.answers(Answer.Targets(emptyList()))
        allPass()
        expect {
            bob.graveyard has cub
            stack.isEmpty()
        }
    }

    // CR 115.10: the player is targeted, every card of that graveyard goes; the other player's stays
    @Test
    fun seraphExilesTheWholeGraveyardOfTheTargetPlayerOnly() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(seraph)
        alice.graveyard(colt)
        bob.graveyard(cub, "Forest", idea)
        alice.casts(seraph)
        allPass()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        expect {
            card(seraph) hasKeyword Keyword.Flying.kind
            bob.graveyard.isEmpty()
            bob.exile has cub
            bob.exile has "Forest"
            bob.exile has idea
            alice.graveyard has colt
        }
    }

    private fun Scenario.killCoachman() {
        alice.battlefield(coachman, "Swamp")
        alice.hand(hex)
        alice.casts(hex, target = coachman)
        allPass()
    }

    // CR 603.6c, 202.3: mana value 2 or less, a creature card, your graveyard; CR 110.2a: under your control
    @Test
    fun coachmanReturnsOnlyYourCheapCreatureCardsWhenItDies() = scenario {
        alice.graveyard(cub, colt, brute, "Forest")
        bob.graveyard(pup)
        killCoachman()
        assertEquals(setOf(target(cub), target(colt)), targetOptions())
        alice.answers(Answer.Targets(listOf(target(cub))))
        allPass()
        expect {
            alice.battlefield has cub
            card(cub).controller shouldBe alice.id
            alice.graveyard has colt
            alice.graveyard has coachman
        }
    }

    // CR 110.4a: a permanent card; CR 115.1d: your graveyard only
    @Test
    fun reclaimerReturnsAPermanentCardToYourHand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(reclaimer)
        alice.graveyard(cub, lamp, "Plains", idea)
        bob.graveyard(pup)
        alice.casts(reclaimer)
        allPass()
        assertEquals(setOf(target(cub), target(lamp), target("Plains")), targetOptions())
        alice.answers(Answer.Targets(listOf(target("Plains"))))
        allPass()
        expect {
            alice.hand has "Plains"
            alice.graveyard has cub
        }
    }

    // CR 701.26a, 603.2e: attacking taps it (CR 508.1f) and the tap triggers; CR 608.2c: exile, then the counter
    @Test
    fun gorgerExilesAndGrowsWhenItAttacks() = scenario {
        alice.battlefield(gorger)
        bob.graveyard(cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gorger)
        alice.answers(Answer.Targets(listOf(target(cub))))
        allPass()
        expect {
            bob.exile has cub
            card(gorger).counters(p1p1) shouldBe 1
            card(gorger).power shouldBe 4
            card(gorger).tapped shouldBe true
        }
    }

    // CR 601.2c: "up to one"; the counter doesn't depend on the exile
    @Test
    fun gorgerGrowsWithoutATarget() = scenario {
        alice.battlefield(gorger)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gorger)
        allPass()
        expect { card(gorger).counters(p1p1) shouldBe 1 }
    }

    // CR 701.21a: the cost, so the creature is already in the graveyard to be exiled; CR 701.26a, 603.2e: "Tap it" triggers; CR 702.12b
    @Test
    fun gorgerSacrificesACreatureToSurviveDestruction() = scenario {
        alice.battlefield(gorger, cub)
        bob.battlefield("Swamp")
        bob.hand(hex)
        alice.activates(gorger, choosing = listOf(cub))
        expect { alice.graveyard has cub }
        allPass()
        alice.answers(Answer.Targets(listOf(target(cub))))
        allPass()
        expect {
            alice.exile has cub
            card(gorger).tapped shouldBe true
            card(gorger).counters(p1p1) shouldBe 1
        }
        alice.passes()
        bob.casts(hex, target = gorger)
        allPass()
        expect { alice.battlefield has gorger }
    }

    // CR 701.26a: only an untapped permanent can be tapped, so nothing becomes tapped and nothing triggers (CR 603.2e)
    @Test
    fun gorgerAlreadyTappedDoesNotTrigger() = scenario {
        alice.battlefield(gorger, tapped = true)
        alice.battlefield(cub)
        alice.activates(gorger, choosing = listOf(cub))
        allPass()
        expect {
            stack.isEmpty()
            card(gorger).counters(p1p1) shouldBe 0
            card(gorger) hasKeyword Keyword.Indestructible.kind
        }
    }

    // "another": CR 118.3, it can't pay with itself
    @Test
    fun gorgerCannotSacrificeItself() = scenario {
        alice.battlefield(gorger)
        assertFailsWith<Throwable> { alice.activates(gorger, choosing = listOf(gorger)) }
    }

    // CR 700.2b, 603.7a: mode chosen as it goes on the stack, "another" leaves Ozzik out; CR 400.7: back as a new object at the next end step
    @Test
    fun ozzikBlinksAnotherCreatureUntilTheEndStep() = scenario {
        alice.battlefield(ozzik, "Island")
        alice.battlefield(cub, counters = listOf(p1p1 to 1))
        alice.battlefield(colt)
        alice.hand(idea)
        alice.library("Plains")
        alice.casts(idea)
        alice.answers(Answer.Modes(listOf(0)))
        assertEquals(setOf(target(cub), target(colt)), targetOptions())
        alice.answers(Answer.Targets(listOf(target(cub))))
        allPass()
        expect { card(cub).zone shouldBe Zone.Exile }
        allPass()
        expect { alice.hand has "Plains" }
        advanceTo(TurnStep.End)
        allPass()
        expect {
            card(cub).zone shouldBe Zone.Battlefield
            card(cub).controller shouldBe alice.id
            card(cub).counters(p1p1) shouldBe 0
        }
    }

    // CR 110.2a, 603.7a: "under its owner's control": a creature taken from Bob goes back to Bob
    @Test
    fun ozzikReturnsAStolenCreatureToItsOwner() = scenario {
        alice.battlefield(ozzik, "Mountain", "Island")
        alice.hand(seize, idea)
        alice.library("Plains")
        bob.battlefield(cub)
        alice.casts(seize, target = cub)
        alice.answers(Answer.Modes(listOf(1)))
        allPass()
        allPass()
        expect { card(cub).controller shouldBe alice.id }
        alice.casts(idea)
        alice.answers(Answer.Modes(listOf(0)))
        alice.answers(Answer.Targets(listOf(target(cub))))
        allPass()
        allPass()
        advanceTo(TurnStep.End)
        allPass()
        expect {
            card(cub).zone shouldBe Zone.Battlefield
            card(cub).controller shouldBe bob.id
        }
    }

    // CR 111.3, 700.2b: alone, "another target creature" has no legal target, so only the token mode can be chosen
    @Test
    fun ozzikAloneMakesASpiritOnly() = scenario {
        alice.battlefield(ozzik, "Island")
        alice.hand(idea)
        alice.library("Plains")
        alice.casts(idea)
        assertEquals(listOf(false, true), decision<Decision.Modes>().options.map { it.available })
        alice.answers(Answer.Modes(listOf(1)))
        allPass()
        expect {
            alice.battlefield has "Spirit Token"
            "Spirit Token".power shouldBe 1
            "Spirit Token".toughness shouldBe 1
            "Spirit Token".colors shouldBe listOf(Color.White)
            "Spirit Token" hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.2: a creature spell isn't a noncreature spell
    @Test
    fun ozzikIgnoresACreatureSpell() = scenario {
        alice.battlefield(ozzik, "Forest", "Forest")
        alice.hand(cub)
        alice.casts(cub)
        assertEquals(null, pending)
        expect { stack.size shouldBe 1 }
    }

    // CR 601.2c, 608.2c: both come back, then the discard
    @Test
    fun minuetReturnsTwoThenDiscards() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(minuet, idea)
        alice.graveyard(cub, colt, brute)
        alice.casts(minuet, targets = listOf(cub, colt))
        allPass()
        alice.chooses(idea)
        expect {
            alice.hand has cub
            alice.hand has colt
            alice.graveyard has idea
            alice.graveyard has brute
            alice.graveyard has minuet
        }
    }

    // CR 608.2c: "then": a card just returned can be the one discarded
    @Test
    fun minuetMayDiscardAReturnedCard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(minuet, idea)
        alice.graveyard(cub, colt)
        alice.casts(minuet, targets = listOf(cub, colt))
        allPass()
        alice.chooses(cub)
        expect {
            alice.graveyard has cub
            alice.hand has colt
            alice.hand has idea
        }
    }

    // CR 601.2c: no targets is legal, and the spell still resolves and makes you discard
    @Test
    fun minuetWithNoTargetsStillDiscards() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(minuet, idea, pup)
        alice.graveyard(cub)
        alice.casts(minuet)
        alice.answers(Answer.Targets(emptyList()))
        payAsSuggested()
        allPass()
        alice.chooses(pup)
        expect {
            alice.graveyard has pup
            alice.graveyard has cub
            alice.hand has idea
        }
    }

    // CR 115.10, 202.3: every creature card of mana value 2 or less of yours; not 3, not a land, not the opponent's
    @Test
    fun musterReturnsEveryCheapCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(muster)
        alice.graveyard(cub, colt, brute, "Forest")
        bob.graveyard(pup)
        alice.casts(muster)
        allPass()
        expect {
            alice.battlefield has cub
            alice.battlefield has colt
            alice.graveyard has brute
            alice.graveyard has "Forest"
            bob.graveyard has pup
            alice.battlefield lacks pup
        }
    }

    // CR 400.7j: "it" finds the exiled card; a creature card gives the counter and the life
    @Test
    fun slimeGrowsFromACreatureCard() = scenario {
        alice.battlefield(slime, "Forest")
        alice.graveyard(cub)
        alice.activates(slime, target = cub)
        allPass()
        expect {
            alice.exile has cub
            card(slime).counters(p1p1) shouldBe 1
            alice.life shouldBe 21
        }
    }

    // CR 400.7j: an exiled noncreature card gives nothing
    @Test
    fun slimeExilesAnyCardButGrowsOnlyFromCreatures() = scenario {
        alice.battlefield(slime, "Forest")
        bob.graveyard(idea)
        alice.activates(slime, target = idea)
        allPass()
        expect {
            bob.exile has idea
            card(slime).counters(p1p1) shouldBe 0
            alice.life shouldBe 20
        }
    }

    // CR 608.2b: the target left the graveyard in response, so the ability doesn't resolve: no counter, no life
    @Test
    fun slimeGetsNothingWhenTheCardIsGoneInResponse() = scenario {
        alice.battlefield(slime, "Forest")
        alice.graveyard(cub)
        bob.battlefield("Swamp")
        bob.hand(erasure)
        alice.activates(slime, target = cub)
        alice.passes()
        bob.casts(erasure, target = cub)
        allPass()
        allPass()
        expect {
            alice.exile has cub
            card(slime).counters(p1p1) shouldBe 0
            alice.life shouldBe 20
        }
    }

    // CR 115.1d: a card from any graveyard
    @Test
    fun lampExilesATargetCardWhenItEnters() = scenario {
        alice.battlefield("Plains")
        alice.hand(lamp)
        bob.graveyard(cub, idea)
        alice.casts(lamp)
        allPass()
        alice.answers(Answer.Targets(listOf(target(idea))))
        allPass()
        expect {
            bob.exile has idea
            bob.graveyard has cub
        }
    }

    // CR 701.21a, 102.3: each opponent's whole graveyard, not yours, and the sacrificed Lamp stays there
    @Test
    fun lampExilesEachOpponentsGraveyard() = scenario(players = 3) {
        alice.battlefield(lamp)
        alice.graveyard(colt)
        bob.graveyard(cub, idea)
        carol.graveyard(pup)
        alice.activates(lamp, ability = 0)
        allPass()
        expect {
            bob.graveyard.isEmpty()
            carol.graveyard.isEmpty()
            bob.exile has cub
            bob.exile has idea
            carol.exile has pup
            alice.graveyard has colt
            alice.graveyard has lamp
        }
    }

    // CR 701.21a: the second ability, its cost includes {1}
    @Test
    fun lampDrawsACard() = scenario {
        alice.battlefield(lamp, "Plains")
        alice.library("Island")
        alice.activates(lamp, ability = 1)
        allPass()
        expect {
            alice.hand has "Island"
            alice.graveyard has lamp
        }
    }

    // CR 115.1a: a creature card in your graveyard; CR 110.2a: under your control
    @Test
    fun reanimationReturnsACreatureCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(reanimation)
        alice.graveyard(brute, colt, "Forest")
        bob.graveyard(pup)
        alice.casts(reanimation)
        assertEquals(setOf(target(brute), target(colt)), targetOptions())
        alice.answers(Answer.Targets(listOf(target(brute))))
        payAsSuggested()
        allPass()
        expect {
            alice.battlefield has brute
            card(brute).controller shouldBe alice.id
            alice.graveyard has colt
        }
    }
}
