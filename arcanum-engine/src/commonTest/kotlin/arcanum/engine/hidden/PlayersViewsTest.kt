package arcanum.engine.hidden

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.input.Input
import arcanum.engine.review.hostFillsAsked
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A host keeping one state per player (D60, spec §7.0 "Hidden information"): the other player's plays of cards the
 * viewer doesn't know are filled in publicly as they are played, and the steps of a resolution only the other player
 * may see are done in their view ([Decision.OtherView], D31), the viewer's state never holding what they saw.
 */
class PlayersViewsTest {
    /** What alice decides in her view while bob's view waits on an [Decision.OtherView]. */
    private fun Scenario.inHerView(): Decision {
        val d = decision<Decision.OtherView>()
        return Engine.otherViewDecision(state, otherView(d, emptyList()).filled) ?: kotlin.test.fail("Nothing to decide in her view")
    }

    private fun Scenario.name(id: ObjectId): String? = state.objOrThrow(id).definition?.faces?.first()?.name

    private fun GameState.json(): String = kotlinx.serialization.json.Json.encodeToString(GameState.serializer(), this)

    // ---- Playing a card the view doesn't know (#4)

    /** In alice's view bob begins to cast Storm Spark from his hidden hand and takes it back: the card was filled in as it
     * moved to the Stack, and is unknown again in his hand (the fill is part of the cast, CR 601.2). */
    // CR 601.2, 601.2a, D60, D58
    @Test
    fun aCancelledCastFromAHiddenHandIsUnknownAgain() = scenario {
        viewer(alice)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        perform(Input.Cast(bob.id, card("Storm Spark")))
        assertTrue(pending is Decision.Targets, "the card was filled in and the cast goes on: $pending")
        perform(Input.Cancel(bob.id))
        assertNull(pending)
        assertTrue(state.player(bob.id).hand.all { state.objOrThrow(it).unknown })
        expect { stack.names shouldBe emptyList() }
    }

    /** In alice's view bob "casts" a card that is in truth a land: the fill is asked, and its answer is refused (lands
     * are played, CR 305.9); the play can then be taken back by bob, leaving nothing behind. */
    // CR 305.9, D60
    @Test
    fun aPlayRefusedOnceItsCardIsKnownCanBeTakenBack() = scenario {
        viewer(alice)
        bob.hand("Forest")
        alice.passes()
        val before = state.deepCopy()
        Engine.perform(state, Input.Cast(bob.id, card("Forest"))).changes.forEach { change(it) }
        val fill = decision<Decision.HostFill>()
        assertTrue(fill.public)
        val answer = Input.Answer(fill.id, Answer.Filled(fill.objects.map { FilledObject(it, hostKnows(it)) }))
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, answer) }
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Cancel(alice.id)) }
        Engine.perform(state, Input.Cancel(bob.id)).changes.forEach { change(it) }
        assertNull(pending)
        assertEquals(before.objects, state.objects)
    }

    /** In bob's view alice plays a land and casts a creature from her hidden hand: each is filled in publicly once, and
     * the creature resolves; the rest of her hand stays unknown. */
    // CR 305.1, 601.2a, D60
    @Test
    fun theOtherPlayersLandAndCreatureAreFilledAsTheyArePlayed() = scenario {
        viewer(bob)
        alice.battlefield("Forest")
        alice.hand("Forest", "Elvish Seer", "Storm Spark")
        alice.plays("Forest")
        alice.casts("Elvish Seer")
        allPass()
        expect {
            alice.battlefield has "Elvish Seer"
            alice.hand.size shouldBe 1
        }
        assertEquals(2, hostFillsAsked().count { it.public && it.reason in setOf("played", "cast") })
        assertTrue(state.player(alice.id).hand.all { state.objOrThrow(it).unknown })
    }

    // ---- Steps only the other player sees (#5)

    /** In bob's view alice's Oracle's Spinning Top looks at her top three and she puts them back in another order: bob's
     * view waits on [Decision.OtherView] meanwhile, offers nobody an answer, and holds no identity of her cards, not even
     * in its pending decision; her order is answered from her view and the library is reordered, still unknown. */
    // CR 401.4, D60, D31
    @Test
    fun aLookAndReorderIsDoneInHerView() = scenario {
        viewer(bob)
        alice.battlefield("Oracle's Spinning Top", "Forest")
        alice.library("Storm Spark", "Denial", "Island", "Plains")
        val top = state.player(alice.id).library.take(3)
        alice.activates("Oracle's Spinning Top", ability = 0)
        allPass()
        val waiting = decision<Decision.OtherView>()
        assertEquals(alice.id, waiting.player)
        assertEquals(top.toSet(), waiting.objects.toSet())
        assertTrue(Engine.options(state, alice.id).isEmpty() && Engine.options(state, bob.id).isEmpty())
        assertFalse(listOf("Storm Spark", "Denial", "Island").any { it in state.json() }, "bob's state names none of her cards")

        val order = inHerView() as Decision.ChooseObjects
        assertEquals(alice.id, order.player)
        alice.answers(Answer.Objects(top.reversed()))
        assertNull(pending)
        assertEquals(top.reversed(), state.player(alice.id).library.take(3))
        assertTrue(state.player(alice.id).library.all { state.objOrThrow(it).unknown })
        assertTrue(applied.filterIsInstance<Change.Filled>().none { it.after.owner == alice.id && it.after.zone == Zone.Library })
        assertTrue(hostFillsAsked().none { !it.public })
    }

    /** Gathered Fellowship in bob's view: alice looks at her top six and puts two small creatures onto the battlefield,
     * the rest on the bottom. The creatures are filled in as they enter (public); the four others stay unknown in bob's
     * view, in his state and in every change he got; the battlefield is the one a full-information game has. */
    // CR 608.2c, 400.2, D60, D31
    @Test
    fun aLookThatPutsCardsOntoTheBattlefieldShowsOnlyThose() {
        fun play(viewer: Boolean): List<String> {
            var battlefield = emptyList<String>()
            scenario {
                alice.battlefield("Forest", "Forest", "Forest", "Forest")
                alice.hand("Gathered Fellowship")
                alice.library("Storm Spark", "Elvish Seer", "Denial", "Tinkered Mite", "Island", "Plains", "Swamp")
                if (viewer) viewer(bob)
                alice.casts("Gathered Fellowship")
                allPass()
                if (viewer) {
                    val choose = inHerView() as Decision.ChooseObjects
                    assertEquals(2, choose.options.size, "the creatures with mana value 3 or less among the six")
                }
                alice.chooses("Elvish Seer", "Tinkered Mite")
                // The rest on the bottom in any order: as listed
                if (pending != null) {
                    val rest = (if (viewer) inHerView() else pending) as Decision.ChooseObjects
                    alice.answers(Answer.Objects(rest.options))
                }
                assertNull(pending)
                battlefield = state.battlefield.mapNotNull { name(it) }.sorted()
                if (viewer) {
                    val seen = applied.filterIsInstance<Change.Filled>().mapNotNull { it.after.definition?.faces?.first()?.name }
                    assertEquals(setOf("Gathered Fellowship", "Elvish Seer", "Tinkered Mite"), seen.toSet())
                    val hidden = listOf("Storm Spark", "Denial", "Island", "Plains")
                    val json = kotlinx.serialization.json.Json
                    assertTrue(applied.none { c -> hidden.any { it in json.encodeToString(Change.serializer(), c) } }, "no change names her other cards")
                    assertTrue(state.player(alice.id).library.all { state.objOrThrow(it).unknown })
                    assertEquals(5, state.player(alice.id).library.size)
                }
            }
            return battlefield
        }
        assertEquals(play(viewer = false), play(viewer = true))
    }

    /** Clever Tinkersmith in bob's view: alice looks at her top four and reveals an artifact card into her hand; the rest
     * go to the bottom in a random order (new ids from the host, D60). bob's view learns only the revealed card (filled
     * as it is revealed), which is unknown again in her hand. */
    // CR 701.20a, 401.4, D60, D31
    @Test
    fun aRevealedCardIsSeenAndTheRestStayHidden() = scenario {
        viewer(bob)
        alice.battlefield("Plains", "Plains")
        alice.hand("Clever Tinkersmith")
        alice.library("Storm Spark", "Tinkered Mite", "Denial", "Island", "Forest")
        alice.casts("Clever Tinkersmith")
        allPass()
        if (pending == null) allPass()
        var guard = 5
        while (pending is Decision.OtherView && guard-- > 0) {
            when (val d = inHerView()) {
                is Decision.May -> alice.answers(Answer.YesNo(true))
                is Decision.ChooseObjects -> {
                    assertEquals(listOf("Tinkered Mite"), d.options.map { hostKnows(it).faces.first().name })
                    alice.chooses("Tinkered Mite")
                }
                else -> kotlin.test.fail("Unexpected in her view: $d")
            }
        }
        assertNull(pending, "$pending")
        val seen = applied.filterIsInstance<Change.Filled>().mapNotNull { it.after.definition?.faces?.first()?.name }
        assertEquals(setOf("Clever Tinkersmith", "Tinkered Mite"), seen.toSet())
        expect { alice.hand.size shouldBe 1 }
        assertTrue(state.player(alice.id).hand.all { state.objOrThrow(it).unknown }, "known while revealed, forgotten in her hand")
        assertTrue(state.player(alice.id).library.all { state.objOrThrow(it).unknown })
    }

    /** An answer to [Decision.OtherView] whose identities aren't exactly the ones asked is refused, as is a fill among
     * the answers; the state is unchanged (the one validation path). */
    // D60
    @Test
    fun wrongIdentitiesForAnotherViewAreRefused() = scenario {
        viewer(bob)
        alice.battlefield("Oracle's Spinning Top", "Forest")
        alice.library("Storm Spark", "Denial", "Island")
        alice.activates("Oracle's Spinning Top", ability = 0)
        allPass()
        val d = decision<Decision.OtherView>()
        val right = otherView(d, emptyList())
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Answer(d.id, right.copy(filled = right.filled.drop(1)))) }
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.Answer(d.id, right.copy(answers = listOf(Answer.Filled(right.filled)))))
        }
        // An answer of the wrong kind for what she decides there
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Answer(d.id, right.copy(answers = listOf(Answer.YesNo(true))))) }
    }

    /** In alice's own view her look is hers: the fill is asked for her and she orders as usual, no [Decision.OtherView]. */
    // D60
    @Test
    fun inHerOwnViewHerLookIsAskedAsUsual() = scenario {
        viewer(alice)
        alice.battlefield("Oracle's Spinning Top", "Forest")
        alice.library("Storm Spark", "Denial", "Island")
        alice.activates("Oracle's Spinning Top", ability = 0)
        allPass()
        assertTrue(pending is Decision.ChooseObjects)
        assertTrue(hostFillsAsked().any { !it.public && it.player == alice.id })
    }
}
