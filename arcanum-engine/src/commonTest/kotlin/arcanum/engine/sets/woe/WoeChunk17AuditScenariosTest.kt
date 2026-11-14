package arcanum.engine.sets.woe

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** WOE chunk 17: the invented name carries the oracle text of Stroke of Midnight. */
class WoeChunk17AuditScenariosTest {
    private val stroke = Cards.invent("Stroke of Twilight", "{2}{W}", "Instant", "Destroy target nonland permanent. Its controller creates a 1/1 white Human creature token.")
    private val lure = Cards.invent("Borrowed Loyalty", "{2}{U}", "Sorcery", "Gain control of target creature.")
    private val ogre = Cards.invent("Hired Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")

    // CR 608.2h, 111.2, 108.4: the token goes to the controller the destroyed creature last had (the thief), not its owner
    @Test
    fun tokenGoesToTheLastControllerNotTheOwner() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Plains", "Plains", "Plains")
        alice.hand(lure, stroke)
        carol.battlefield(ogre)
        alice.casts(lure, target = ogre)
        resolveAll()
        alice.casts(stroke, target = ogre)
        resolveAll()
        expect {
            carol.graveyard has ogre
        }
        assertEquals(1, alice.battlefield.names.count { it == "Human Token" })
        assertEquals(0, carol.battlefield.names.count { it == "Human Token" })
        assertEquals(0, bob.battlefield.names.count { it == "Human Token" })
    }
}
