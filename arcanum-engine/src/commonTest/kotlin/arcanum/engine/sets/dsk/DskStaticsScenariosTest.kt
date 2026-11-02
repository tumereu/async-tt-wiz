package arcanum.engine.sets.dsk

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Duskmourn's static abilities in play: combat restrictions and requirements, untapping in other players' untap steps,
 * life at 0, and hexproof and ward set aside. */
class DskStaticsScenariosTest {
    private val hunter = Cards.invent("Dread of the Chase", "{1}{R}{R}", "Enchantment Creature — Nightmare", """
        Haste
        This creature must be blocked if able.
    """, "4/2")
    private val loner = Cards.invent("Clinging Brute", "{2}{W}", "Creature — Beast", "This creature can't attack or block alone.", "4/4")
    private val vigil = Cards.invent("Restless Vigil", "{2}{U}", "Enchantment", "Untap each creature you control during each other player's untap step.")
    private val grimoire = Cards.invent("Undying Ledger", "{5}{U}", "Artifact",
        "You have no maximum hand size and don't lose the game for having 0 or less life.")
    private val exposure = Cards.invent("Nowhere Safe", "{1}{B}", "Enchantment",
        "Creatures your opponents control can be the targets of spells and abilities as though they didn't have hexproof. Ward abilities of those creatures don't trigger.")
    private val hexproofBeast = Cards.invent("Veiled Stag", "{1}{G}", "Creature — Elk", "Hexproof", "2/2")
    private val wardBeast = Cards.invent("Warded Stag", "{1}{G}", "Creature — Elk", "Ward {2}", "2/2")
    private val bolt = Cards.invent("Searing Jolt", "{R}", "Instant", "Searing Jolt deals 2 damage to target creature.")

    /** Bob attacks alice with [attacker] in his turn; the game waits for alice's blockers. */
    private fun Scenario.bobAttacksWith(attacker: String) {
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(attacker)
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
    }

    // CR 509.1c: a requirement that it be blocked; a declaration obeying it is required when one can
    @Test
    fun mustBeBlockedIfAble() = scenario {
        bob.battlefield(hunter)
        alice.battlefield("Elvish Seer")
        bobAttacksWith(hunter)
        assertFailsWith<IllegalArgumentException> { alice.blocks() }
        alice.blocks("Elvish Seer" to hunter)
        expect { state.combat!!.blockers.size shouldBe 1 }
    }

    // CR 508.1c: it attacks only with another attacker
    @Test
    fun cantAttackAlone() = scenario {
        alice.battlefield(loner, "Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(loner) }
        alice.attacks(loner, "Elvish Seer")
        expect { state.combat!!.attackers.size shouldBe 2 }
    }

    // CR 509.1b: it blocks only with another blocker
    @Test
    fun cantBlockAlone() = scenario {
        bob.battlefield("Elvish Seer")
        alice.battlefield(loner, "Elvish Seer")
        bobAttacksWith("Elvish Seer")
        assertFailsWith<IllegalArgumentException> { alice.blocks(loner to "Elvish Seer") }
    }

    // CR 502.3: permanents untap in their controller's untap step; this one also in each other player's
    @Test
    fun untapsInOtherPlayersUntapSteps() = scenario {
        alice.battlefield(vigil)
        alice.battlefield("Elvish Seer", tapped = true)
        bob.library("Swamp")
        turn(number = 1, active = alice, step = TurnStep.End)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Elvish Seer".tapped shouldBe false }
    }

    // CR 704.5a doesn't apply to a player who doesn't lose for having 0 or less life
    @Test
    fun noLossAtZeroLife() = scenario {
        alice.battlefield(grimoire)
        alice.life = 0
        settle()
        assertFalse(state.player(alice.id).lost)
    }

    // CR 702.11b: without the enchantment, hexproof keeps it from being targeted
    @Test
    fun hexproofWithoutTheEnchantment() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(bolt, bolt)
        bob.battlefield(hexproofBeast)
        assertFailsWith<Throwable> { alice.casts(bolt, target = hexproofBeast) }
    }

    // CR 702.11b: with the enchantment out, the hexproof creature can be targeted
    @Test
    fun hexproofCreatureTargeted() = scenario {
        alice.battlefield("Mountain", exposure)
        alice.hand(bolt)
        bob.battlefield(hexproofBeast)
        alice.casts(bolt, target = hexproofBeast)
        allPass()
        expect { bob.graveyard has hexproofBeast }
    }

    // CR 702.21a: a ward ability that doesn't trigger
    @Test
    fun wardDoesntTrigger() = scenario {
        alice.battlefield("Mountain", exposure)
        alice.hand(bolt)
        bob.battlefield(wardBeast)
        alice.casts(bolt, target = wardBeast)
        expect { stack.size shouldBe 1 }
        allPass()
        expect { bob.graveyard has wardBeast }
    }
}
