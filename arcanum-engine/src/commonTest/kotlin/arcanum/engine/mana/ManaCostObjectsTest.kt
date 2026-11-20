package arcanum.engine.mana

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Mana abilities whose costs use cards (CR 605.3a, 602.2b, 601.2h) as payment options: one is withheld only when its cost certainly
 * can't be paid (owner 2026-11-19: no false denials, as few false offers as can be told cheaply).
 */
class ManaCostObjectsTest {
    private val cipher = Cards.invent("Tallyglass Cipher", "{2}", "Artifact", """
        {T}, Collect evidence 3: Add one mana of any color. Put an unlock counter on this artifact. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
    """)
    private val imp = Cards.invent("Scrapgnaw Imp", "{1}{B}", "Creature — Imp", "Discard a card: Add {B}.", "1/1")
    private val tender = Cards.invent("Ashpicker Tender", "{1}{G}", "Creature — Insect Druid", "{T}, Exile a card from your graveyard: Add one mana of any color.", "1/1")
    private val tally = Cards.invent("Quick Tally", "{1}", "Instant", "Draw a card.")
    private val notes = Cards.invent("Loose Case Notes", "{1}", "Sorcery", "Draw a card.")
    private val dossier = Cards.invent("Thick Dossier", "{1}{U}", "Sorcery", "Draw two cards.")
    private val memo = Cards.invent("Stray Memo", "{1}", "Sorcery", "Draw a card.")

    /** Starts casting Quick Tally ({1}) and returns its payment. */
    private fun Scenario.payment(): Decision.ManaPayment {
        perform(Input.Cast(alice.id, card(tally)))
        return assertIs<Decision.ManaPayment>(pending)
    }

    private fun Scenario.uses(d: Decision.ManaPayment, name: String): List<ManaUse.Ability> =
        d.options.filterIsInstance<ManaUse.Ability>().filter { it.source == card(name) }

    // STK-211, CR 701.59a, 602.2b: collecting evidence 3 needs cards with total mana value 3 or more in the graveyard; with none, its mana
    // ability can't be activated and isn't offered
    @Test
    fun evidenceAbilityNotOfferedWithAnEmptyGraveyard() = scenario {
        alice.battlefield(cipher)
        alice.hand(tally)
        assertEquals(emptyList(), uses(payment(), cipher))
    }

    // STK-211, CR 701.59a, 602.2b: a total of 2 can't collect evidence 3
    @Test
    fun evidenceAbilityNotOfferedBelowItsTotal() = scenario {
        alice.battlefield(cipher)
        alice.graveyard(dossier)
        alice.hand(tally)
        assertEquals(emptyList(), uses(payment(), cipher))
    }

    // STK-211, CR 701.59a, 605.3a, 601.2g–h: with enough in the graveyard it is offered with cards that reach the total, and pays: the cards are
    // exiled, the counter comes with the mana
    @Test
    fun evidenceAbilityPaysWithCardsReachingTheTotal() = scenario {
        alice.battlefield(cipher)
        alice.graveyard(notes, dossier)
        alice.hand(tally)
        val use = uses(payment(), cipher).first { it.color == Color.Blue }
        assertEquals(listOf<Answer>(Answer.Objects(listOf(card(notes), card(dossier)))), use.answers)
        answer(Answer.ManaPayment(listOf(use)))
        expect {
            alice.exile has notes
            alice.exile has dossier
            cipher.counters(CounterKind.Named("unlock")) shouldBe 1
            cipher.tapped shouldBe true
            stack has tally
        }
    }

    // STK-211, CR 601.2g: mana abilities are activated in any order while paying; discarding for mana first may fill the graveyard, so the
    // evidence can't be called short (no false denial)
    @Test
    fun evidenceAbilityStaysOfferedWhenAnotherSourceMayFillTheGraveyard() = scenario {
        alice.battlefield(cipher, imp)
        alice.graveyard(dossier)
        alice.hand(tally, notes)
        assertTrue(uses(payment(), cipher).isNotEmpty())
    }

    // STK-211, CR 602.2b, 601.2h: "exile a card from your graveyard" with an empty graveyard can't be paid
    @Test
    fun graveyardExileNotOfferedWithAnEmptyGraveyard() = scenario {
        alice.battlefield(tender)
        alice.hand(tally)
        assertEquals(emptyList(), uses(payment(), tender))
    }

    // STK-211, CR 601.2g: but a discard for mana may put a card there first
    @Test
    fun graveyardExileStaysOfferedBesideADiscardForMana() = scenario {
        alice.battlefield(tender, imp)
        alice.hand(tally, notes)
        assertTrue(uses(payment(), tender).isNotEmpty())
    }

    // STK-211, CR 605.3a, 701.9a: a mana ability whose cost discards a card is usable while paying: offered with each card it may discard, and pays
    @Test
    fun discardForManaPaysWhilePaying() = scenario {
        alice.battlefield(imp)
        alice.hand(tally, notes)
        val use = uses(payment(), imp).single()
        assertEquals(listOf<Answer>(Answer.Objects(listOf(card(notes)))), use.answers)
        answer(Answer.ManaPayment(listOf(use)))
        expect {
            alice.graveyard has notes
            stack has tally
        }
    }

    // STK-211, STK-214, CR 601.2h, 701.59a: the payer chooses which cards collect the evidence: the payment lists the choice (the graveyard's cards,
    // total mana value 3), and a set other than the listed one pays too, exiling those cards only
    @Test
    fun evidenceIsCollectedWithTheCardsThePayerChooses() = scenario {
        alice.battlefield(cipher)
        alice.graveyard(notes, dossier, memo)
        alice.hand(tally)
        val d = payment()
        val choice = d.costChoices.single { it.source == card(cipher) }
        assertEquals(setOf(card(notes), card(dossier), card(memo)), choice.options.toSet())
        assertEquals(0, choice.index)
        assertEquals(3, (choice.total?.atLeast as? arcanum.api.Value.Fixed)?.value)
        val listed = uses(d, cipher).first { it.color == Color.Blue }
        val mine = listed.copy(answers = listOf(Answer.Objects(listOf(card(dossier), card(memo)))))
        assertTrue(mine !in d.options, "not the listed set")
        answer(Answer.ManaPayment(listOf(mine)))
        expect {
            alice.exile has dossier
            alice.exile has memo
            alice.graveyard has notes
            stack has tally
        }
    }

    // STK-211, CR 601.2h, 701.59a: a chosen set short of the total doesn't pay
    @Test
    fun evidenceShortOfItsTotalIsRefused() = scenario {
        alice.battlefield(cipher)
        alice.graveyard(notes, dossier, memo)
        alice.hand(tally)
        val d = payment()
        val listed = uses(d, cipher).first { it.color == Color.Blue }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            answer(Answer.ManaPayment(listOf(listed.copy(answers = listOf(Answer.Objects(listOf(card(notes), card(memo))))))))
        }
    }

    // STK-214, CR 601.2h, 701.9a: Autotap's fill keeps a discard for mana of the payer's own choosing exactly as given
    @Test
    fun aDiscardOfThePayersChoosingIsKeptByTheFill() = scenario {
        alice.battlefield(imp)
        alice.hand(tally, notes, memo)
        val d = payment()
        assertEquals(setOf(card(notes), card(memo)), d.costChoices.single().options.toSet() - card(tally))
        val mine = uses(d, imp).first().copy(answers = listOf(Answer.Objects(listOf(card(memo)))))
        val filled = arcanum.engine.CastScreen.fillPayment(state, emptyList(), emptyList(), listOf(mine))
        assertEquals(listOf<arcanum.engine.decision.ManaUse>(mine), filled)
        answer(Answer.ManaPayment(filled))
        expect {
            alice.graveyard has memo
            stack has tally
        }
    }

    // STK-211, CR 602.2b, 701.9a: with no other card in hand, it has nothing to discard
    @Test
    fun discardForManaNotOfferedWithAnEmptyHand() = scenario {
        alice.battlefield(imp)
        alice.hand(tally)
        assertEquals(emptyList(), uses(payment(), imp))
    }
}
