package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MOM chunk 11: one spell in all from five exiled cards, damage to up to two targets, a creature exiled instead of dying, mana from creatures
 * counted, each player's own cast limit, the first +1/+1 counters on a permanent this turn, counters put this turn checked at each end step, an
 * attacking X/X token, cards put on the bottom then drawn plus one, and a card exiled with a creature cast free and put on the bottom. */
class MomChunk11ConditionsAndEffectsScenariosTest {
    private val pyra = Cards.invent("Pyra, Ember Herald", "{4}{R}{R}", "Legendary Planeswalker — Chandra", """
        Whenever you cast an instant or sorcery spell, copy it. You may choose new targets for the copy. This ability triggers only once each turn.
        [+2]: Add two mana in any combination of colors.
        [+1]: Exile the top five cards of your library. Until the end of your next turn, you may cast an instant or sorcery spell from among those exiled cards.
        [−X]: Pyra deals X damage to each of up to two targets.
    """.trimIndent())
    private val outrider = Cards.invent("Thicket Outrider", "{4}{G}", "Creature — Elf Warrior", """
        When this creature enters, return target creature or battle card from your graveyard to your hand.
        If this creature would die, exile it instead.
    """.trimIndent(), "3/3")
    private val orla = Cards.invent("Orla and Bryn", "{2}{G}{U}", "Legendary Creature — Human God", """
        Creatures you control have vigilance and "{T}: Add one mana of any color. Spend this mana only to cast a creature spell."
        Whenever you cast a creature spell, if three or more mana from creatures was spent to cast it, draw a card.
    """.trimIndent(), "4/4")
    private val arbiter = Cards.invent("Compleated Arbiter", "{2}{W}", "Creature — Phyrexian Wizard", """
        Each player can't cast more than one non-Phyrexian spell each turn.
        Non-Phyrexian creatures enter tapped.
    """.trimIndent(), "3/3")
    private val bruiser = Cards.invent("Greenhouse Bruiser", "{G}{W}", "Creature — Elemental Warrior", """
        Trample
        This creature enters with two +1/+1 counters on it.
        Whenever one or more +1/+1 counters are put on another permanent you control, if it's the first time +1/+1 counters have been put on that permanent this turn, put a +1/+1 counter on this creature.
    """.trimIndent(), "0/0")
    private val herald = Cards.invent("Carnival Herald", "{2}{G}", "Creature — Elephant",
        "At the beginning of each end step, if a +1/+1 counter was put on a permanent under your control this turn, put a +1/+1 counter on this creature.", "2/2")
    private val torvak = Cards.invent("Torvak and Seline", "{3}{G}{G}{W}{W}", "Legendary Creature — Dinosaur Vampire", """
        Trample
        Whenever you attack, choose one —
        • Create a tapped and attacking X/X green Dinosaur creature token with trample, where X is the greatest power among other attacking creatures.
        • Create X 1/1 white Vampire creature tokens with lifelink, where X is the number of other attacking creatures.
    """.trimIndent(), "12/12")
    private val gambit = Cards.invent("Blaze Gambit", "{2}{R}", "Sorcery", """
        Choose one —
        • Blaze Gambit deals 2 damage to each creature, planeswalker, and battle.
        • Put any number of cards from your hand on the bottom of your library, then draw that many cards plus one.
    """.trimIndent())
    private val velmund = Cards.invent("Velmund, Lorekeeper", "{3}{R}{W}", "Legendary Creature — Elephant Cleric", """
        Vigilance
        At the beginning of your end step, exile target noncreature, nonland card from your graveyard. Create a 3/2 red and white Spirit creature token.
        {1}{R}{W}, {T}, Sacrifice a Spirit: Choose target card exiled with Velmund. You may cast that card this turn without paying its mana cost. If that spell would be put into a graveyard, put it on the bottom of its owner's library instead.
    """.trimIndent(), "3/5")

    private val sparkcall = Cards.invent("Pyra Sparkcall", "{R}", "Instant", "You gain 2 life.")
    private val kindled = Cards.invent("Pyra Kindled Thought", "{R}", "Instant", "You gain 3 life.")
    private val jolt = Cards.invent("Ash Jolt", "{R}", "Instant", "This spell deals 3 damage to target creature.")
    private val pup = Cards.invent("Grove Pup", "{1}{G}", "Creature — Dog", "", "2/2")
    private val elk = Cards.invent("Grove Elk", "{2}{G}", "Creature — Elk", "", "3/3")
    private val drone = Cards.invent("Compleated Drone", "{1}", "Artifact Creature — Phyrexian Construct", "", "1/1")
    private val insight = Cards.invent("Arbiter Insight", "{W}", "Instant", "You gain 1 life.")
    private val blessing = Cards.invent("Seedcore Blessing", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val stun = Cards.invent("Seedcore Stun", "{G}", "Instant", "Put a stun counter on target creature.")
    private val bigger = Cards.invent("Grove Elder Elk", "{3}", "Creature — Elk", "", "3/3")
    private val recall = Cards.invent("Lorekeeper Recall", "{2}{W}", "Instant", "You gain 3 life.")

    // CR 611.2a: one instant or sorcery spell in all from the five exiled cards; CR 707.10: the first spell is copied (once each turn)
    @Test
    fun pyraCastsOneSpellFromAmongTheExiledCards() = scenario {
        alice.battlefield(pyra, "Mountain", "Mountain", counters = listOf(CounterKind.Loyalty to 5))
        alice.library(sparkcall, kindled, "Mountain", "Mountain", "Mountain", "Island")
        alice.activates(pyra, ability = 1)
        resolveAll()
        expect {
            sparkcall.zone shouldBe Zone.Exile
            kindled.zone shouldBe Zone.Exile
            alice.library.size shouldBe 1
        }
        alice.casts(sparkcall)
        resolveAll()
        // the copy trigger gained 2 more
        expect { alice.life shouldBe 24 }
        assertFailsWith<IllegalArgumentException> { alice.casts(kindled) }
    }

    // CR 115.4, 107.3a: X of the loyalty cost, to each of up to two targets
    @Test
    fun pyraDealsXToEachOfTwoTargets() = scenario {
        alice.battlefield(pyra, counters = listOf(CounterKind.Loyalty to 5))
        bob.battlefield(pup)
        alice.activates(pyra, ability = 2, x = 2, targets = listOf("bob", pup))
        resolveAll()
        expect {
            bob.life shouldBe 18
            pup.zone shouldBe Zone.Graveyard
            pyra.counters(CounterKind.Loyalty) shouldBe 3
        }
    }

    // CR 700.4, 614.1a: it would die, so it is exiled instead
    @Test
    fun thicketOutriderIsExiledInsteadOfDying() = scenario {
        alice.battlefield(outrider, "Mountain")
        alice.hand(jolt)
        alice.casts(jolt, target = outrider)
        resolveAll()
        expect {
            outrider.zone shouldBe Zone.Exile
            alice.graveyard lacks outrider
        }
    }

    // CR 106.3, 601.2h: two mana from creatures and one from a land: no card
    @Test
    fun orlaAndBrynDoesntDrawForTwoManaFromCreatures() = scenario {
        alice.battlefield(orla, pup, "Forest")
        alice.hand(elk, "Forest")
        alice.library("Island", "Island")
        alice.casts(elk, paying = listOf(orla, pup, "Forest"))
        resolveAll()
        expect {
            elk.zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 1
        }
    }

    // CR 106.3, 601.2h: three mana from creatures spent to cast it draws a card
    @Test
    fun orlaAndBrynDrawsWithThreeCreatures() = scenario {
        alice.battlefield(orla, pup, elk)
        alice.hand(drone, bigger)
        alice.library("Island", "Island")
        alice.casts(bigger, paying = listOf(orla, pup, elk))
        resolveAll()
        expect {
            bigger.zone shouldBe Zone.Battlefield
            alice.hand has "Island"
            // the creatures have vigilance and stay a mana source each
            orla hasKeyword arcanum.api.Keyword.Vigilance.kind
        }
    }

    // CR 101.2: each player's own non-Phyrexian spells count for that player only; a Phyrexian spell isn't limited
    @Test
    fun compleatedArbiterLimitsEachPlayerSeparately() = scenario(players = 3) {
        alice.battlefield(arbiter, "Plains", "Plains", "Plains")
        bob.battlefield("Plains")
        alice.hand(insight, insight, drone)
        bob.hand(insight)
        alice.casts(insight, paying = listOf("Plains"))
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.casts(insight) }
        alice.casts(drone)
        resolveAll()
        expect { alice.battlefield has drone }
        alice.passes()
        bob.casts(insight)
        resolveAll()
        expect { bob.life shouldBe 21 }
    }

    // CR 122.6, 603.4: the first +1/+1 counters on that permanent this turn (a stun counter before them doesn't count); not the second time
    @Test
    fun greenhouseBruiserGrowsOnTheFirstPlusOneCountersOnly() = scenario {
        alice.battlefield(bruiser, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield(pup, "Forest", "Forest", "Forest")
        alice.hand(stun, blessing, blessing)
        alice.casts(stun, target = pup)
        resolveAll()
        alice.casts(blessing, target = pup)
        resolveAll()
        expect { bruiser.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3 }
        alice.casts(blessing, target = pup)
        resolveAll()
        expect {
            bruiser.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            pup.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 513.1, 603.4: a +1/+1 counter put on a permanent you control this turn; one on an opponent's doesn't count
    @Test
    fun carnivalHeraldGrowsAtTheEndStep() = scenario {
        alice.battlefield(herald, pup, "Forest")
        alice.hand(blessing)
        alice.casts(blessing, target = pup)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { herald.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    @Test
    fun carnivalHeraldIgnoresAnOpponentsPermanent() = scenario {
        alice.battlefield(herald, "Forest")
        bob.battlefield(pup)
        alice.hand(blessing)
        alice.casts(blessing, target = pup)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { herald.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 508.4: the token enters attacking; CR 107.3c: X is the greatest power among the other attackers (Torvak isn't attacking)
    @Test
    fun torvakAndSelineCreatesAnAttackingDinosaur() = scenario {
        alice.battlefield(torvak, pup, elk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pup, elk)
        resolveAll()
        modes(0)
        resolveAll()
        val dinosaurs = tokens("Dinosaur Token")
        assertEquals(1, dinosaurs.size)
        val dinosaur = dinosaurs.single()
        assertTrue(state.combat?.attackers.orEmpty().any { it.id == dinosaur })
        assertTrue(state.obj(dinosaur)!!.tapped)
        expect { CardRef(dinosaur, "Dinosaur Token").power shouldBe 3 }
    }

    @Test
    fun torvakAndSelineCreatesAVampireForEachOtherAttacker() = scenario {
        alice.battlefield(torvak, pup, elk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(torvak, pup, elk)
        resolveAll()
        modes(1)
        resolveAll()
        assertEquals(2, tokens("Vampire Token").size)
    }

    // CR 401.4: any number of cards on the bottom; CR 121.2: that many plus one drawn
    @Test
    fun blazeGambitCyclesCardsPlusOne() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(gambit, pup, elk)
        alice.library("Island", "Island", "Island", "Plains")
        alice.casts(gambit, modes = listOf(1))
        resolveAll()
        alice.chooses(pup, elk)
        // the owner orders the cards put on the bottom (CR 401.4)
        alice.chooses(pup, elk)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.hand has "Island"
            alice.library.names shouldBe listOf("Plains", pup, elk)
        }
    }

    // CR 607.2a: the card its end step ability exiled; CR 601.2a, 118.9: cast free this turn; CR 614.1a: to the bottom of the library instead
    @Test
    fun velmundCastsTheExiledCardFree() = scenario {
        alice.battlefield(velmund, "Mountain", "Plains", "Plains")
        alice.graveyard(recall)
        alice.library("Island", "Island", "Island")
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            recall.zone shouldBe Zone.Exile
            alice.battlefield has "Spirit Token"
        }
        alice.activates(velmund, target = recall, choosing = listOf("Spirit Token"))
        resolveAll()
        alice.casts(recall)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.library.names.last() shouldBe recall
            alice.graveyard lacks recall
        }
    }
}
