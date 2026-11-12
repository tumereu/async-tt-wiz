package arcanum.engine.dungeon

import arcanum.api.Designation
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Venturing into the dungeon (CR 701.49, 309) and the initiative (CR 726), with invented dungeon cards. */
class VentureTest {
    private val crypt = Cards.invent("Venture Test Crypt", null, "Dungeon", """
        Crypt Door — You gain 1 life. (→ Bone Hall or Dark Well)
        Bone Hall — Each opponent loses 1 life. (→ Final Vault)
        Dark Well — Draw a card. (→ Final Vault)
        Final Vault — You gain 3 life.
    """.trimIndent())
    private val undercity = Cards.invent("Venture Test Undercity", null, "Dungeon — Undercity", """
        Sewer Gate — You gain 2 life. (→ Drowned Depths)
        Drowned Depths — Each opponent loses 2 life.
    """.trimIndent())
    private val map = Cards.invent("Venture Test Map", "{W}", "Sorcery", "Venture into the dungeon.")
    private val lantern = Cards.invent("Venture Test Lantern", "{W}", "Instant", "Venture into the dungeon.")
    private val vanguard = Cards.invent("Venture Test Vanguard", "{1}{W}", "Creature — Human Soldier", """
        When this creature enters, you take the initiative.
    """.trimIndent(), "2/2")
    private val raider = Cards.invent("Venture Test Raider", "{1}{R}", "Creature — Goblin", "", "2/1")

    private fun Scenario.dungeonOf(name: String) = state.command.mapNotNull { state.obj(it) }.firstOrNull { it.definition?.faces?.first()?.name == name }

    // CR 701.49a, b, 309.4a, c: the first venture brings a dungeon in with the marker on its topmost room, whose ability triggers;
    // the next moves along an arrow its owner chooses; CR 309.6, 309.7: on the bottommost room, once its ability has resolved,
    // the dungeon is removed from the game and completed
    @Test
    fun throughADungeon() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(map, map, map)
        alice.outside(crypt)
        alice.casts(map)
        allPass()
        assertEquals(0, dungeonOf(crypt)?.let { Dungeons.room(it) })
        allPass()
        expect { alice.life shouldBe 21 }
        alice.casts(map)
        allPass()
        val d = pending
        assertTrue(d is Decision.ChooseOption && d.options == listOf("Bone Hall", "Dark Well"), "Choose a room: $d")
        answer(Answer.Option("Bone Hall"))
        allPass()
        expect { bob.life shouldBe 19 }
        alice.casts(map)
        allPass()
        assertEquals(3, dungeonOf(crypt)?.let { Dungeons.room(it) })
        allPass()
        expect { alice.life shouldBe 24 }
        assertEquals(null, dungeonOf(crypt))
        assertEquals(1, Dungeons.completed(state, alice.id))
        assertTrue(state.player(alice.id).outside.any { state.obj(it)?.definition?.faces?.first()?.name == crypt })
    }

    // CR 701.49c: venturing from the bottommost room (its room ability still on the Stack, so not yet completed by CR 309.6)
    // completes the dungeon and starts a new one
    @Test
    fun ventureFromTheBottommostRoom() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(map, map, lantern)
        alice.outside(undercity)
        alice.casts(map)
        allPass()
        allPass()
        alice.casts(map)
        allPass()
        // Drowned Depths' ability waits on the Stack; venture again in response
        alice.casts(lantern)
        allPass()
        assertEquals(1, Dungeons.completed(state, alice.id))
        assertEquals(0, dungeonOf(undercity)?.let { Dungeons.room(it) })
        allPass()
        allPass()
        expect {
            alice.life shouldBe 24
            bob.life shouldBe 18
        }
    }

    // CR 726.2, 701.49d: taking the initiative ventures into Undercity (only an Undercity dungeon), and so does the beginning of the
    // upkeep of the player who has it; combat damage to that player passes it to the attacker's controller, who ventures too
    @Test
    fun theInitiative() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(vanguard)
        alice.outside(crypt, undercity)
        alice.library("Plains", "Plains")
        bob.library("Island", "Island")
        bob.outside(undercity)
        bob.battlefield(raider)
        alice.casts(vanguard)
        allPass()
        allPass()
        assertTrue(Designation.Initiative in state.player(alice.id).designations)
        // The initiative's trigger ventures into Undercity, then Sewer Gate's ability resolves
        allPass()
        allPass()
        expect { alice.life shouldBe 22 }
        assertEquals(null, dungeonOf(crypt))
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(raider)
        advanceTo(TurnStep.EndOfCombat, turn = 2)
        allPass()
        assertTrue(Designation.Initiative in state.player(bob.id).designations)
        assertTrue(Designation.Initiative !in state.player(alice.id).designations)
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 22
        }
        assertEquals(Zone.Command, state.command.mapNotNull { state.obj(it) }.firstOrNull { it.owner == bob.id }?.zone)
    }

    // CR 701.49a, d: a dungeon that "you can't enter unless you venture into Undercity" is never chosen by a plain venture, while
    // the initiative's venture into Undercity chooses it (the rooms' arrows written as card data writes them, "(Leads to: …)")
    @Test
    fun undercityOnlyByVentureIntoUndercity() = scenario {
        val restricted = Cards.invent("Venture Test Sunken City", null, "Dungeon — Undercity", """
            You can't enter this dungeon unless you "venture into Undercity."
            Flooded Stair — You gain 2 life. (Leads to: Coral Hall, Salt Vault)
            Coral Hall — Each opponent loses 1 life. (Leads to: Abyss Throne)
            Salt Vault — Draw a card. (Leads to: Abyss Throne)
            Abyss Throne — You gain 5 life.
        """.trimIndent())
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(map, vanguard)
        alice.library("Plains", "Plains")
        alice.outside(restricted, crypt)
        alice.casts(map)
        allPass()
        // Only the crypt could be chosen: it is entered at once, without a choice
        assertEquals(0, dungeonOf(crypt)?.let { Dungeons.room(it) })
        assertEquals(null, dungeonOf(restricted))
        allPass()
        expect { alice.life shouldBe 21 }
        // A venture into Undercity while the crypt is in the command zone moves its marker instead (CR 701.49d)
        alice.casts(vanguard)
        allPass()
        allPass()
        allPass()
        val d = pending
        assertTrue(d is Decision.ChooseOption && d.options == listOf("Bone Hall", "Dark Well"), "Choose a room: $d")
        answer(Answer.Option("Dark Well"))
        allPass()
        assertEquals(2, dungeonOf(crypt)?.let { Dungeons.room(it) })
        assertEquals(null, dungeonOf(restricted))
    }

    // CR 701.49d: with no dungeon in the command zone, venturing into Undercity enters the dungeon only it may enter
    @Test
    fun theInitiativeEntersTheRestrictedDungeon() = scenario {
        val restricted = Cards.invent("Venture Test Drowned Keep", null, "Dungeon — Undercity", """
            You can't enter this dungeon unless you "venture into Undercity."
            Flooded Stair — You gain 2 life. (Leads to: Abyss Throne)
            Abyss Throne — You gain 5 life.
        """.trimIndent())
        alice.battlefield("Plains", "Plains")
        alice.hand(vanguard)
        alice.outside(crypt, restricted)
        alice.casts(vanguard)
        allPass()
        allPass()
        allPass()
        allPass()
        expect { alice.life shouldBe 22 }
        assertEquals(0, dungeonOf(restricted)?.let { Dungeons.room(it) })
        assertEquals(null, dungeonOf(crypt))
    }
}
