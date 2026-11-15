package arcanum.engine.sets.trk

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertTrue

/** Star Trek chunk 05 in play: beam me up (CR 702.196a): cast from the graveyard for its cost by also returning a creature you
 * control to its owner's hand, then exiled; not castable this way without a creature of your own; cast from the hand it is an
 * ordinary spell. The cards' other effects repeat tested shapes. */
class TrkChunk05BeamMeUpScenariosTest {
    private val ping = Cards.invent("Open Channel Ping", "{U}", "Sorcery", """
        Draw a card.
        Beam me up {2}{U} (You may cast this card from your graveyard for {2}{U} if you also return a creature you control to its owner's hand. Then exile this spell.)
    """)
    private val seize = Cards.invent("Trk5 Borrowed Crew", "{U}", "Sorcery", "Gain control of target creature until end of turn.")
    private val bear = Cards.invent("Trk5 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk5 Ox", "{2}{G}", "Creature — Ox", "", "3/3")

    private fun Scenario.castable(name: String) =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().any { it.card == card(name) }

    // CR 702.196a: {2}{U} and a creature you control back to hand; the spell resolves, then is exiled instead of going to the graveyard
    @Test
    fun castFromTheGraveyardReturningACreatureThenExiled() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", bear, ox)
        alice.graveyard(ping)
        alice.library("Forest")
        alice.casts(ping, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has "Forest"
            alice.exile has ping
            ox.zone shouldBe Zone.Battlefield
        }
    }

    // CR 702.196a: the creature returned must be one you control; other players' creatures don't pay the cost
    @Test
    fun notCastableWithoutACreatureYouControl() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island")
        bob.battlefield(bear)
        carol.battlefield(ox)
        alice.graveyard(ping)
        assertTrue(!castable(ping))
    }

    // CR 702.196a, 400.3: a creature you control but don't own goes to its owner's hand
    @Test
    fun returnedCreatureGoesToItsOwnersHand() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island")
        carol.battlefield(ox)
        alice.hand(seize)
        alice.graveyard(ping)
        alice.library("Forest")
        alice.casts(seize, target = ox, paying = listOf("Island"))
        resolveAll()
        assertTrue(castable(ping))
        alice.casts(ping, choosing = listOf(ox))
        resolveAll()
        expect {
            carol.hand has ox
            alice.hand has "Forest"
            alice.exile has ping
        }
    }

    // CR 702.196a: cast from the hand for its mana cost it is an ordinary spell and goes to the graveyard
    @Test
    fun castFromTheHandGoesToTheGraveyard() = scenario(players = 3) {
        alice.battlefield("Island", bear)
        alice.hand(ping)
        alice.library("Forest")
        alice.casts(ping)
        resolveAll()
        expect {
            alice.graveyard has ping
            bear.zone shouldBe Zone.Battlefield
        }
    }
}
