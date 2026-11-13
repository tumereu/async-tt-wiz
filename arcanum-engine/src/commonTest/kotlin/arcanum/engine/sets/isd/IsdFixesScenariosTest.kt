package arcanum.engine.sets.isd

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** ISD fixes: rules the set's cards showed the engine got wrong. Invented names carry the oracle text of the real cards. */
class IsdFixesScenariosTest {
    private val tail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    private val kindling = Cards.invent("Second Kindling", "{3}{R}", "Sorcery", """
        Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.
        Flashback {4}{R} $tail
    """)
    private val glimmer = Cards.invent("Glimmer Thought", "{1}{U}", "Instant", "Draw a card.\nFlashback {2}{U} $tail")

    private val ledge = Cards.invent("Return from the Ledge", "{4}{U}{U}", "Enchantment",
        "Exile a creature card from your graveyard and pay its mana cost: Create a token that's a copy of that card. Activate only as a sorcery.")
    private val gutter = Cards.invent("Ooze Gutter", "{4}{G}", "Enchantment",
        "Whenever a nontoken creature you control dies, put a slime counter on this enchantment, then create a green Ooze creature token with \"This token's power and toughness are each equal to the number of slime counters on Ooze Gutter.\"")
    private val stag = Cards.invent("Ledgewood Stag", "{2}{G}", "Creature — Elk", "", "3/3")
    private val heron = Cards.invent("Ledgewood Heron", "{1}{U}", "Creature — Bird", "Flying", "1/2")
    private val vole = Cards.invent("Gutterbank Vole", "{G}", "Creature — Rodent", "", "1/1")
    private val shrew = Cards.invent("Gutterbank Shrew", "{G}", "Creature — Rodent", "", "1/1")
    private val jolt = Cards.invent("Gutter Jolt", "{R}", "Instant", "Gutter Jolt deals 2 damage to target creature.")
    private val image = Cards.invent("Second Ooze Image", "{U}", "Instant", "Create a token that's a copy of target creature you control.")

    private val insight = Cards.invent("Costless Insight", null, "Sorcery", "Draw three cards.")
    private val hulk = Cards.invent("Costless Hulk", null, "Creature — Golem", "", "5/5")

    private fun Scenario.castsOf(name: String) = Engine.options(state, alice.id)
        .filterIsInstance<Input.Cast>().filter { it.card == card(name) }

    // CR 702.34a, 601.2b: a card with its own flashback that gains a second flashback ability can be cast with either; the player
    // chooses which (both are offered while both costs can be paid)
    @Test
    fun aCardWithTwoFlashbackAbilitiesOffersBoth() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island", "Island", "Island")
        alice.hand(kindling)
        alice.graveyard(glimmer)
        alice.casts(kindling)
        resolveAll()
        val ways = castsOf("Glimmer Thought").map { it.way.permission }.distinct()
        assertEquals(2, ways.size, "both flashback abilities are offered: $ways")
    }

    // CR 702.34a, 601.2f: the granted flashback costs its mana cost {1}{U}, less than its own flashback {2}{U}; with two Islands the
    // card is cast for the granted one
    @Test
    fun theGrantedFlashbackIsUsableBesideTheCardsOwn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island", "Island")
        alice.hand(kindling)
        alice.graveyard(glimmer)
        alice.library("Plains", "Plains")
        alice.casts(kindling)
        resolveAll()
        val casts = castsOf("Glimmer Thought")
        assertTrue(casts.isNotEmpty(), "the granted flashback {1}{U} can be paid with two Islands")
        alice.casts(glimmer, way = casts.first().way)
        resolveAll()
        expect {
            glimmer.zone shouldBe Zone.Exile
            alice.hand.size shouldBe 1
        }
    }

    // CR 601.2b, 601.2f, 601.2h: the exiled card is chosen first; the mana paid is its mana cost {2}{G}; the token copies it (CR 707.2)
    @Test
    fun ledgePaysTheChosenCardsManaCostAndCopiesIt() = scenario {
        alice.battlefield(ledge, "Forest", "Forest", "Forest")
        alice.graveyard(stag, heron)
        alice.activates("Return from the Ledge", choosing = listOf("Ledgewood Stag"))
        resolveAll()
        expect { heron.zone shouldBe Zone.Graveyard }
        assertEquals(1, state.exile.count { state.obj(it)?.let { o -> nameOf(o) } == "Ledgewood Stag" }, "the card was exiled for the cost")
        assertEquals(1, state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == "Ledgewood Stag" }, "a token copy of the exiled card")
        assertTrue(state.battlefield.filter { state.obj(it)?.let { o -> nameOf(o) } == "Forest" }.all { state.obj(it)!!.tapped }, "the three Forests paid {2}{G}")
    }

    // CR 601.2f, 601.2h: choosing the blue card makes the cost {1}{U}, which three Forests can't pay
    @Test
    fun ledgeCantPayABlueCardsCostWithForests() = scenario {
        alice.battlefield(ledge, "Forest", "Forest", "Forest")
        alice.graveyard(stag, heron)
        alice.activates("Return from the Ledge", choosing = listOf("Ledgewood Heron"))
        assertTrue(decision<arcanum.engine.decision.Decision.ManaPayment>().suggestion.isEmpty(), "{1}{U} can't be paid with Forests")
    }

    // CR 201.5a, 604.3: the Ooze's power and toughness are the counters on the enchantment that created it, growing with it; once that
    // enchantment has left the battlefield the token sees no counters (CR 400.7) and dies (CR 704.5f)
    @Test
    fun gutterOozesCountTheCreatorsSlimeCounters() = scenario {
        alice.battlefield(gutter, vole, shrew, "Mountain", "Mountain")
        alice.hand("Storm Spark", jolt)
        alice.casts("Storm Spark", target = "Gutterbank Vole")
        resolveAll()
        val first = state.battlefield.first { state.obj(it)?.token == true }
        expect { CardRef(first, "Ooze").power shouldBe 1 }
        alice.casts(jolt, target = "Gutterbank Shrew")
        resolveAll()
        assertEquals(2, state.battlefield.count { state.obj(it)?.token == true })
        expect {
            CardRef(first, "Ooze").power shouldBe 2
            CardRef(first, "Ooze").toughness shouldBe 2
        }
        move("Ooze Gutter", Zone.Graveyard)
        expect { CardRef(first, "Ooze").toughness shouldBe 0 }
        allPass()
        assertEquals(0, state.battlefield.count { state.obj(it)?.token == true }, "0/0 Oozes die once their creator has left")
    }

    // CR 201.5a: a copy of the Ooze token refers to the enchantment that created the original (the copy's own creator is the copying
    // spell); CR 707.2: the reference is part of the copiable values
    @Test
    fun aCopyOfAGutterOozeCountsTheSameEnchantment() = scenario(players = 3) {
        alice.battlefield(gutter, vole, "Mountain", "Island")
        alice.hand("Storm Spark", image)
        alice.casts("Storm Spark", target = "Gutterbank Vole")
        resolveAll()
        val ooze = state.battlefield.first { state.obj(it)?.token == true }
        alice.casts(image, target = "Ooze Token")
        resolveAll()
        val copy = state.battlefield.first { state.obj(it)?.token == true && it != ooze }
        expect {
            CardRef(copy, "copy").power shouldBe 1
            CardRef(copy, "copy").toughness shouldBe 1
            image.zone shouldBe Zone.Graveyard
        }
    }

    // CR 118.6: a card with no mana cost has an unpayable cost: it can't be cast for it from the hand
    @Test
    fun aSpellWithNoManaCostCantBeCastForIt() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(insight)
        assertTrue(castsOf("Costless Insight").isEmpty(), "no cast is offered")
        assertFailsWith<IllegalArgumentException> { alice.casts(insight) }
    }

    // CR 118.6, 702.34a: flashback "equal to its mana cost" of a card with no mana cost is unpayable
    @Test
    fun grantedFlashbackOfACardWithNoManaCostIsUnpayable() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island")
        alice.hand(kindling)
        alice.graveyard(insight)
        alice.casts(kindling)
        resolveAll()
        assertTrue(castsOf("Costless Insight").isEmpty(), "its granted flashback can't be paid")
        assertFailsWith<IllegalArgumentException> { alice.casts(insight) }
    }

    // CR 118.6: "pay its mana cost" of a card with no mana cost is unpayable: that choice is refused, another card may be chosen
    @Test
    fun ledgeRefusesACardWithNoManaCost() = scenario {
        alice.battlefield(ledge, "Forest", "Forest", "Forest")
        alice.graveyard(hulk, stag)
        assertFailsWith<IllegalArgumentException> { alice.activates("Return from the Ledge", choosing = listOf("Costless Hulk")) }
        expect { hulk.zone shouldBe Zone.Graveyard }
    }
}
