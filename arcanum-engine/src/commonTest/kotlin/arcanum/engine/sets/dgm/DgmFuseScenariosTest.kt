package arcanum.engine.sets.dgm

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Dragon's Maze split cards with fuse in play (CR 702.102, 709): each half alone, or both from the hand as one spell. */
class DgmFuseScenariosTest {
    private val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
    private val boneMarrow = Cards.inventFaces("Split",
        face("Bone", "{3}{B}{G}", "Sorcery", "Exile target creature card from a graveyard. Put X +1/+1 counters on target creature, where X is the power of the card you exiled.\n$fuse", null, null, null),
        face("Marrow", "{R}{G}", "Sorcery", "Target creature you control deals damage equal to its power to any target.\n$fuse", null, null, null),
    )
    private val nearAfar = Cards.inventFaces("Split",
        face("Near", "{1}{U}", "Instant", "Return target creature to its owner's hand.\n$fuse", null, null, null),
        face("Afar", "{2}{B}", "Instant", "Target player sacrifices a creature of their choice.\n$fuse", null, null, null),
    )
    private val laborStrife = Cards.inventFaces("Split",
        face("Labor", "{2}{B}", "Sorcery", "Target player draws two cards and loses 2 life.\n$fuse", null, null, null),
        face("Strife", "{2}{R}", "Sorcery", "Strife deals damage to target player equal to the number of cards in that player's hand.\n$fuse", null, null, null),
    )
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val giant = Cards.invent("Hill Brute", "{3}{R}", "Creature — Giant", "", "4/4")
    private val counter = CounterKind.PowerToughness(1, 1)
    private val lands = arrayOf("Island", "Island", "Swamp", "Swamp", "Swamp")

    /** One half alone: its own cost and instructions. */
    // CR 709.3
    @Test
    fun oneHalf() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(nearAfar)
        bob.battlefield(bear)
        alice.casts(nearAfar, target = bear, way = CastWay(face = 0))
        allPass()
        expect { bob.hand.names shouldBe listOf(bear) }
    }

    /** Fused: both halves' costs are paid, the left half's instructions then the right half's. */
    // CR 702.102a, c, d
    @Test
    fun bothHalves() = scenario {
        alice.battlefield(*lands)
        alice.hand(nearAfar)
        bob.battlefield(bear, giant)
        alice.casts(nearAfar, targets = listOf(bear, "bob"), way = CastWay(fused = true))
        allPass()
        expect {
            bob.hand.names shouldBe listOf(bear)
            bob.graveyard.names shouldBe listOf(giant)
            listOf(0, 1).all { card("Island", it).tapped } shouldBe true
            listOf(0, 1, 2).all { card("Swamp", it).tapped } shouldBe true
        }
    }

    /** On the Stack a fused split spell has both halves' characteristics: its mana value is the total. */
    // CR 709.4d
    @Test
    fun fusedCharacteristics() = scenario {
        alice.battlefield(*lands)
        alice.hand(nearAfar)
        bob.battlefield(bear, giant)
        alice.casts(nearAfar, targets = listOf(bear, "bob"), way = CastWay(fused = true))
        val onStack = view()[state.stack.last()]!!.characteristics!!
        assertEquals("Near // Afar", onStack.name)
        assertEquals(arcanum.api.ManaCost.parse("{1}{U}{2}{B}"), onStack.manaCost)
    }

    /** Each half keeps its own targets even where both halves say "target creature" or "target player". */
    // CR 702.102d, 115.1
    @Test
    fun halvesWithTheSameSlotNames() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain")
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        bob.hand("Forest")
        alice.hand(laborStrife)
        alice.casts(laborStrife, targets = listOf("alice", "bob"), way = CastWay(fused = true))
        allPass()
        expect {
            alice.hand.names.size shouldBe 2
            alice.life shouldBe 18
            bob.life shouldBe 19
        }
    }

    /** Bone's counters and Marrow's damage: the right half's "target creature" is its own. */
    // CR 702.102d, 608.2h
    @Test
    fun boneMarrowFused() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest", "Forest", "Mountain", "Forest", "Forest", bear)
        alice.graveyard(giant)
        bob.battlefield(Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4"))
        alice.hand(boneMarrow)
        alice.casts(boneMarrow, targets = listOf(giant, bear, bear, "Wide Ox"), way = CastWay(fused = true))
        allPass()
        expect {
            bear.counters(counter) shouldBe 4
            bob.graveyard.names shouldBe listOf("Wide Ox")
            alice.exile.names shouldBe listOf(giant)
        }
    }

    /** Only from the hand: from elsewhere there is no fused way to cast it. */
    // CR 702.102a
    @Test
    fun fusedOnlyFromTheHand() = scenario {
        alice.battlefield(*lands)
        alice.graveyard(nearAfar)
        assertFailsWith<IllegalArgumentException> { alice.casts(nearAfar, way = CastWay(fused = true)) }
    }

    /** A sorcery half makes the fused spell a sorcery: not in another player's turn. */
    // CR 709.4d, 307.1
    @Test
    fun fusedSorceryTiming() = scenario {
        turn(2, active = bob, priority = alice)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain")
        alice.hand(laborStrife)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.way.fused })
    }

    /** Instants both: offered fused at instant speed. */
    // CR 702.102a
    @Test
    fun fusedInstantsOffered() = scenario {
        turn(2, active = bob, priority = alice)
        alice.battlefield(*lands)
        alice.hand(nearAfar)
        bob.battlefield(bear)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Cast && it.way.fused })
    }
}
