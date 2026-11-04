package arcanum.engine.hidden

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * "Skip when I have no choice" (I-D18), a per-player setting in the game state. On (the default): priority is passed for
 * a player who can do nothing ([Engine.shouldPassFor], I-D3) and questions with nothing to choose are skipped. Off: a
 * pass or skip that depends on cards only that player may see (hand, face-down cards) is theirs: a priority window they
 * pass themselves, or the question with only its forced answer. Skips that depend on nothing hidden stay automatic (P5).
 * Each scenario runs with full information and on both players' views (D60).
 */
class SkipWhenNoChoiceTest {
    /** Full information, alice's view, bob's view. */
    private val viewers = listOf<String?>(null, "alice", "bob")

    private fun Scenario.view(name: String?) {
        if (name != null) viewer(player(PlayerId(name)))
    }

    /** Runs [block] for every viewer with [on] as the setting of [who] (alice by default). */
    private fun everyWay(block: Scenario.(viewer: String?, on: Boolean) -> Unit) = viewers.forEach { v ->
        listOf(true, false).forEach { on -> scenario { view(v); block(v, on) } }
    }

    /** The decision asked now: in a viewer's state that waits on the other player's view, the one asked there. */
    private fun Scenario.asked(): Decision? = when (val d = pending) {
        is Decision.OtherView -> Engine.otherViewDecision(state, otherView(d, emptyList()).filled)
        else -> d
    }

    private fun Scenario.unknownHand(player: PlayerId) = state.player(player).hand.all { state.objOrThrow(it).unknown }

    /** "Choose a creature card in your hand. Put it onto the battlefield." ({G} sorcery, written in the model: the parser
     * reads no such wording yet.) */
    private val summons = Cards.define("Kindred Summons", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Kindred Summons", ManaCost.parse("{G}"), types = listOf(CardType.Sorcery),
        abilities = listOf(Ability.Spell(Instructions(steps = listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.You))), min = Value.Fixed(1), max = Value.Fixed(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield))),
        )))),
    ))))

    // ---- Priority (I-D3 through I-D18)

    /** alice casts Storm Spark at bob and passes; bob holds priority (CR 117.3c–d). */
    private fun Scenario.aliceSparksBob() {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        assertEquals(bob.id, state.priority.holder)
    }

    /** bob's hand holds an instant he can't afford (Denial, {U}{U}, with one Island): on, priority is passed for him (his
     * Island's mana ability alone isn't something to do); off, it is his to pass, since a card only he sees decides. In
     * alice's view his hand is unknown, so the engine can't tell either way and never passes for him (D60). */
    // I-D18, CR 117.3d, 106.4
    @Test
    fun anUnaffordableInstantInHandIsPassedForOnlyWhenOn() = viewers.forEach { v ->
        listOf(true, false).forEach { on ->
            scenario {
                view(v)
                bob.skipWhenNoChoice = on
                bob.battlefield("Island")
                bob.hand("Denial")
                aliceSparksBob()
                assertTrue(Engine.options(state, bob.id).any { it is Input.Activate }, "his Island's mana ability is listed")
                assertEquals(on && v != "alice", Engine.shouldPassFor(state, bob.id), "on=$on viewer=$v")
                assertFalse(Engine.shouldPassFor(state, alice.id), "only the holder is passed for")
            }
        }
    }

    /** [Engine.passCheck] answers [Engine.shouldPassFor] as set and with bob's own setting on, from one search: the
     * same as asking it twice with the setting changed in between. */
    // I-D18, CR 117.3d
    @Test
    fun passCheckIsShouldPassForBothWays() = everyWay { v, on ->
        bob.skipWhenNoChoice = on
        bob.battlefield("Island")
        bob.hand("Denial")
        aliceSparksBob()
        val check = Engine.passCheck(state, bob.id)
        assertEquals(Engine.shouldPassFor(state, bob.id), check.asSet, "as set, on=$on viewer=$v")
        val skip = Changes.skipWhenNoChoice(state, bob.id, true)
        state.apply(skip)
        val withSkip = Engine.shouldPassFor(state, bob.id)
        state.revert(skip)
        assertEquals(withSkip, check.ownSkipOn, "own skip on, on=$on viewer=$v")
        assertEquals(arcanum.engine.PassCheck.NONE, Engine.passCheck(state, alice.id), "only the holder")
    }

    /** With an empty hand nothing hidden decides: priority is passed for bob whatever his setting and whoever's view. */
    // I-D18, CR 117.3d
    @Test
    fun anEmptyHandIsPassedForEitherWay() = everyWay { _, on ->
        bob.skipWhenNoChoice = on
        bob.battlefield("Island")
        aliceSparksBob()
        assertTrue(Engine.shouldPassFor(state, bob.id))
    }

    /** A face-down permanent is a card only its controller sees (CR 708.5): off, bob passes himself. */
    // I-D18, CR 708.5
    @Test
    fun aFaceDownPermanentIsHisToPassWithWhenOff() = everyWay { v, on ->
        bob.skipWhenNoChoice = on
        bob.battlefield("Undead Soothsayer", faceDown = true)
        aliceSparksBob()
        assertEquals(on && v != "alice", Engine.shouldPassFor(state, bob.id), "on=$on viewer=$v")
    }

    /** A host passes for each player while [Engine.shouldPassFor] says so: on, the spell resolves at once although bob
     * holds an instant; off, the host stops at bob's window and he passes himself. */
    // I-D18, I-D3, CR 117.4
    @Test
    fun aHostPassingForPlayersStopsAtAHiddenWindowWhenOff() = listOf(true, false).forEach { on ->
        scenario {
            bob.skipWhenNoChoice = on
            bob.hand("Denial")
            alice.battlefield("Mountain")
            alice.hand("Storm Spark")
            alice.casts("Storm Spark", target = "bob")
            while (state.stack.isNotEmpty() && pending == null && state.priority.holder?.let { Engine.shouldPassFor(state, it) } == true) passPriority()
            if (on) {
                expect { bob.life shouldBe 17 }
            } else {
                assertEquals(bob.id, state.priority.holder)
                expect { bob.life shouldBe 20 }
                bob.passes()
                expect { bob.life shouldBe 17 }
            }
        }
    }

    // ---- Questions with nothing to choose

    /** "If this artifact would enter, you may discard a land card instead. If you do, put this artifact onto the
     * battlefield. If you don't, put it into its owner's graveyard." (Gemstone Charm) with no land card in hand: on, it
     * goes to the graveyard unasked; off, alice is asked the cost she can't pay, "no" being the only answer (anyone may
     * give it, P7). In bob's view her hand is filled in only in her view (D31) and stays unknown in his. */
    // I-D18, CR 614.1c, 118.3
    @Test
    fun aCostOnlyHiddenCardsCouldPayIsAskedWhenOff() = everyWay { v, on ->
        alice.skipWhenNoChoice = on
        alice.hand("Gemstone Charm", "Storm Spark")
        alice.casts("Gemstone Charm")
        allPass()
        if (!on) {
            val may = asked() as? Decision.May ?: fail("Expected the cost to be asked: $pending")
            assertEquals(alice.id, may.player)
            assertFalse(may.payable)
            (pending as? Decision.May)?.let { d ->
                val no = Input.Answer(d.id, Answer.YesNo(false))
                assertEquals(listOf(no), Engine.options(state, alice.id))
                assertEquals(listOf(no), Engine.options(state, bob.id))
                assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Answer(d.id, Answer.YesNo(true))) }
            }
            alice.answers(Answer.YesNo(false))
        }
        assertNull(pending, "viewer=$v on=$on")
        expect {
            alice.graveyard has "Gemstone Charm"
            alice.hand.size shouldBe 1
        }
        if (v == "bob") assertTrue(unknownHand(alice.id))
    }

    /** With a land card in hand the cost is asked as usual; in bob's view her hand is looked at in her view (D31), so the
     * charm enters there as it does in hers (her hand used to read as no land card there). Paying, the only land card is
     * discarded by the rules: off, she is asked with it as the one answer, as only she knows it is her only land. */
    // I-D18, CR 614.1c, D60
    @Test
    fun aCostHiddenCardsCanPayIsAskedEitherWay() = everyWay { v, on ->
        alice.skipWhenNoChoice = on
        alice.hand("Gemstone Charm", "Forest", "Storm Spark")
        alice.casts("Gemstone Charm")
        allPass()
        assertTrue((asked() as? Decision.May)?.payable == true, "asked: ${asked()}")
        alice.answers(Answer.YesNo(true))
        if (!on) {
            val choice = asked() as? Decision.ChooseObjects ?: fail("Expected the discard to be asked: $pending")
            assertEquals(1, choice.options.size)
            alice.answers(Answer.Objects(choice.options))
        }
        assertNull(pending, "viewer=$v on=$on")
        expect {
            alice.battlefield has "Gemstone Charm"
            alice.graveyard has "Forest"
        }
        if (v == "bob") assertTrue(unknownHand(alice.id))
    }

    /** With an empty hand the cost can't be paid whatever cards are hidden: no question, setting or not. */
    // I-D18, P5
    @Test
    fun aCostWithAnEmptyHandIsSkippedEitherWay() = everyWay { _, on ->
        alice.skipWhenNoChoice = on
        alice.hand("Gemstone Charm")
        alice.casts("Gemstone Charm")
        allPass()
        assertNull(pending)
        expect { alice.graveyard has "Gemstone Charm" }
    }

    /** "Choose a creature card in your hand" with none: off, the choice is asked with nothing to choose. */
    // I-D18, CR 608.2d
    @Test
    fun aChoiceOfHiddenCardsWithNoneIsAskedWhenOff() = everyWay { v, on ->
        alice.skipWhenNoChoice = on
        alice.battlefield("Forest")
        alice.hand(summons, "Storm Spark")
        alice.casts(summons)
        allPass()
        if (!on) {
            val choice = asked() as? Decision.ChooseObjects ?: fail("Expected the choice to be asked: $pending")
            assertEquals(Triple(emptyList(), 0, 0), Triple(choice.options, choice.min, choice.max))
            (pending as? Decision.ChooseObjects)?.let { d ->
                assertEquals(listOf<Input>(Input.Answer(d.id, Answer.Objects(emptyList()))), Engine.options(state, alice.id))
            }
            alice.answers(Answer.Objects(emptyList()))
        }
        assertNull(pending, "viewer=$v on=$on")
        expect {
            alice.graveyard has summons
            alice.hand.size shouldBe 1
        }
    }

    /** The only creature card in hand is chosen by the rules (CR 609.3): off, alice is asked with it as the one answer. */
    // I-D18, CR 609.3
    @Test
    fun aForcedChoiceOfHiddenCardsIsAskedWhenOff() = everyWay { v, on ->
        alice.skipWhenNoChoice = on
        alice.battlefield("Forest")
        alice.hand(summons, "Elvish Seer", "Storm Spark")
        alice.casts(summons)
        allPass()
        if (!on) {
            val choice = asked() as? Decision.ChooseObjects ?: fail("Expected the choice to be asked: $pending")
            assertEquals(1, choice.options.size)
            assertEquals(1, choice.min)
            alice.answers(Answer.Objects(choice.options))
        }
        assertNull(pending, "viewer=$v on=$on")
        expect { alice.battlefield has "Elvish Seer" }
    }

    /** "When this creature enters, you may put a land card from your hand onto the battlefield tapped." (Canopy Browser)
     * with no land card in hand: the "may" is skipped only because no hidden card qualifies, so off asks it. */
    // I-D18, CR 603.3
    @Test
    fun aMaySkippedForWantOfAHandCardIsAskedWhenOff() = everyWay { v, on ->
        alice.skipWhenNoChoice = on
        alice.battlefield("Forest")
        alice.hand("Canopy Browser", "Storm Spark")
        alice.casts("Canopy Browser")
        allPass()
        expect { stack.names shouldBe listOf("ability of Canopy Browser") }
        allPass()
        if (!on) {
            val choice = asked() as? Decision.ChooseObjects ?: fail("Expected the choice to be asked: $pending")
            assertTrue(choice.options.isEmpty())
            alice.answers(Answer.Objects(emptyList()))
        }
        assertNull(pending, "viewer=$v on=$on")
        expect {
            alice.battlefield has "Canopy Browser"
            alice.hand.size shouldBe 1
        }
    }

    /** "Each player sacrifices a nontoken creature of their choice." with one creature each, and "each player discards a
     * card" with one card in hand: forced choices everyone can see (how many cards, not which) stay automatic off. */
    // I-D18, P5, CR 701.21a, 701.9a
    @Test
    fun forcedChoicesEveryoneCanSeeStayAutomatic() = viewers.forEach { v ->
        scenario {
            view(v)
            alice.skipWhenNoChoice = false
            bob.skipWhenNoChoice = false
            alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
            alice.hand("Cursed Pillager", "Festering Vermin")
            bob.battlefield("Elvish Seer")
            bob.hand("Storm Spark")
            alice.casts("Cursed Pillager")
            allPass()
            allPass()
            assertNull(pending, "viewer=$v")
            expect {
                alice.graveyard has "Cursed Pillager"
                bob.graveyard has "Elvish Seer"
            }
            alice.casts("Festering Vermin")
            allPass()
            allPass()
            assertNull(pending, "viewer=$v")
            expect {
                bob.graveyard has "Storm Spark"
                bob.hand.size shouldBe 0
            }
        }
    }

    // ---- The setting in the state

    /** A host turns the setting off mid-game with a change of its own, which reverts (D59). */
    // I-D18, D59
    @Test
    fun aHostChangesTheSetting() = scenario {
        alice.hand("Gemstone Charm", "Storm Spark")
        change(Changes.skipWhenNoChoice(state, alice.id, false))
        assertFalse(alice.skipWhenNoChoice)
        alice.casts("Gemstone Charm")
        allPass()
        assertTrue(pending is Decision.May)
        alice.answers(Answer.YesNo(false))
    }

    /** The setting, its change and an asked cost go through JSON unchanged; on, the default, isn't written. */
    // I-D18, D58
    @Test
    fun theSettingGoesThroughJson() = scenario {
        bob.skipWhenNoChoice = false
        alice.hand("Gemstone Charm", "Storm Spark")
        alice.skipWhenNoChoice = false
        val json = Json.encodeToString(GameState.serializer(), state)
        assertEquals(state, Json.decodeFromString(GameState.serializer(), json))
        assertEquals(2, Regex("\"skipWhenNoChoice\":false").findAll(json).count())
        assertFalse("skipWhenNoChoice\":true" in json)
        val change: Change = Changes.skipWhenNoChoice(state, bob.id, true)
        assertEquals(change, Json.decodeFromString(Change.serializer(), Json.encodeToString(Change.serializer(), change)))
        alice.casts("Gemstone Charm")
        allPass()
        val restored = Json.decodeFromString(GameState.serializer(), Json.encodeToString(GameState.serializer(), state))
        assertEquals(state, restored)
        assertFalse((restored.pending?.decision as Decision.May).payable)
    }
}
