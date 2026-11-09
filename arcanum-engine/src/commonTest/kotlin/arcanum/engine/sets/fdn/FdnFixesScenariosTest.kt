package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.sets.spm.drive
import arcanum.engine.sets.spm.targets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Engine fixes found while building FDN (the fix chunk). The invented names carry oracle templates. */
class FdnFixesScenariosTest {
    private val charm = Cards.invent("Scrapyard Charm", "{1}{R}", "Sorcery", """
        Choose one —
        • Destroy target artifact.
        • You gain 3 life.
    """)
    private val tinker = Cards.invent("Fixes Tinker", "{1}", "Artifact", "{T}: You gain 1 life.")

    // CR 700.2a: a mode that couldn't choose a legal target can't be chosen: with no artifact in play only the second is available
    @Test
    fun modeWithoutLegalTargetIsUnavailable() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(charm)

        alice.casts(charm)
        assertEquals(listOf(false, true), decision<Decision.Modes>().options.map { it.available })
        answer(Answer.Modes(listOf(1)))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()

        expect { alice.life shouldBe 23 }
    }

    // CR 700.2a: with an artifact to target, both modes are available
    @Test
    fun modeWithLegalTargetIsAvailable() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(charm)
        bob.battlefield(tinker)

        alice.casts(charm)
        assertEquals(listOf(true, true), decision<Decision.Modes>().options.map { it.available })
    }

    private val blot = Cards.invent("Fixes Gateway", "{3}{W}{B}", "Legendary Creature — Human Villain", """
        When this creature enters, exile up to one target nonland permanent and up to one target nonland permanent card from a graveyard.
        When this creature dies, put him on the bottom of his owner's library. If you do, return the exiled cards to their owners' hands.
    """, "4/4")
    private val destroy = Cards.invent("Fixes Sudden End", "{1}{B}", "Instant", "Destroy target creature.")
    private val scour = Cards.invent("Fixes Scour", "{B}", "Instant", "Exile target card from a graveyard.")
    private val ogre = Cards.invent("Fixes Ogre", "{3}", "Creature — Ogre", "", "3/3")

    // CR 608.2c: "if you do" asks whether the card was put on the bottom: exiled in response, it isn't, and the exiled cards stay exiled
    @Test
    fun ifYouDoNeedsTheMoveToHappen() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(blot, destroy)
        bob.battlefield(tinker, "Swamp")
        bob.graveyard(ogre)
        bob.hand(scour)
        alice.casts(blot, paying = listOf("Plains", "Plains", "Plains", "Swamp", "Swamp"))
        drive({ d: Decision -> d is Decision.Targets && (d as Decision.Targets).slot == "permanent" } to { targets(tinker) },
            { d: Decision -> d is Decision.Targets } to { targets(ogre) })
        alice.casts(destroy, target = blot, paying = listOf("Swamp#2", "Swamp#3"))
        alice.passes()
        bob.passes()
        // the dies trigger is on the Stack: bob exiles the creature card from alice's graveyard first
        alice.passes()
        bob.casts(scour, target = blot)
        resolveAll()
        expect {
            bob.exile has tinker
            bob.exile has ogre
        }
        assertEquals(0, state.player(alice.id).library.size)
    }

    private val wisp = Cards.invent("Fixes Wisp", "{1}{W}", "Creature — Spirit", """
        Flying
        If Fixes Wisp would be put into a graveyard from anywhere, exile it instead.
    """, "2/2")
    private val refusal = Cards.invent("Fixes Refusal", "{U}{U}", "Instant", "Counter target spell.")

    // CR 113.6b, 614.1a: "from anywhere" makes the ability work off the battlefield: countered on the Stack, it is exiled
    @Test
    fun exileInsteadWorksOnTheStack() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(wisp)
        bob.battlefield("Island", "Island")
        bob.hand(refusal)
        alice.casts(wisp)
        alice.passes()
        bob.casts(refusal, target = wisp)
        resolveAll()
        expect {
            alice.exile has wisp
            alice.graveyard lacks wisp
        }
    }

    private val springtide = Cards.invent("Fixes Springtide", "{4}{G}", "Enchantment", """
        If an effect would create one or more tokens under your control, it creates twice that many of those tokens instead.
        If an effect would put one or more counters on a permanent you control, it puts twice that many of those counters on that permanent instead.
    """)
    private val saga = Cards.invent("The Fixes Saga", "{2}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — You gain 1 life.
        II — You gain 2 life.
        III — You gain 4 life.
    """)
    private val imp = Cards.invent("Fixes Imp", "{B}", "Creature — Imp", """
        Wither
        {T}: This creature deals 1 damage to target creature.
    """, "1/1")
    private val ox = Cards.invent("Fixes Ox", "{3}", "Creature — Ox", "", "4/4")

    // CR 714.3a, 614.1c, 122.6: a Saga's "enters with a lore counter" is a replacement effect that puts a counter on a permanent
    // you control, so it puts two: chapters I and II both trigger (CR 714.2c)
    @Test
    fun sagaEntersWithDoubledLoreCounters() = scenario {
        alice.battlefield(springtide, "Plains", "Plains", "Plains")
        alice.hand(saga)
        alice.casts(saga)
        resolveAll()
        expect {
            saga.counters(CounterKind.Lore) shouldBe 2
            alice.life shouldBe 23
        }
    }

    // CR 702.80a, 120.3d, 122.6: wither damage makes its source's controller put -1/-1 counters on the creature, on a permanent
    // controlled by the one whose counter-doubling effect applies: one damage puts two counters
    @Test
    fun witherCountersAreDoubled() = scenario {
        alice.battlefield(imp)
        bob.battlefield(springtide, ox)
        turn(number = 3)
        alice.activates(imp, target = ox)
        resolveAll()
        expect { ox.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2 }
    }

    private val walker = Cards.invent("Fixes Sage", "{2}{G}", "Legendary Planeswalker — Sage", "[+1]: You gain 1 life.", loyalty = "3")

    // CR 306.5b, 614.1c, 122.6: a planeswalker's "enters with loyalty counters" is a replacement effect putting counters on a permanent you
    // control, so it enters with twice as many
    @Test
    fun planeswalkerEntersWithDoubledLoyalty() = scenario {
        alice.battlefield(springtide, "Forest", "Forest", "Forest")
        alice.hand(walker)
        alice.casts(walker)
        resolveAll()
        expect { walker.counters(CounterKind.Loyalty) shouldBe 6 }
    }

    private val orb = Cards.invent("Crimson Orb of Wyrms", "{2}{R}", "Artifact",
        "{T}: Add {R}. If that mana is spent on a Dragon creature spell, it gains haste until end of turn.")
    private val whelp = Cards.invent("Fixes Whelp", "{R}", "Creature — Dragon", "", "1/1")
    private val goblin = Cards.invent("Fixes Goblin", "{R}", "Creature — Goblin", "", "1/1")

    // CR 106.6a: mana from the Orb spent on a Dragon creature spell gives it haste: it can attack the turn it is cast
    @Test
    fun orbMakesADragonHasty() = scenario {
        alice.battlefield(orb)
        alice.hand(whelp)
        alice.casts(whelp, paying = listOf(orb))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(whelp)
        expect { whelp.tapped shouldBe true }
    }

    // CR 106.6a: spent on a creature that isn't a Dragon, the mana gives nothing
    @Test
    fun orbGivesOtherCreaturesNothing() = scenario {
        alice.battlefield(orb)
        alice.hand(goblin)
        alice.casts(goblin, paying = listOf(orb))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.attacks(goblin) }
    }
}
