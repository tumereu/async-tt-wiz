package arcanum.engine.decision

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A choice "from among" a group of cards lists the group's cards it can't choose ([Decision.ChooseObjects.others]), so a host
 * can show the whole group with the choice: the cards revealed, looked at, exiled or milled just before. The invented cards carry
 * the oracle wording of Squad Rallier's look, a reveal of the same shape, Light Up the Stage's kin's "choose one of them", a mill of
 * the same shape.
 * (ATW shows them under "Other cards", STK-224.)
 */
class ChoiceAmongOthersTest {
    private val reveal = Cards.invent("Ember Unveiling", "{0}", "Sorcery",
        "Reveal the top four cards of your library. You may put a creature card from among them into your hand. Put the rest on the bottom of your library in a random order.")
    private val look = Cards.invent("Ember Glimpse", "{0}", "Sorcery",
        "Look at the top four cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.")
    private val exile = Cards.invent("Ember Excavation", "{0}", "Sorcery",
        "Exile the top three cards of your library. Choose one of them. You may play that card this turn.")
    private val mill = Cards.invent("Ember Sifting", "{0}", "Sorcery",
        "Mill four cards. You may put a creature card from among them into your hand.")
    private val discard = Cards.invent("Ember Purge", "{0}", "Sorcery", "Discard a card.")
    private val bear = Cards.invent("Gray Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val knight = Cards.invent("Pale Knight", "{1}{W}", "Creature — Human Knight", "", "2/2")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The open choice of cards, with its options and other cards by name. */
    private fun Scenario.choice(): Triple<Decision.ChooseObjects, List<String?>, List<String?>> {
        val d = assertIs<Decision.ChooseObjects>(pending, "a choice of cards")
        fun names(ids: List<arcanum.engine.state.ObjectId>) = ids.map { id -> state.obj(id)?.let { nameOf(it) } }
        return Triple(d, names(d.options), names(d.others))
    }

    @Test
    fun aRevealThenChooseListsTheRevealedCardsItCantChoose() = scenario {
        alice.library(bear, "Forest", knight, "Swamp", "Island")
        alice.hand(reveal)
        alice.casts(reveal)
        resolveAll()
        val (_, options, others) = choice()
        assertEquals(setOf(bear, knight), options.toSet())
        assertEquals(setOf("Forest", "Swamp"), others.toSet())
        alice.chooses(knight)
        resolveAll()
        expect { alice.hand has knight }
    }

    @Test
    fun aLookThenChooseListsTheLookedAtCardsItCantChoose() = scenario {
        alice.library("Forest", bear, "Swamp", "Plains", "Island")
        alice.hand(look)
        alice.casts(look)
        resolveAll()
        val (_, options, others) = choice()
        assertEquals(listOf<String?>(bear), options)
        assertEquals(setOf("Forest", "Swamp", "Plains"), others.toSet())
    }

    @Test
    fun anExileThenChooseAnyOfThemListsNoOtherCards() = scenario {
        alice.library("Forest", bear, "Swamp", "Island")
        alice.hand(exile)
        alice.casts(exile)
        resolveAll()
        val (_, options, others) = choice()
        assertEquals(setOf("Forest", bear, "Swamp"), options.toSet())
        assertTrue(others.isEmpty(), "every exiled card may be chosen: $others")
    }

    @Test
    fun aMillThenChooseListsTheMilledCardsItCantChoose() = scenario {
        alice.library("Forest", bear, "Swamp", "Plains", "Island")
        alice.hand(mill)
        alice.casts(mill)
        resolveAll()
        val (_, options, others) = choice()
        assertEquals(listOf<String?>(bear), options)
        assertEquals(setOf("Forest", "Swamp", "Plains"), others.toSet())
    }

    @Test
    fun aChoiceFromAZoneListsNoOtherCards() = scenario {
        alice.library("Island", "Island")
        alice.hand(discard, bear, "Forest")
        alice.casts(discard)
        resolveAll()
        val (_, options, others) = choice()
        assertEquals(setOf(bear, "Forest"), options.toSet())
        assertTrue(others.isEmpty(), "no group chosen from: $others")
    }
}
