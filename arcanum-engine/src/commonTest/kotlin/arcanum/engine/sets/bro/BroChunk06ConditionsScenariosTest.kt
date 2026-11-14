package arcanum.engine.sets.bro

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 06 in play: a creature entering with counters unless two colors paid for it, a spell costing more on other
 * players' turns, a legend silencing opponents on its controller's turn (three players), a return that adds a counter only to a creature,
 * bouncing an unearthed permanent, an exiled card its owner may cast for more, a mana ability counting named creatures, and a granted
 * trigger returning a creature that dies or is exiled.
 */
class BroChunk06ConditionsScenariosTest {
    private val ironcladParagon = Cards.invent("Ironclad Paragon", "{5}", "Artifact Creature — Wizard", """
        Trample
        This creature enters with two +1/+1 counters on it unless two or more colors of mana were spent to cast it.
    """, "4/4")
    private val sagesLastReverie = Cards.invent("Sage's Last Reverie", "{4}{U}{U}{U}", "Instant", """
        During turns other than yours, this spell costs {3} more to cast.
        Return all nonland permanents to their owners' hands. End the turn.
    """)
    private val corvin = Cards.invent("Corvin, Shield of Thran", "{3}{W}", "Legendary Creature — Human Soldier", """
        During your turn, your opponents can't cast spells or activate abilities of artifacts, creatures, or enchantments.
        Whenever Corvin attacks, create X 1/1 colorless Soldier artifact creature tokens, where X is the number of Soldiers you control.
    """, "3/4")
    private val reactivationOrder = Cards.invent("Reactivation Order", "{1}{W}", "Sorcery",
        "Return target artifact or creature card with mana value 3 or less from your graveyard to the battlefield. If a creature enters this way, it enters with an additional +1/+1 counter on it.")
    private val carefulUnearthing = Cards.invent("Careful Unearthing", "{W}", "Enchantment",
        "{2}{W}: Return target permanent you control to its owner's hand. If it has unearth, instead exile it, then return that card to its owner's hand. Activate only during your turn.")
    private val spiritSeverance = Cards.invent("Spirit Severance", "{1}{W}", "Instant",
        "Exile target nonland permanent. For as long as that card remains exiled, its owner may play it. A spell cast by an opponent this way costs {2} more to cast.")
    private val quarryWorker = Cards.invent("Quarry Worker", "{2}", "Artifact Creature — Assembly-Worker",
        "{T}: You gain 1 life. If you control creatures named Furnace Worker and Spire Worker, you gain 3 life instead.", "2/1")
    private val furnaceWorker = Cards.invent("Furnace Worker", "{5}", "Artifact Creature — Assembly-Worker",
        "{3}: This creature gets +2/+2 until end of turn. If you control creatures named Quarry Worker and Spire Worker, put two +1/+1 counters on this creature instead. Activate only once each turn.", "4/4")
    private val spireWorker = Cards.invent("Spire Worker", "{3}", "Artifact Creature — Assembly-Worker", """
        Reach
        {T}: Add {C}. If you control creatures named Quarry Worker and Furnace Worker, add {C}{C}{C} instead.
    """, "1/3")
    private val vessasReprieve = Cards.invent("Vessa's Reprieve", "{B}", "Instant",
        "Until end of turn, target creature gets +2/+0 and gains \"When this creature dies or is put into exile from the battlefield, return it to its owner's hand.\"")

    private val grizzly = Cards.invent("Mossback Grizzly", "{1}{G}", "Creature — Bear", "", "2/2")
    private val lurkerCub = Cards.invent("Thicket Lurker Cub", "{1}{G}", "Creature — Cat", "Flash", "2/2")
    private val gizmoTotem = Cards.invent("Gizmo Totem", "{2}", "Artifact", "{T}: You gain 1 life.")
    private val rustProwler = Cards.invent("Rust Prowler", "{2}{B}", "Creature — Zombie", "Unearth {B}", "3/2")
    private val banishingGlare = Cards.invent("Banishing Glare", "{W}", "Instant", "Exile target creature.")
    private val sparkVolley = Cards.invent("Spark Volley", "{R}", "Instant", "Spark Volley deals 5 damage to target creature.")

    // CR 614.1c, 601.2h: one color of mana spent: it enters with two +1/+1 counters
    @Test
    fun paragonPaidWithOneColorEntersWithCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(ironcladParagon)
        alice.casts(ironcladParagon)
        allPass()
        expect {
            ironcladParagon.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            ironcladParagon.power shouldBe 6
        }
    }

    // CR 614.1c, 601.2h: two colors of mana spent: no counters
    @Test
    fun paragonPaidWithTwoColorsEntersWithoutCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island")
        alice.hand(ironcladParagon)
        alice.casts(ironcladParagon, paying = listOf("Plains", "Plains", "Plains", "Island", "Island"))
        allPass()
        expect {
            ironcladParagon.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            ironcladParagon.power shouldBe 4
        }
    }

    // CR 601.2f: on another player's turn it costs {3} more, so seven lands can't pay for it
    @Test
    fun reverieCostsMoreOnAnotherPlayersTurn() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(sagesLastReverie)
        alice.casts(sagesLastReverie)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 601.2f, 723.1: on its controller's own turn seven lands pay for it; nonland permanents return to their owners' hands
    @Test
    fun reverieOnYourTurnCostsItsManaCost() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield(grizzly)
        alice.hand(sagesLastReverie)
        alice.casts(sagesLastReverie)
        allPass()
        expect {
            bob.hand has grizzly
            alice.battlefield.size shouldBe 7
        }
    }

    // CR 101.2, 602.5: during Corvin's controller's turn no opponent may activate an artifact's ability or cast a spell
    @Test
    fun corvinStopsOpponentsOnItsControllersTurn() = scenario(players = 3) {
        alice.battlefield(corvin)
        bob.battlefield(gizmoTotem, "Forest", "Forest")
        bob.hand(lurkerCub)
        carol.battlefield(gizmoTotem)
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates(gizmoTotem) }
        assertFailsWith<IllegalArgumentException> { bob.casts(lurkerCub) }
    }

    // CR 101.2: on an opponent's own turn the restriction doesn't apply
    @Test
    fun corvinDoesNotStopOpponentsOnTheirTurn() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(corvin)
        bob.battlefield(gizmoTotem)
        bob.activates(gizmoTotem)
        allPass()
        expect { bob.life shouldBe 21 }
    }

    // CR 614.1c, 614.12: a creature returned this way enters with an additional +1/+1 counter
    @Test
    fun reactivationOrderReturnsACreatureWithACounter() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(grizzly)
        alice.hand(reactivationOrder)
        alice.casts(reactivationOrder, target = grizzly)
        allPass()
        expect {
            alice.battlefield has grizzly
            grizzly.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            grizzly.power shouldBe 3
        }
    }

    // CR 614.1c, 614.12: a noncreature artifact returned this way gets no counter
    @Test
    fun reactivationOrderReturnsANoncreatureArtifactWithoutACounter() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(gizmoTotem)
        alice.hand(reactivationOrder)
        alice.casts(reactivationOrder, target = gizmoTotem)
        allPass()
        expect {
            alice.battlefield has gizmoTotem
            gizmoTotem.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 702.84a, 400.7: an unearthed creature is exiled, then that card returns to its owner's hand (not left in exile by unearth's
    // replacement)
    @Test
    fun carefulUnearthingSavesAnUnearthedCreature() = scenario {
        alice.battlefield(carefulUnearthing, "Swamp", "Plains", "Plains", "Plains")
        alice.graveyard(rustProwler)
        alice.activates(rustProwler, paying = listOf("Swamp"))
        playOut()
        alice.activates(carefulUnearthing, target = rustProwler)
        playOut()
        expect {
            alice.hand has rustProwler
            alice.exile lacks rustProwler
        }
    }

    // CR 400.7: a permanent without unearth simply returns to its owner's hand
    @Test
    fun carefulUnearthingReturnsAPermanentToHand() = scenario {
        alice.battlefield(carefulUnearthing, grizzly, "Plains", "Plains", "Plains")
        alice.activates(carefulUnearthing, target = grizzly)
        playOut()
        expect { alice.hand has grizzly }
    }

    // CR 601.2f: an opponent casting their exiled card pays {2} more: three lands aren't enough for a two-mana creature
    @Test
    fun severedOpponentCardCostsTwoMore() = scenario {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(lurkerCub, "Forest", "Forest", "Forest")
        alice.hand(spiritSeverance)
        alice.casts(spiritSeverance, target = lurkerCub)
        allPass()
        alice.passes()
        bob.casts(lurkerCub)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 400.7, 601.2f: with four lands the opponent casts it from exile
    @Test
    fun severedOpponentCastsTheCardFromExile() = scenario {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(lurkerCub, "Forest", "Forest", "Forest", "Forest")
        alice.hand(spiritSeverance)
        alice.casts(spiritSeverance, target = lurkerCub)
        allPass()
        alice.passes()
        bob.casts(lurkerCub)
        allPass()
        expect { bob.battlefield has lurkerCub }
    }

    // CR 400.7: exiling your own permanent, you cast it again for its mana cost
    @Test
    fun severedOwnCardCostsItsManaCost() = scenario {
        alice.battlefield(lurkerCub, "Plains", "Plains", "Forest", "Forest")
        alice.hand(spiritSeverance)
        alice.casts(spiritSeverance, target = lurkerCub, paying = listOf("Plains", "Plains"))
        allPass()
        alice.casts(lurkerCub, paying = listOf("Forest", "Forest"))
        allPass()
        expect { alice.battlefield has lurkerCub }
    }

    // CR 605.1a, 201.2: alone, the worker adds {C}
    @Test
    fun spireWorkerAloneAddsOne() = scenario {
        alice.battlefield(spireWorker, quarryWorker)
        alice.activates(spireWorker)
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 605.1a, 201.2: with creatures of both other names it adds {C}{C}{C}
    @Test
    fun spireWorkerWithTheOtherTwoAddsThree() = scenario {
        alice.battlefield(spireWorker, quarryWorker, furnaceWorker)
        alice.activates(spireWorker)
        expect { alice.pool.size shouldBe 3 }
    }

    // CR 603.6c, 603.10a, 400.7: the granted trigger returns an exiled creature to its owner's hand (another player's creature)
    @Test
    fun reprieveReturnsAnExiledCreatureToItsOwnersHand() = scenario(players = 3) {
        alice.battlefield("Swamp", "Plains")
        bob.battlefield(grizzly)
        alice.hand(vessasReprieve, banishingGlare)
        alice.casts(vessasReprieve, target = grizzly)
        allPass()
        expect { grizzly.power shouldBe 4 }
        alice.casts(banishingGlare, target = grizzly)
        playOut()
        expect {
            bob.hand has grizzly
            alice.hand lacks grizzly
        }
    }

    // CR 603.6c, 700.4: the granted trigger returns a creature that dies
    @Test
    fun reprieveReturnsACreatureThatDies() = scenario {
        alice.battlefield(grizzly, "Swamp", "Mountain")
        alice.hand(vessasReprieve, sparkVolley)
        alice.casts(vessasReprieve, target = grizzly)
        allPass()
        alice.casts(sparkVolley, target = grizzly)
        playOut()
        expect { alice.hand has grizzly }
    }
}
