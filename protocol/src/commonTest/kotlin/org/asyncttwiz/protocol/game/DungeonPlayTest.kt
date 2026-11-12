package org.asyncttwiz.protocol.game

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Inspect
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.api.TurnStep
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LOB-117, CR 309, 701.49, 726: dungeon cards as a player's device brings them (card data as the catalog writes it, the arrows
 * "(Leads to: …)", Undercity's restriction, the reminder face of a double-faced token dropped), played through the server's rules
 * at protocol level: a venture enters the dungeon a plain venture may enter, moves through its rooms by the owner's choice and
 * completes it; the initiative enters the Undercity; an undo across room moves gives back the earlier state exactly. Invented
 * dungeons only.
 */
class DungeonPlayTest {
    private fun face(id: Int, name: String, type: String, text: String) =
        DefinitionFace("00000000-0000-5000-8000-0000000000${id.toString().padStart(2, '0')}", name, typeLine = type, rulesText = text)

    /** A plain dungeon: two ways down from its first room. */
    private val crypt = CardDefinition("Dungeon Test Crypt", listOf(face(1, "Dungeon Test Crypt", "Dungeon", """
        Crypt Door — You gain 1 life. (Leads to: Bone Hall, Dark Well)
        Bone Hall — Each opponent loses 1 life. (Leads to: Final Vault)
        Dark Well — You gain 2 life. (Leads to: Final Vault)
        Final Vault — You gain 3 life.
    """.trimIndent())), provider = "test", token = true)

    /** An Undercity printed as a double-faced token with a reminder face, as card data has it. */
    private val sunken = CardDefinition("Dungeon Test Sunken City // The Claim", listOf(
        face(2, "Dungeon Test Sunken City", "Dungeon — Undercity", """
            You can't enter this dungeon unless you "venture into Undercity."
            Flooded Stair — You gain 4 life. (Leads to: Abyss Throne)
            Abyss Throne — You gain 5 life.
        """.trimIndent()).copy(otherFaceIds = listOf("00000000-0000-5000-8000-000000000003")),
        face(3, "The Claim", "Card", "(You can take the initiative.)").copy(otherFaceIds = listOf("00000000-0000-5000-8000-000000000002"))),
        provider = "test", layout = "double_faced_token", token = true)

    private val map = Cards.invent("Dungeon Test Map", "{0}", "Sorcery", "Venture into the dungeon.")
    private val claim = Cards.invent("Dungeon Test Claim", "{0}", "Instant", "You take the initiative.")

    /** The dungeons as Alice's device brings them, parsed as the server parses them. */
    private val brought = listOf(crypt, sunken).map { requireNotNull(OutsideCards.dungeon(it)) { "${it.name} is a dungeon" } }
    private val outside = brought.map { requireNotNull(GameCards.parse(it.definition).definition) }

    private fun server(spell: String) = AsyncServer(EngineGames(5, List(40) { spell } to List(40) { "Island" }, outside = outside))

    private fun GameObject.named() = definition?.faces?.first()?.name
    private fun AsyncServer.dungeon(): GameObject? = state().engine.let { s -> s.command.mapNotNull { s.obj(it) }.firstOrNull { it.owner == alice } }
    private fun AsyncServer.room(): String? = dungeon()?.let { d -> Inspect.ventureRoom(state().engine, d.id)?.let { d.definition!!.faces.first().rooms[it].name } }
    private fun AsyncServer.life(p: PlayerId) = state().engine.player(p).life
    private fun AsyncServer.hand(name: String): ObjectId = state().engine.player(alice).hand.first { state().engine.obj(it)?.named() == name }

    /** Alice plays on, passing priority for both (assuming Bob's passes), answering her own decisions with [choose], until the
     * Stack is empty and nothing waits to trigger. */
    private fun AsyncServer.resolve(choose: (Decision) -> Answer? = { null }) {
        repeat(40) {
            val s = state().engine
            val d = s.pending?.decision
            if (d != null) {
                val answer = choose(d) ?: Engine.options(s, alice).filterIsInstance<Input.Answer>().first().answer
                act(alice, Input.Answer(d.id, answer)).getOrThrow(); return@repeat
            }
            if (s.stack.isEmpty() && s.triggered.isEmpty()) return
            val holder = s.priority.holder!!
            act(alice, Input.PassPriority(holder), assumed = holder != alice).getOrThrow()
        }
        error("not resolved")
    }

    private fun AsyncServer.toMain() {
        start(alice)
        // CR 103.2b: dungeons aren't companions: Alice isn't asked to reveal one, the first decision is her mulligan
        assertIs<Decision.ChooseOption>(pending())
        keepAndAssumeKeep(alice)
        repeat(20) {
            val s = state().engine
            if (s.turn.step == TurnStep.PrecombatMain && s.priority.holder == alice) return
            act(alice, Input.PassPriority(s.priority.holder!!), assumed = s.priority.holder != alice).getOrThrow()
        }
    }

    private fun AsyncServer.cast(name: String) = act(alice, Input.Cast(alice, hand(name)), follow = flow(alice)).getOrThrow()

    @Test
    fun theDungeonsComeAsCardsOfTheirOwn() {
        assertEquals(listOf("Dungeon Test Crypt", "Dungeon Test Sunken City"), brought.map { it.definition.name })
        assertTrue(brought.all { it.definition.faces.size == 1 && it.definition.layout == "normal" })
        assertEquals(setOf(OutsideCards.Need.Dungeon), OutsideCards.needs(Cards.definition(map)))
        assertEquals(setOf(OutsideCards.Need.Undercity), OutsideCards.needs(Cards.definition(claim)))
        assertTrue(OutsideCards.provides(setOf(OutsideCards.Need.Dungeon, OutsideCards.Need.Undercity), outside))
        assertFalse(OutsideCards.provides(setOf(OutsideCards.Need.Dungeon), outside.drop(1)), "the Undercity can't be entered by a plain venture")
        // I-D52: a dungeon with a room the parser can't read would leave it to the players: not brought, not taken by the server
        val odd = CardDefinition("Dungeon Test Odd Vault", listOf(face(5, "Dungeon Test Odd Vault", "Dungeon", """
            Odd Door — The moon hums a slow tune. (Leads to: Odd End)
            Odd End — You gain 1 life.
        """.trimIndent())))
        assertNull(OutsideCards.dungeon(odd))
        assertEquals(setOf(OutsideCards.Need.Dungeon, OutsideCards.Need.Undercity), OutsideCards.provided(outside))
        assertEquals(emptySet(), OutsideCards.provided(listOfNotNull(GameCards.parse(odd).definition)))
        // A card that isn't a dungeon isn't brought
        val plain = CardDefinition("Dungeon Test Plain", listOf(face(4, "Dungeon Test Plain", "Sorcery", "Draw a card.")))
        assertNull(OutsideCards.dungeon(plain))
    }

    // CR 701.49a–c, 309.5–309.7: a venture enters the dungeon a plain venture may enter; the next ventures move along the arrow its
    // owner chooses; the bottommost room's ability resolves and the dungeon is completed. An undo across the room moves gives back
    // the state before them exactly, and playing on again moves the marker anew.
    @Test
    fun aVentureCardGoesThroughADungeon() {
        val server = server(map)
        server.toMain()
        server.cast(map)
        server.resolve()
        assertEquals("Dungeon Test Crypt", server.dungeon()?.named(), "only the crypt may be entered by a plain venture")
        assertEquals("Crypt Door", server.room())
        assertEquals(21, server.life(server.alice))
        val atCryptDoor = server.head
        val before = server.state()
        server.cast(map)
        var offered: List<String>? = null
        server.resolve { d -> (d as? Decision.ChooseOption)?.let { offered = it.options; Answer.Option("Dark Well") } }
        assertEquals(listOf("Bone Hall", "Dark Well"), offered)
        assertEquals("Dark Well", server.room())
        assertEquals(23, server.life(server.alice))
        // Undo back across the room move: the board is the one before it
        server.rewind(server.alice, atCryptDoor)
        val back = server.state()
        assertEquals("Crypt Door", server.room())
        assertEquals(before.engine.player(server.alice).life, back.engine.player(server.alice).life)
        assertEquals(before.engine.command, back.engine.command)
        assertEquals(before.engine.objects.filterKeys { it in before.engine.command }, back.engine.objects.filterKeys { it in back.engine.command })
        // Played on again, this time the other way, then to the bottommost room: the dungeon is completed
        server.cast(map)
        server.resolve { d -> (d as? Decision.ChooseOption)?.let { Answer.Option("Bone Hall") } }
        assertEquals("Bone Hall", server.room())
        assertEquals(19, server.life(server.bob))
        server.cast(map)
        server.resolve()
        assertNull(server.dungeon(), "completed: removed from the game")
        assertEquals(24, server.life(server.alice))
        assertEquals(1, server.state().engine.player(server.alice).ledger["dungeons-completed"])
        assertTrue(server.state().engine.player(server.alice).outside.any { server.state().engine.obj(it)?.named() == "Dungeon Test Crypt" })
    }

    // CR 726.2, 701.49d: taking the initiative ventures into Undercity: the dungeon only "venture into Undercity" may enter
    @Test
    fun theInitiativeEntersTheUndercity() {
        val server = server(claim)
        server.toMain()
        server.cast(claim)
        server.resolve()
        assertNotNull(server.state().engine.player(server.alice).designations.firstOrNull { it == arcanum.api.Designation.Initiative })
        assertEquals("Dungeon Test Sunken City", server.dungeon()?.named())
        assertEquals("Flooded Stair", server.room())
        assertEquals(24, server.life(server.alice))
        assertEquals(Zone.Outside, server.state().engine.player(server.alice).outside.map { server.state().engine.obj(it)!! }
            .single { it.named() == "Dungeon Test Crypt" }.zone)
    }
}
