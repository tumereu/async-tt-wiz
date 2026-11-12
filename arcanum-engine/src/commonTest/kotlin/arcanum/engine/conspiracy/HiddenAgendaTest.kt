package arcanum.engine.conspiracy

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.inOrder
import arcanum.engine.setup.keep
import arcanum.engine.setup.startGame
import arcanum.engine.setup.table
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hidden agenda and double agenda (CR 702.106): a conspiracy put into the command zone face down with a secretly chosen name. */
class HiddenAgendaTest {
    private val tithe = Cards.invent("Agenda Secret Tithe", null, "Conspiracy", """
        Hidden agenda (Start the game with this conspiracy face down in the command zone and secretly choose a card name. You may turn this conspiracy face up any time and reveal that name.)
        Spells with the chosen name you cast cost {1} less to cast.
    """.trimIndent())
    private val twin = Cards.invent("Agenda Twin Tithe", null, "Conspiracy", """
        Double agenda (Start the game with this conspiracy face down in the command zone and secretly choose two different card names. You may turn this conspiracy face up any time and reveal those names.)
        Spells with the chosen name you cast cost {1} less to cast.
    """.trimIndent())
    private val bear = Cards.invent("Agenda Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Agenda Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")

    /** The special action turning [id] face up (the hidden agenda keyword's expansion: its first printed ability). */
    private val reveal = AbilityOrigin.Expansion(AbilityOrigin.Printed(0, 0), 0)

    private fun Scenario.conspiracy(name: String, vararg names: String): ObjectId {
        val id = put(alice, listOf(name), Zone.Command, faceDown = true).single()
        var memory = Bindings.EMPTY
        names.getOrNull(0)?.let { memory = memory.with(Conspiracies.CHOSEN_NAME, NamedValue.Name(it)) }
        names.getOrNull(1)?.let { memory = memory.with(Conspiracies.SECOND_NAME, NamedValue.Name(it)) }
        state._objects[id] = state.objOrThrow(id).copy(memory = memory)
        return id
    }

    // CR 103.2e, 905.4, 905.4a, 702.106a: a Conspiracy Draft game starts with the conspiracy face down in the command zone and its name
    // secretly chosen
    @Test
    fun putIntoTheCommandZoneAsTheGameStarts() = scenario(start = table(List(40) { "Forest" }, List(40) { "Island" },
        sideboards = mapOf(0 to listOf(tithe)))) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id, conspiracies = true))
        val choose = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, choose.player)
        answer(Answer.Objects(choose.options))
        assertTrue(pending is Decision.ChooseCardName, "alice names a card: $pending")
        answer(Answer.CardName(bear))
        keep(alice)
        keep(bob)
        val o = state.command.map { state.objOrThrow(it) }.single()
        assertTrue(o.faceDown && o.owner == alice.id)
        assertEquals(NamedValue.Name(bear), o.memory[Conspiracies.CHOSEN_NAME])
    }

    // CR 702.106c, 116.2j: any time she has priority alice turns it face up; CR 607.2d: then spells with the chosen name cost less
    @Test
    fun turnedFaceUpTheChosenNameCounts() = scenario {
        alice.battlefield("Forest")
        alice.hand(bear)
        val id = conspiracy(tithe, bear)
        // Face down it has no abilities: the bear costs {1}{G}
        perform(Input.Cast(alice.id, card(bear), arcanum.engine.input.CastWay()))
        assertEquals("{1}{G}", decision<Decision.ManaPayment>().cost.toString())
        perform(Input.Cancel(alice.id))
        perform(Input.Activate(alice.id, id, reveal))
        assertTrue(!state.objOrThrow(id).faceDown)
        alice.casts(bear)
        allPass()
        expect { alice.battlefield has bear }
    }

    // CR 702.106f: double agenda's two names both count
    @Test
    fun doubleAgendaNamesTwoCards() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(bear, ox)
        val id = conspiracy(twin, bear, ox)
        perform(Input.Activate(alice.id, id, reveal))
        alice.casts(ox)
        allPass()
        expect { alice.battlefield has ox }
    }
}
