package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Lorwyn Eclipsed cards in play: -1/-1 counters removed while tapped, losing creature types, targets that share a type, a land of
 * a chosen type among a list, counters that can't be put, a creature that returns without its abilities, cards that return at the next end
 * step, a dream counter on an exiled spell, exiling until a total mana value, and a transforming legend. */
class EclCardsScenariosTest {
    private val m1m1 = CounterKind.PowerToughness(-1, -1)
    private val shoalkeeper = Cards.invent("Weighed-Down Shoalkeeper", "{1}{W}", "Creature — Merfolk Soldier", """
        This creature enters with three -1/-1 counters on it.
        Whenever this creature becomes tapped while it has a -1/-1 counter on it, remove a -1/-1 counter from it.
    """, "5/4")
    private val reversal = Cards.invent("Faceless Reversal", "{1}{B}", "Kindred Instant — Shapeshifter", """
        Changeling (This card is every creature type.)
        Target creature gets +3/-3 and loses all creature types until end of turn.
    """)
    private val changeling = Cards.invent("Carapace Clutchling", "{3}{G/U}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        Reach
    """, "3/4")
    private val disinter = Cards.invent("Disinter", "{1}{B}", "Instant", """
        Choose one —
        • Return target creature card from your graveyard to your hand.
        • Return two target creature cards that share a creature type from your graveyard to your hand.
    """)
    private val domains = Cards.invent("Shadowed Domains", null, "Land", """
        As this land enters, choose Elemental, Elf, Faerie, Giant, Goblin, Kithkin, Merfolk, or Treefolk.
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a spell of the chosen type or activate an ability of a source of the chosen type.
    """)
    private val petalbind = Cards.invent("Petalbind", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature can't become untapped and can't have counters put on it.
    """)
    private val wretch = Cards.invent("Gagging Wretch", "{2}{B}", "Creature — Goblin",
        "When this creature dies, if it had a -1/-1 counter on it, return it to the battlefield under its owner's control and it loses all abilities.", "4/2")
    private val glow = Cards.invent("Daybreak's Glow", "{3}{W}", "Sorcery", """
        Exile any number of target creatures. At the beginning of the next end step, return those cards to the battlefield tapped under their owners' control.
        Until your next turn, prevent all damage that would be dealt to you.
        Exile Daybreak's Glow.
    """)
    private val reverist = Cards.invent("Colossus Reverist", "{2}{R}{R}", "Creature — Giant Wizard", """
        Whenever you cast an instant or sorcery spell from your hand, exile that card with a dream counter on it instead of putting it into your graveyard as it resolves.
        Whenever this creature attacks, you may cast a spell from among cards you own in exile with dream counters on them without paying its mana cost.
    """, "4/4")
    private val reaping = Cards.invent("Reverie Reaping", "{5}{U/B}{U/B}", "Sorcery",
        "Each opponent exiles cards from the top of their library until they have exiled cards with total mana value 5 or greater this way. Until end of turn, you may cast cards exiled this way without paying their mana costs.")
    private val maewen = Cards.inventFaces("Transform",
        arcanum.engine.cards.face("Maewen, Hamlet's Hearth", "{2}{W}", "Legendary Creature — Kithkin Warrior", """
            Whenever this creature enters or transforms into Maewen, Hamlet's Hearth, create a 1/1 green and white Kithkin creature token.
            At the beginning of your first main phase, you may pay {G}. If you do, transform Maewen.
        """.trimIndent(), "3/2", null, null),
        arcanum.engine.cards.face("Maewen, Hilltop's Wit", null, "Legendary Creature — Kithkin Soldier", """
            {T}: Add X {G} or X {W}, where X is the number of other creatures you control.
            At the beginning of your first main phase, you may pay {W}. If you do, transform Maewen.
        """.trimIndent(), "3/2", null, "G"))
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val goblin = Cards.invent("Bog Goblin", "{B}", "Creature — Goblin", "", "1/1")
    private val elf = Cards.invent("Grove Elf", "{G}", "Creature — Elf", "", "1/1")
    private val jolt = Cards.invent("Spark Jolt", "{R}", "Instant", "Spark Jolt deals 2 damage to any target.")
    private val blightRot = Cards.invent("Canker Decay", "{2}{B}", "Instant", "Put four -1/-1 counters on target creature.")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val giant = Cards.invent("Hill Giant Kin", "{3}{R}", "Creature — Giant", "", "3/3")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 603.2: "while it has a -1/-1 counter on it", checked as it becomes tapped
    @Test
    fun aCounterGoesWhenItBecomesTapped() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Plains", "Plains")
        alice.hand(shoalkeeper)
        alice.casts(shoalkeeper)
        resolveAll()
        expect { shoalkeeper.counters(m1m1) shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(shoalkeeper)
        resolveAll()
        expect {
            shoalkeeper.counters(m1m1) shouldBe 2
            shoalkeeper.power shouldBe 3
        }
    }

    // CR 613.1d: a changeling loses its creature types too
    @Test
    fun losingAllCreatureTypes() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(reversal)
        bob.battlefield(changeling)
        alice.casts(reversal, target = changeling)
        resolveAll()
        expect {
            changeling.power shouldBe 6
            changeling.subtypes shouldBe emptyList()
            changeling.view.characteristics?.hasSubtype(Subtype("Elf")) shouldBe false
        }
    }

    // CR 601.2c: the two targets must share a creature type
    @Test
    fun twoTargetsThatShareACreatureType() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(disinter)
        alice.graveyard(bear, goblin, changeling)
        assertFailsWith<IllegalArgumentException> { alice.casts(disinter, modes = listOf(1), targets = listOf(bear, goblin)) }
    }

    @Test
    fun aChangelingSharesEveryType() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(disinter)
        alice.graveyard(bear, goblin, changeling)
        alice.casts(disinter, modes = listOf(1), targets = listOf(goblin, changeling))
        resolveAll()
        expect {
            alice.hand has goblin
            alice.hand has changeling
        }
    }

    // CR 205.3m, 106.6: one of the listed types; its coloured mana only for spells of that type
    @Test
    fun aTypeChosenAmongAList() = scenario {
        alice.hand(domains, elf, goblin)
        alice.plays(domains)
        assertTrue(pending is Decision.ChooseSubtype, "a creature type is asked: $pending")
        val offered = (pending as Decision.ChooseSubtype).among
        assertEquals(8, offered?.size)
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        alice.casts(elf, paying = listOf("Shadowed Domains:G"))
        resolveAll()
        expect { alice.battlefield has elf }
    }

    // CR 614.1a: counters that would be put on it aren't; it stays tapped
    @Test
    fun noCountersOnTheEnchantedCreature() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Swamp")
        alice.hand(petalbind, blightRot)
        bob.battlefield(ogre)
        alice.casts(petalbind, target = ogre)
        resolveAll()
        alice.casts(blightRot, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(m1m1) shouldBe 0
        }
    }

    // CR 603.10a, 611.2e: it returns, without its abilities
    @Test
    fun itReturnsWithoutItsAbilities() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", wretch)
        alice.hand(blightRot, jolt)
        alice.casts(blightRot, target = wretch)
        resolveAll()
        expect {
            alice.battlefield has wretch
            wretch.counters(m1m1) shouldBe 0
            wretch.view.characteristics?.abilities.orEmpty().isEmpty() shouldBe true
        }
        // dying again without a counter: it stays in the graveyard
        alice.casts(jolt, target = wretch)
        resolveAll()
        expect { alice.graveyard has wretch }
    }

    // CR 603.7: they return at the beginning of the next end step; CR 608.2n: the spell exiles itself
    @Test
    fun exiledCreaturesReturnAtTheNextEndStep() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(glow)
        bob.battlefield(ogre)
        alice.casts(glow, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.exile has glow
            alice.exile has bear
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
            ogre.controller shouldBe bob.id
        }
    }

    // CR 614.1a: exiled with a dream counter as it resolves, then cast for free when the creature attacks
    @Test
    fun aDreamCounterOnTheExiledSpell() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Mountain", reverist)
        alice.hand(jolt)
        alice.casts(jolt, target = "bob")
        resolveAll()
        expect {
            alice.exile has jolt
            jolt.counters(CounterKind.Named("dream")) shouldBe 1
        }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(reverist)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(jolt)
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        assertEquals(16, bob.life)
    }

    // CR 608.2c: one card at a time until the total is reached
    @Test
    fun exilingUntilATotalManaValue() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(reaping)
        bob.library(goblin, ogre, giant, bear)
        alice.casts(reaping)
        resolveAll()
        expect {
            bob.exile has goblin
            bob.exile has ogre
            bob.exile has giant
            bob.library has bear
        }
    }

    // CR 701.27e: "transforms into" this face triggers again
    @Test
    fun aLegendTransformsAtTheFirstMainPhase() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Plains", "Plains", "Plains", "Forest")
        alice.hand(maewen)
        alice.casts(maewen, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        expect { alice.battlefield has "Kithkin Token" }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) alice.answers(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        resolveAll()
        expect { state.obj(card("Maewen, Hamlet's Hearth // Maewen, Hilltop's Wit").id)?.face shouldBe 1 }
    }
}
