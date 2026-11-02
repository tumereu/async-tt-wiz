package arcanum.engine.review

import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3 findings #1 and #2, around the fixes: a copy of a spell is a spell of its own (CR 707.10), objects new with a
 * look-back event (CR 603.10a, 603.6c). Redirection (#3, CR 614.9) is covered in [Review3CostsTest].
 */
class Review3RulesFixesTest {
    /** "Whenever another creature card is put into your graveyard from anywhere, you may pay {B}. If you do, return this
     * card from your graveyard to the battlefield." Works from the graveyard (CR 113.6k); "from anywhere" isn't a
     * leaves-the-battlefield ability (CR 603.6c). */
    private val graveCaller = Cards.invent("Review3 Grave Caller", "{B}{B}", "Creature — Spirit",
        "Whenever another creature card is put into your graveyard from anywhere, you may pay {B}. If you do, return this card from your graveyard to the battlefield.",
        "1/1")

    // ---- #1: copies of spells

    /** The copy deals its damage itself (CR 707.10: a spell of its own, not its original card): the damage's source is
     * the copy, seen in history as Storm Spark; the original is still on the Stack. */
    // CR 707.10
    @Test
    fun aCopysDamageIsDealtByTheCopy() = scenario {
        alice.hand("Storm Spark", "Flash of Mirroring")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Flash of Mirroring", target = "Storm Spark")
        resolve()
        if (pending is Decision.May) no()
        resolve()
        val damage = applied.filterIsInstance<Change.Damage>().single().dealt.single()
        assertTrue(damage.source is ObjectId.StackItem, "dealt by ${damage.source}")
        assertEquals("Storm Spark", damage.sourceSeen?.characteristics?.name)
        expect {
            bob.life shouldBe 17
            stack.names shouldBe listOf("Storm Spark")
        }
        resolve()
        expect {
            bob.life shouldBe 14
            alice.graveyard has "Storm Spark"
        }
    }

    /** A copy of a spell cast with flashback leaves the Stack without moving the card (CR 707.10a); the original is
     * exiled as flashback says (CR 702.34a) and not put into the graveyard by its copy's resolution. */
    // CR 707.10a, 702.34a
    @Test
    fun aCopyOfAFlashedBackSpellLeavesTheCardAlone() = scenario {
        alice.graveyard("Warden's Reclamation", "Tinkered Mite", "Gearwing Glider")
        alice.hand("Flash of Mirroring")
        alice.castsWithoutCosts("Warden's Reclamation", target = "Tinkered Mite")
        alice.castsWithoutCosts("Flash of Mirroring", target = "Warden's Reclamation")
        resolve()
        if (pending is Decision.May) no()
        resolve()
        expect { stack.names shouldBe listOf("Warden's Reclamation") }
        if (pending is Decision.May) no()
        resolve()
        if (pending is Decision.May) no()
        expect {
            alice.exile has "Warden's Reclamation"
            stack.isEmpty()
        }
    }

    // ---- #2: new objects and look-back events

    /** Netherbound Turncoat already in the graveyard sees both creatures dying at once, the other Turncoat among them
     * (its ability existed before the event); the dying Turncoat's new graveyard object sees neither (CR 603.10a). */
    // CR 603.10a, 113.6k
    @Test
    fun aTurncoatAlreadyInTheGraveyardSeesBothDeaths() = scenario {
        alice.graveyard("Netherbound Turncoat")
        alice.battlefield("Netherbound Turncoat", "Tinkered Mite", "Swamp")
        bob.hand("Blazing Confluence")
        bob.castsWithoutCosts("Blazing Confluence", modes = listOf(0, 0, 0))
        resolve()
        expect {
            alice.graveyard.names.count { it == "Netherbound Turncoat" } shouldBe 2
            stack.size shouldBe 2
        }
    }

    /** An ability that triggers on a card put into a graveyard "from anywhere" isn't a leaves-the-battlefield ability
     * (CR 603.6c): Grave Caller dying with Tinkered Mite sees the Mite from the graveyard, but not its own move, which
     * was its earlier object's (CR 400.7): one trigger. */
    // CR 603.6c, 400.7
    @Test
    fun aFromAnywhereGraveyardAbilitySeesTheOthersButNotItself() = scenario {
        alice.battlefield(graveCaller, "Tinkered Mite", "Swamp")
        bob.hand("Blazing Confluence")
        bob.castsWithoutCosts("Blazing Confluence", modes = listOf(0, 0, 0))
        resolve()
        expect {
            alice.graveyard has graveCaller
            stack.size shouldBe 1
        }
        resolve()
        yes()
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        expect { alice.battlefield has graveCaller }
    }

    /** Grave Caller dying alone: nothing else was put into the graveyard, and its own move isn't "another": no trigger. */
    // CR 400.7, 603.6c
    @Test
    fun aFromAnywhereGraveyardAbilityDoesntSeeItsOwnDeath() = scenario {
        alice.battlefield(graveCaller, "Swamp")
        bob.hand("Storm Spark")
        bob.castsWithoutCosts("Storm Spark", target = graveCaller)
        resolve()
        expect {
            alice.graveyard has graveCaller
            stack.isEmpty()
        }
        assertTrue(pending == null, "nothing to decide: $pending")
    }
}
