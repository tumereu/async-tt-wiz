package arcanum.engine.view

import arcanum.api.PlayKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectStatus
import arcanum.engine.state.PlayableBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ObjectStatus.Playable] in the view: the players a permission lets cast or play a card from outside a hand, castable now or
 * not, by the same permissions its cast and land play check (holder, condition, uses each turn, the top of a library).
 */
class PlayableStatusTest {
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val pup = Cards.invent("Thornlane Pup", "{G}", "Creature — Dog", "", "1/1")
    private val hostility = Cards.invent("Stay the Hostility", "{1}{R}{W}", "Instant",
        "Exile target nonland permanent and the top card of your library. For each of those cards, its owner may play it until the end of their next turn.")

    // CR 601.3, 702.34a: a flashback card in a graveyard is its owner's to cast from there, also without mana or priority; a
    // card without a permission there has none, nor has a card in a hand
    @Test
    fun aFlashbackCardIsItsOwnersToCastWhetherOrNotTheyCanNow() = scenario {
        alice.graveyard("Cinder Bolt", "Storm Spark")
        alice.hand("Faithless Rummage")
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast)), playable(card("Cinder Bolt")))
        assertNull(playable(card("Storm Spark")))
        assertNull(playable(card("Faithless Rummage")))
        alice.passes()
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast)), playable(card("Cinder Bolt")), "Bob's priority")
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast })
    }

    // CR 702.34a, 601.3: with the mana, every cast options lists from outside the hand is of a card the status names
    @Test
    fun whatOptionsCastsFromElsewhereHasTheStatus() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")
        assertTrue(Engine.options(state, alice.id).any { it is Input.Cast && it.card == card("Cinder Bolt") })
        agreesWithOptions(alice)
    }

    // CR 611.2c, 601.3: "its owner may play it": each exiled card is its own owner's, the land of the library's top too
    @Test
    fun anOwnersPermissionIsTheirsAlone() = scenario(players = 3) {
        alice.battlefield("Mountain", "Plains", "Plains")
        alice.hand(hostility)
        alice.library("Forest", bear)
        bob.battlefield(ogre)
        alice.casts(hostility, targets = listOf("Plaza Ogre"))
        allPass()
        assertEquals(listOf(PlayableBy(bob.id, PlayKind.Play)), playable(card(ogre)), "only Bob, of Alice's two opponents")
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Play)), playable(card("Forest")))
        assertNull(playable(card(bear)), "still in the library")
        players.forEach { agreesWithOptions(it) }
    }

    // CR 305.1, 601.3, 401.5: "you may play lands from the top of your library": the top card only, a land; the coven
    // permission's condition unmet gives a creature on top none, met it does
    @Test
    fun theTopOfTheLibraryAndAConditionalPermission() = scenario {
        alice.battlefield("Seer of Harvestide")
        alice.library("Forest", "Mountain")
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Play)), playable(card("Forest")))
        assertNull(playable(card("Mountain")), "not the top card")
        alice.plays("Forest")
        // CR 305.2: no land plays left, but the permission still applies to the new top card
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Play)), playable(card("Mountain")))
        agreesWithOptions(alice)
    }

    // CR 601.3, 207.2c (coven, an ability word): the creature permission applies only while its condition holds
    @Test
    fun aPermissionWhoseConditionFailsGivesNone() = scenario {
        alice.battlefield("Seer of Harvestide", ogre)
        alice.library(bear)
        assertNull(playable(card(bear)), "two different powers")
        alice.battlefield(pup)
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast)), playable(card(bear)), "three different powers")
    }

    // CR 601.3: "once during each of your turns, you may cast … from your graveyard": once used this turn, none
    @Test
    fun aOncePerTurnPermissionUsedGivesNone() = scenario {
        alice.battlefield("Velmora of the Sleep-Den", "Forest", "Forest")
        alice.graveyard("Elvish Seer", "Thicketwood Elves")
        players.forEach { it.library("Forest", "Forest", "Forest") }
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast)), playable(card("Thicketwood Elves")))
        alice.casts("Elvish Seer")
        allPass()
        assertNull(playable(card("Thicketwood Elves")))
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast)), playable(card("Thicketwood Elves")), "her next turn")
    }

    private fun Scenario.playable(id: ObjectId): List<PlayableBy>? =
        Engine.view(state)[id]?.statuses.orEmpty().filterIsInstance<ObjectStatus.Playable>().singleOrNull()?.by

    private fun Scenario.card(name: String): ObjectId = card(name, null)

    /** Every cast or land play [Engine.options] lists for [player] of a card outside their hand has the status naming them. */
    private fun Scenario.agreesWithOptions(player: ScenarioPlayer) {
        val offered = Engine.options(state, player.id).mapNotNull { input ->
            when (input) {
                is Input.Cast -> input.card
                is Input.PlayLand -> input.card
                else -> null
            }
        }.filter { state.objOrThrow(it).zone != Zone.Hand }
        offered.forEach { id -> assertTrue(playable(id).orEmpty().any { it.player == player.id }, "$id listed by options for ${player.id}") }
    }
}
