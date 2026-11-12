package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.PlayKind
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
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
 * [ObjectStatus.Playable] for the activated abilities and special actions a player may use from a card's zone outside a hand
 * (CR 113.6, 602.2, 116.2), usable now or not, by the same checks its activation makes first (where it functions, who may
 * activate it, its condition and uses): what [Engine.options] lists of such a card always has it.
 */
class PlayableAbilitiesStatusTest {
    private val ghoul = Cards.invent("Plaza Ghoul", "{1}{B}", "Creature — Zombie", "Unearth {B}", "2/2")
    private val mummy = Cards.invent("Plaza Mummy", "{1}{W}", "Creature — Human Cleric", "Embalm {W}", "2/1")
    private val skeleton = Cards.invent("Plaza Skeleton", "{1}{B}", "Creature — Skeleton",
        "{2}{B}: Return this card from your graveyard to your hand.", "1/1")
    private val echo = Cards.invent("Plaza Echo", "{R}", "Sorcery",
        "You gain 2 life.\nFlashback {2}{R}\n{1}{B}: Return this card from your graveyard to your hand.")

    // CR 702.84a, 113.6, 602.2: unearth is its owner's to activate from their graveyard, also without mana or priority; on
    // the battlefield or in a hand the card has no such status
    @Test
    fun anUnearthCardIsItsOwnersToUseFromTheGraveyard() = scenario {
        alice.graveyard(ghoul)
        alice.hand(mummy)
        val unearth = activated(card(ghoul))
        assertEquals(listOf(PlayableBy(alice.id, null, listOf(unearth))), playable(card(ghoul)))
        assertNull(playable(card(mummy)), "in her hand")
        alice.passes()
        assertEquals(listOf(PlayableBy(alice.id, null, listOf(unearth))), playable(card(ghoul)), "Bob's priority")
        assertTrue(Engine.options(state, alice.id).none { it is Input.Activate && it.source == card(ghoul) })
    }

    // CR 702.84a, 602.2: with the mana in her main phase, the unearth options lists is one the status names; once unearthed
    // the card is on the battlefield and has none
    @Test
    fun whatOptionsActivatesFromTheGraveyardHasTheStatus() = scenario {
        alice.battlefield("Swamp")
        alice.graveyard(ghoul)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Activate && it.source == card(ghoul) })
        agreesWithOptions(alice)
        alice.activates(ghoul)
        allPass()
        assertNull(playable(card(ghoul, zone = Zone.Battlefield)), "on the battlefield")
    }

    // CR 702.128a, 602.5d: embalm is used from the graveyard as a sorcery, but the status names it in Bob's turn too; used,
    // the card is exiled and has none
    @Test
    fun anEmbalmCardWhetherOrNotItCanBeUsedNow() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(mummy)
        val embalm = activated(card(mummy))
        assertEquals(listOf(PlayableBy(alice.id, null, listOf(embalm))), playable(card(mummy)))
        agreesWithOptions(alice)
        alice.activates(mummy)
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertNull(playable(card(mummy, zone = Zone.Exile)), "exiled as its cost")
    }

    // CR 113.6, 602.2: "{2}{B}: Return this card from your graveyard to your hand" works only in the graveyard: there it is
    // named, also with no mana at all
    @Test
    fun aReturnToHandAbilityOfACardInTheGraveyard() = scenario {
        alice.graveyard(skeleton)
        assertEquals(listOf(PlayableBy(alice.id, null, listOf(activated(card(skeleton))))), playable(card(skeleton)))
        alice.battlefield("Swamp", "Swamp", "Swamp")
        agreesWithOptions(alice)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Activate && it.source == card(skeleton) })
    }

    // CR 602.2, 113.6: "this card from your graveyard": each card is its own owner's to use, never another player's, of three
    @Test
    fun eachCardIsItsOwnersAloneOfThree() = scenario(players = 3) {
        alice.graveyard(ghoul)
        bob.graveyard(skeleton)
        carol.graveyard(mummy)
        players.forEach { it.battlefield("Swamp", "Swamp", "Plains") }
        assertEquals(listOf(PlayableBy(alice.id, null, listOf(activated(card(ghoul))))), playable(card(ghoul)))
        assertEquals(listOf(PlayableBy(bob.id, null, listOf(activated(card(skeleton))))), playable(card(skeleton)))
        assertEquals(listOf(PlayableBy(carol.id, null, listOf(activated(card(mummy))))), playable(card(mummy)))
        players.forEach { agreesWithOptions(it) }
    }

    // CR 702.34a, 601.3, 602.2: a card with flashback and an ability that works in the graveyard names both for its owner
    @Test
    fun aCardWithFlashbackAndAnAbility() = scenario {
        alice.graveyard(echo)
        val ability = activated(card(echo))
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast, listOf(ability))), playable(card(echo)))
        alice.battlefield("Swamp", "Mountain")
        // CR 702.34a: {2}{R} can't be paid with two lands, {1}{B} can
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(echo) })
        assertTrue(Engine.options(state, alice.id).any { it is Input.Activate && it.source == card(echo) })
        assertEquals(listOf(PlayableBy(alice.id, PlayKind.Cast, listOf(ability))), playable(card(echo)), "castable or not")
        agreesWithOptions(alice)
    }

    // CR 702.139a, 116.2g: a revealed companion's special action, from outside the game, is its owner's
    @Test
    fun aRevealedCompanionsSpecialAction() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest")
        val id = put(alice, listOf("Velmora of the Sleep-Den"), Zone.Outside).single()
        state._objects[id] = state.objOrThrow(id).copy(statuses = listOf(ObjectStatus.RevealedCompanion))
        val action = view()[id]!!.characteristics!!.abilities.first { it.ability is Ability.SpecialAction }
        assertEquals(listOf(PlayableBy(alice.id, null, listOf(action.origin))), playable(id))
        agreesWithOptions(alice)
    }

    private fun Scenario.playable(id: ObjectId): List<PlayableBy>? =
        Engine.view(state)[id]?.statuses.orEmpty().filterIsInstance<ObjectStatus.Playable>().singleOrNull()?.by

    private fun Scenario.card(name: String): ObjectId = card(name, null)

    /** The origin of [id]'s only activated ability. */
    private fun Scenario.activated(id: ObjectId): AbilityOrigin =
        Engine.view(state)[id]!!.characteristics!!.abilities.single { it.ability is Ability.Activated }.origin

    /** Every activation and cast [Engine.options] lists for [player] of a card outside a hand, the Stack and the battlefield is
     * named for them by the status: its ability among theirs, a cast with a permission they have. */
    private fun Scenario.agreesWithOptions(player: ScenarioPlayer) {
        fun offZone(id: ObjectId) = state.obj(id)?.zone.let { it != Zone.Hand && it != Zone.Battlefield && it != Zone.Stack }
        Engine.options(state, player.id).forEach { input ->
            when (input) {
                is Input.Activate -> if (offZone(input.source)) assertTrue(playable(input.source).orEmpty()
                    .any { it.player == player.id && input.ability in it.abilities }, "${input.source} ${input.ability} listed for ${player.id}")
                is Input.Cast -> if (offZone(input.card)) assertTrue(playable(input.card).orEmpty()
                    .any { it.player == player.id && it.how != null }, "${input.card} cast listed for ${player.id}")
                else -> Unit
            }
        }
    }
}
