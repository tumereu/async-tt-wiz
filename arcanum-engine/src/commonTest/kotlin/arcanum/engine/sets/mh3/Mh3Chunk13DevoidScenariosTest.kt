package arcanum.engine.sets.mh3

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The MH3 devoid cards of chunk 13, played: the invented names carry the oracle text of Ghostfire Slice and Ugin's Binding
 * (costs cut where they don't change play).
 */
class Mh3Chunk13DevoidScenariosTest {
    private val lash = Cards.invent("Hollowflame Lash", "{2}{R}", "Instant", """
        Devoid (This card has no color.)
        This spell costs {2} less to cast if an opponent controls a multicolored permanent.
        Hollowflame Lash deals 4 damage to any target.
    """)
    private val fetter = Cards.invent("Elder's Fetter", "{2}{U}", "Instant", """
        Devoid (This card has no color.)
        Return target nonland permanent you don't control to its owner's hand.
        Whenever you cast a colorless spell with mana value 7 or greater, you may exile this card from your graveyard. When you do, return each nonland permanent you don't control to its owner's hand.
    """)
    private val warden = Cards.invent("Marbled Warden", "{R}{G}", "Creature — Elemental", "", "2/2")
    private val drifter = Cards.invent("Quiet Drifter", "{G}", "Creature — Elemental", "", "1/1")
    private val relic = Cards.invent("Titanic Relic", "{7}", "Artifact", "")

    // CR 601.2f, 105.2: a permanent of two or more colors held by any one opponent makes the spell cheaper (three players)
    @Test
    fun hollowflameLashIsCheaperWhenAnyOpponentControlsAMulticoloredPermanent() = scenario(players = 3) {
        alice.battlefield("Mountain")
        carol.battlefield(warden)
        alice.hand(lash)
        alice.casts(lash, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 601.2f: a monocolored permanent doesn't count, so one Mountain can't pay {2}{R}
    @Test
    fun hollowflameLashCostsFullWithoutAMulticoloredPermanent() = scenario(players = 3) {
        alice.battlefield("Mountain")
        carol.battlefield(drifter)
        alice.hand(lash)
        alice.casts(lash, target = "bob")
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 603.12, 702.114a: a colorless spell with mana value 7 triggers it from the graveyard; exiling it returns every opposing nonland permanent
    @Test
    fun eldersFetterFromTheGraveyardReturnsEveryOpposingPermanent() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.graveyard(fetter)
        alice.hand(relic)
        bob.battlefield(warden)
        carol.battlefield(drifter, "Forest")
        alice.casts(relic)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.hand has warden
            carol.hand has drifter
            carol.battlefield has "Forest"
        }
    }
}
