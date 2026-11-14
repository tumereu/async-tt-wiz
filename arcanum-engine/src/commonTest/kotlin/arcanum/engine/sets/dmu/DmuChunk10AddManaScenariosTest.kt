package arcanum.engine.sets.dmu

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The DMU mana cards of chunk 10, played: a painland (the pattern of six), tapping untapped nontoken artifacts or a legendary creature
 * as a cost, a land with two kinds of restricted mana, and a creature that animates a land at sorcery speed.
 */
class DmuChunk10AddManaScenariosTest {
    private val wastes = Cards.invent("Dusk Wastes", null, "Land", """
        {T}: Add {C}.
        {T}: Add {W} or {U}. This land deals 1 damage to you.
    """)
    private val merrow = Cards.invent("Merrow, Keeper of Gears", "{1}{R}{G}", "Legendary Creature — Elf Artificer", """
        Tap an untapped nontoken artifact you control: Add {G}.
        Tap two untapped nontoken artifacts you control: Exile the top card of your library. You may play it this turn.
    """, "3/3")
    private val plaza = Cards.invent("Plaza of Banners", null, "Land", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a legendary spell.
        {T}: Add one mana of any color among legendary permanents you control.
        {3}, {T}, Exile this land: Target legendary creature gains hexproof and indestructible until end of turn.
    """)
    private val relic = Cards.invent("Relic of Sagas", "{3}", "Artifact", """
        {T}: Add one mana of any color.
        Tap an untapped legendary creature you control: Add one mana of any color.
    """)
    private val sprite = Cards.invent("Loamweaver Sprite", "{1}{G}", "Creature — Elf Druid", """
        {T}: Add one mana of any color.
        {T}: Target land you control becomes a 3/3 Elemental creature with haste until end of turn. It's still a land. Activate only as a sorcery.
    """, "1/3")
    private val regent = Cards.invent("Briar Regent", "{G}{G}", "Legendary Creature — Elf", "", "2/2")
    private val legendSpell = Cards.invent("Briar Legend", "{G}", "Legendary Creature — Elf", "", "1/1")
    private val plainSpell = Cards.invent("Briar Plain", "{G}", "Creature — Elf", "", "1/1")
    private val gadget = Cards.invent("Briar Gadget", "{1}", "Artifact", "")
    private val gizmo = Cards.invent("Briar Gizmo", "{1}", "Artifact", "")
    private val hoard = Cards.invent("Briar Hoard", "{0}", "Sorcery", "Create a Treasure token.")
    private val topCard = Cards.invent("Briar Topcard", "{0}", "Artifact", "")

    // CR 605.1a, 120.3: only the coloured ability hurts; the colourless one is free
    @Test
    fun painlandColourlessIsFree() = scenario {
        alice.battlefield(wastes)
        alice.activates(wastes, ability = 0)
        expect {
            alice.pool.size shouldBe 1
            alice.life shouldBe 20
        }
    }

    // CR 605.3b, 120.3: the mana ability resolves at once and deals 1 damage to you
    @Test
    fun painlandColouredCostsOneLife() = scenario {
        alice.battlefield(wastes)
        alice.activates(wastes, ability = 1, color = Color.Blue)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        expect {
            alice.pool.size shouldBe 1
            alice.life shouldBe 19
        }
    }

    // CR 602.2b, 605.1a: tapping an untapped nontoken artifact is the cost; the token can't pay it
    @Test
    fun tappingANontokenArtifactAddsGreen() = scenario {
        alice.battlefield(merrow, gadget)
        alice.activates(merrow, ability = 0, choosing = listOf(gadget))
        expect {
            gadget.tapped shouldBe true
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun aTokenArtifactCannotBeTappedForIt() = scenario {
        alice.battlefield(merrow)
        alice.hand(hoard)
        alice.casts(hoard)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(merrow, ability = 0) }
    }

    // CR 602.2b: two untapped nontoken artifacts are tapped, then the top card may be played this turn (CR 601.2, 305.1)
    @Test
    fun tappingTwoArtifactsExilesTheTopCardToPlay() = scenario {
        alice.battlefield(merrow, gadget, gizmo)
        alice.library(topCard)
        alice.activates(merrow, ability = 1, choosing = listOf(gadget, gizmo))
        resolveAll()
        alice.casts(topCard)
        resolveAll()
        expect {
            gadget.tapped shouldBe true
            gizmo.tapped shouldBe true
            topCard.zone shouldBe arcanum.api.Zone.Battlefield
        }
    }

    // CR 106.6: the restricted mana pays for a legendary spell
    @Test
    fun restrictedManaCastsALegendarySpell() = scenario {
        alice.battlefield(plaza)
        alice.hand(legendSpell)
        alice.activates(plaza, ability = 1, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        alice.casts(legendSpell)
        resolveAll()
        expect { legendSpell.zone shouldBe arcanum.api.Zone.Battlefield }
    }

    // CR 106.6: it can't pay for a spell that isn't legendary
    @Test
    fun restrictedManaCannotCastANonlegendarySpell() = scenario {
        alice.battlefield(plaza)
        alice.hand(plainSpell)
        alice.activates(plaza, ability = 1, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        perform(Input.Cast(alice.id, card(plainSpell)))
        val pay = decision<Decision.ManaPayment>()
        assertTrue(pay.options.none { it is ManaUse.Pool })
    }

    // CR 106.5: with no legendary permanent there is no colour to add, so no mana is produced
    @Test
    fun colourAmongLegendariesNeedsOne() = scenario {
        alice.battlefield(plaza)
        alice.activates(plaza, ability = 2)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 0 }
    }

    // CR 106.1b, 105.2: the legendary green creature offers green
    @Test
    fun colourAmongLegendariesIsTheirColour() = scenario {
        alice.battlefield(plaza, regent)
        alice.activates(plaza, ability = 2, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 602.2b: tapping an untapped legendary creature adds mana; a nonlegendary creature can't pay
    @Test
    fun relicTapsALegendaryCreature() = scenario {
        alice.battlefield(relic, regent)
        alice.activates(relic, ability = 1, color = Color.Red, choosing = listOf(regent))
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        expect {
            regent.tapped shouldBe true
            relic.tapped shouldBe false
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun relicNeedsALegendaryCreature() = scenario {
        alice.battlefield(relic, sprite)
        assertFailsWith<IllegalArgumentException> { alice.activates(relic, ability = 1, color = Color.Red) }
    }

    // CR 613.1d, 205.1b, 702.10: the land is a 3/3 hasty Elemental creature and still a land until end of turn
    @Test
    fun spriteAnimatesALand() = scenario {
        alice.battlefield(sprite, "Forest")
        alice.activates(sprite, ability = 1, target = "Forest")
        resolveAll()
        expect {
            "Forest".power shouldBe 3
            "Forest" hasKeyword Keyword.Haste
            "Forest".types.contains(CardType.Land) shouldBe true
            "Forest".types.contains(CardType.Creature) shouldBe true
        }
    }
}
