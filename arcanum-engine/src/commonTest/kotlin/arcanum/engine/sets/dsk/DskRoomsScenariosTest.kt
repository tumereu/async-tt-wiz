package arcanum.engine.sets.dsk

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Duskmourn Rooms in play (CR 709.5): casting a door, unlocking the other, locked halves, eerie and full unlocks. */
class DskRoomsScenariosTest {
    private val room = Cards.inventFaces("Split",
        face("Sunlit Foyer", "{1}{W}", "Enchantment — Room", "When you unlock this door, you gain 3 life.", null, null, null),
        face("Shadowed Pantry", "{2}{B}", "Enchantment — Room", "When you unlock this door, draw a card.", null, null, null),
    )
    private val watcher = Cards.invent("Creaking Watcher", "{1}{G}", "Creature — Spirit",
        "Eerie — Whenever an enchantment you control enters and whenever you fully unlock a Room, put a +1/+1 counter on this creature.", "1/1")

    // CR 709.5d, 709.5h: the half cast is unlocked as it enters, and its "when you unlock this door" triggers
    @Test
    fun castingADoorUnlocksIt() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(room)
        alice.casts(room, way = CastWay(face = 0))
        allPass()
        allPass()
        expect {
            alice.life shouldBe 23
            assertEquals("Sunlit Foyer", "Sunlit Foyer".view.characteristics!!.name)
        }
    }

    // CR 709.5: a locked half has no name, mana cost or rules text; CR 709.5d: put onto the battlefield, neither is unlocked
    @Test
    fun lockedDoorsHaveNoText() = scenario {
        alice.battlefield(room)
        expect {
            assertTrue(room.view.characteristics!!.abilities.isEmpty())
            assertEquals(null, room.view.characteristics!!.name)
        }
    }

    // CR 709.5e: paying a locked door's mana cost unlocks it, as a special action in a main phase of your turn with the
    // Stack empty; CR 709.5i: unlocking the second door fully unlocks the Room
    @Test
    fun unlockingTheOtherDoorFullyUnlocks() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", watcher)
        alice.library("Island")
        alice.hand(room)
        alice.casts(room, way = CastWay(face = 0), paying = listOf("Plains", "Plains"))
        allPass()
        answer(Answer.Order(listOf(0, 1))) // the eerie trigger for the enchantment entering and the door's trigger
        allPass()
        allPass()
        expect { watcher.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
        alice.takesSpecialAction(room, "unlock-right", paying = listOf("Swamp", "Swamp", "Swamp"))
        answer(Answer.Order(listOf(0, 1))) // the eerie trigger for the full unlock and the door's trigger
        allPass()
        allPass()
        expect {
            alice.hand has "Island"
            watcher.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            assertEquals("Sunlit Foyer // Shadowed Pantry", room.view.characteristics!!.name)
        }
    }

    // CR 709.5e: only during a main phase of your turn
    @Test
    fun unlockingOnlyInYourMainPhase() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", room)
        turn(number = 1, active = alice, step = TurnStep.Upkeep)
        assertFailsWith<Throwable> { alice.takesSpecialAction(room, "unlock-right") }
    }
}
