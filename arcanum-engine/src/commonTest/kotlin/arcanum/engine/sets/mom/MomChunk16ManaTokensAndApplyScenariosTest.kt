package arcanum.engine.sets.mom

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * MOM chunk 16 played: the invented names carry the oracle text of Corrupted Conviction, Final Flourish, Grafted Butcher,
 * Phyrexian Pegasus, Change the Equation and Xerex Strobe-Knight.
 */
class MomChunk16ManaTokensAndApplyScenariosTest {
    private val credence = Cards.invent("Corrupted Credence", "{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature.
        Draw two cards.
    """)
    private val fanfare = Cards.invent("Final Fanfare", "{1}{B}", "Instant", """
        Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
        Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -6/-6 until end of turn instead.
    """)
    private val skinner = Cards.invent("Grafted Skinner", "{1}{B}", "Creature — Phyrexian Samurai", """
        When this creature enters, Phyrexians you control gain menace until end of turn.
        Other Phyrexians you control get +1/+1.
        {3}{B}, Sacrifice an artifact or creature: Return this card from your graveyard to the battlefield. Activate only as a sorcery.
    """, "2/2")
    private val gull = Cards.invent("Phyrexian Gull", "{2}{W}", "Creature — Phyrexian Bird", """
        Flying
        Whenever this creature attacks, another target attacking creature without flying gains flying until end of turn.
    """, "2/2")
    private val formula = Cards.invent("Change the Formula", "{1}{U}", "Instant", """
        Choose one —
        • Counter target spell with mana value 2 or less.
        • Counter target red or green spell with mana value 6 or less.
    """)
    private val squire = Cards.invent("Xerex Strobe-Squire", "{2}{U}", "Creature — Human Knight", """
        Flying, vigilance
        {T}: Create a 2/2 white and blue Knight creature token with vigilance. Activate only if you've cast two or more spells this turn.
    """, "2/2")
    private val brute = Cards.invent("Mirebank Brute", "{2}{G}", "Creature — Beast", "", "3/3")
    private val fodder = Cards.invent("Pale Fodder", "{W}", "Creature — Human", "", "1/1")
    private val phyrexian = Cards.invent("Coal Phyrexian", "{2}{B}", "Creature — Phyrexian Horror", "", "3/3")
    private val colossus = Cards.invent("Ember Colossus", "{5}{R}", "Creature — Giant", "", "6/6")
    private val glimpse = Cards.invent("Quiet Glimpse", "{U}", "Instant", "You gain 1 life.")
    private val glimpse2 = Cards.invent("Soft Glimpse", "{U}", "Instant", "You gain 1 life.")

    // CR 601.2f, 118.8: the sacrifice is paid as the spell is cast
    @Test
    fun corruptedCredenceSacrificesACreatureToDrawTwo() = scenario {
        alice.battlefield("Swamp", fodder)
        alice.hand(credence)
        alice.library("Plains", "Island")
        alice.casts(credence, choosing = listOf(fodder))
        resolveAll()
        expect {
            fodder.zone shouldBe Zone.Graveyard
            alice.hand has "Plains"
            alice.hand has "Island"
        }
    }

    // CR 702.33: kicked by sacrificing a creature, -6/-6 instead of -2/-2
    @Test
    fun finalFanfareUnkickedShrinksByTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", fodder)
        alice.hand(fanfare)
        bob.battlefield(brute)
        alice.casts(fanfare, target = brute, kicked = 0)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            fodder.zone shouldBe Zone.Battlefield
            brute.view.characteristics!!.power shouldBe 1
        }
    }

    @Test
    fun finalFanfareKickedShrinksBySix() = scenario {
        alice.battlefield("Swamp", "Swamp", fodder)
        alice.hand(fanfare)
        bob.battlefield(brute)
        alice.casts(fanfare, target = brute, kicked = 1, choosing = listOf(fodder))
        resolveAll()
        expect {
            fodder.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Graveyard
        }
    }

    // CR 205.3m, 613.4c: only Phyrexians gain menace and get the bonus; the lord is not boosted by itself
    @Test
    fun graftedSkinnerGivesPhyrexiansMenaceAndAPlusOne() = scenario {
        alice.battlefield("Swamp", "Swamp", phyrexian, fodder)
        alice.hand(skinner)
        alice.casts(skinner)
        resolveAll()
        expect {
            phyrexian hasKeyword Keyword.Menace.kind
            phyrexian.view.characteristics!!.power shouldBe 4
            skinner hasKeyword Keyword.Menace.kind
            skinner.view.characteristics!!.power shouldBe 2
            fodder lacksKeyword Keyword.Menace.kind
            fodder.view.characteristics!!.power shouldBe 1
        }
    }

    // CR 113.6m, 602.2: the ability works from the graveyard, as a sorcery
    @Test
    fun graftedSkinnerReturnsFromTheGraveyardForASacrifice() = scenario {
        alice.graveyard(skinner)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", fodder)
        alice.activates(skinner, choosing = listOf(fodder))
        resolveAll()
        expect {
            skinner.zone shouldBe Zone.Battlefield
            fodder.zone shouldBe Zone.Graveyard
        }
    }

    // CR 508.1b: only the attacking creature without flying is a legal target (a lone target is chosen by itself)
    @Test
    fun phyrexianGullGivesAnAttackerFlying() = scenario {
        alice.battlefield(gull, brute, fodder)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gull, brute)
        playOut()
        expect {
            brute hasKeyword Keyword.Flying.kind
            fodder lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 700.2, 202.3: the second mode reaches a red spell with mana value 6, the first does not
    @Test
    fun changeTheFormulaCountersARedSpellOfValueSix() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(colossus)
        alice.battlefield("Island", "Island")
        alice.hand(formula)
        turn(active = bob)
        bob.casts(colossus)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(formula, target = colossus, modes = listOf(0)) }
    }

    @Test
    fun changeTheFormulaSecondModeCounters() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(colossus)
        alice.battlefield("Island", "Island")
        alice.hand(formula)
        turn(active = bob)
        bob.casts(colossus)
        bob.passes()
        alice.casts(formula, target = colossus, modes = listOf(1))
        resolveAll()
        expect { colossus.zone shouldBe Zone.Graveyard }
    }

    // CR 602.5b: the tap ability needs two spells cast this turn
    @Test
    fun xerexStrobeSquireNeedsTwoSpellsThisTurn() = scenario {
        alice.battlefield("Island", "Island", squire)
        alice.hand(glimpse, glimpse2)
        assertFailsWith<IllegalArgumentException> { alice.activates(squire) }
        alice.casts(glimpse)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(squire) }
        alice.casts(glimpse2)
        resolveAll()
        alice.activates(squire)
        resolveAll()
        expect { card("Knight Token") hasKeyword Keyword.Vigilance.kind }
    }
}
