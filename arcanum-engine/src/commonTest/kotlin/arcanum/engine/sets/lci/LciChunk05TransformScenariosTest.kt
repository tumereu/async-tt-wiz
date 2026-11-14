package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** LCI chunk 05: transforming double-faced cards played: gods that return as their temple lands, the temples' activation conditions, a
 * replacement raising damage to an amount, tripled creature tokens, a cast spell gaining rebound, a graveyard creature cast with a finality
 * counter and an extra type, and an enchantment transforming once its counters add up. Invented names carry the oracle text. */
class LciChunk05TransformScenariosTest {
    private val zotlacai = Cards.inventFaces("Transform",
        face("Zotlacai, Hollow Treachery", "{3}{B}{B}", "Legendary Creature — Bat God", """
            Flying, lifelink
            Whenever Zotlacai attacks, each opponent discards a card. For each opponent who can't, you draw a card.
            Whenever an opponent discards a land card, create a 1/1 black Bat creature token with flying.
            When Zotlacai dies, return it to the battlefield tapped and transformed under its owner's control.
        """, "4/4", null, null),
        face("Sanctum of the Hollow", null, "Land", """
            (Transforms from Zotlacai, Hollow Treachery.)
            {T}: Add {B}.
            {2}{B}, {T}: Transform this land. Activate only if a player has one or fewer cards in hand and only as a sorcery.
        """, null, null, null),
    )
    private val tavrix = Cards.inventFaces("Transform",
        face("Ojen Tavrix, Deepest Fury", "{2}{R}{R}", "Legendary Creature — God", """
            Trample
            If a red source you control would deal an amount of noncombat damage less than Ojen Tavrix's power to an opponent, that source deals damage equal to Ojen Tavrix's power instead.
            When Ojen Tavrix dies, return it to the battlefield tapped and transformed under its owner's control.
        """, "4/4", null, null),
        face("Shrine of Fury", null, "Land", """
            (Transforms from Ojen Tavrix, Deepest Fury.)
            {T}: Add {R}.
            {2}{R}, {T}: Transform this land. Activate only if red sources you controlled dealt 4 or more noncombat damage this turn and only as a sorcery.
        """, null, null, null),
    )
    private val moskal = Cards.inventFaces("Transform",
        face("Ojen Moskal, Deepest Bloom", "{3}{G}{G}", "Legendary Creature — God", """
            Trample
            Whenever Ojen Moskal deals combat damage to a player, reveal that many cards from the top of your library. You may put a creature card and/or a land card from among them onto the battlefield. Put the rest on the bottom in a random order.
            When Ojen Moskal dies, return it to the battlefield tapped and transformed under its owner's control.
        """, "6/5", null, null),
        face("Shrine of Bloom", null, "Land", """
            (Transforms from Ojen Moskal, Deepest Bloom.)
            {T}: Add {G}.
            {2}{G}, {T}: Transform this land. Activate only if you control ten or more permanents and only as a sorcery.
        """, null, null, null),
    )
    private val quetzi = Cards.inventFaces("Transform",
        face("Ojen Quetzi, Deepest Tide", "{2}{U}{U}", "Legendary Creature — God", """
            Flying
            Whenever you cast an instant spell from your hand, it gains rebound. (Exile it as it resolves. At the beginning of your next upkeep, you may cast it from exile without paying its mana cost.)
            When Ojen Quetzi dies, return it to the battlefield tapped and transformed under its owner's control with three time counters on it.
        """, "4/3", null, null),
        face("Shrine of Turning Hours", null, "Land", """
            (Transforms from Ojen Quetzi, Deepest Tide.)
            {T}: Add {U}. Remove a time counter from this land.
            {2}{U}, {T}: Transform this land. Activate only if it has no time counters on it and only as a sorcery.
        """, null, null, null),
    )
    private val tolqa = Cards.inventFaces("Transform",
        face("Ojen Tolqa, Deepest Bedrock", "{4}{W}{W}", "Legendary Creature — God", """
            Vigilance
            If one or more creature tokens would be created under your control, three times that many of those tokens are created instead.
            When Ojen Tolqa dies, return it to the battlefield tapped and transformed under its owner's control.
        """, "6/6", null, null),
        face("Shrine of Masonry", null, "Land", """
            (Transforms from Ojen Tolqa, Deepest Bedrock.)
            {T}: Add {W}.
            {2}{W}, {T}: Transform this land. Activate only if you attacked with three or more creatures this turn and only as a sorcery.
        """, null, null, null),
    )
    private val diary = Cards.inventFaces("Transform",
        face("Velmira's Diary", "{1}{B}", "Legendary Artifact — Book", """
            {T}, Sacrifice another artifact or creature: Draw a card. Activate only as a sorcery.
            {2}, {T}, Discard your hand: Transform Velmira's Diary.
        """, null, null, null),
        face("The Crypt of Velmira", null, "Legendary Land — Cave", """
            (Transforms from Velmira's Diary.)
            {T}: Add {B}.
            {T}: You may cast a creature spell from your graveyard this turn. If you do, it enters with a finality counter on it and is a Vampire in addition to its other types. (If a creature with a finality counter on it would die, exile it instead.)
        """, null, null, null),
    )
    private val gloom = Cards.inventFaces("Transform",
        face("Clutching Gloom", "{3}{B}", "Enchantment",
            "Whenever a creature you control attacks alone, it gains deathtouch and lifelink until end of turn. Put a dread counter on this enchantment. Then if there are three or more dread counters on it, transform it.",
            null, null, null),
        face("Gloom's Den", null, "Land — Cave", """
            (Transforms from Clutching Gloom.)
            {T}: Add {B}.
            {B}, {T}, Remove a dread counter from this land: You draw a card and you lose 1 life.
        """, null, null, null),
    )
    private val prick = Cards.invent("Ember Prick", "{R}", "Instant", "Ember Prick deals 1 damage to any target.")
    private val rebuke = Cards.invent("Final Rebuke", "{B}", "Instant", "Destroy target creature.")
    private val notion = Cards.invent("Fleeting Notion", "{U}", "Instant", "Draw a card.")
    private val pickets = Cards.invent("Muster the Pickets", "{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens. Create a Treasure token.")
    private val cache = Cards.invent("Glint Cache", "{W}", "Sorcery", "Create a Treasure token.")
    private val sapling = Cards.invent("Hollowroot Sapling", "{1}{G}", "Creature — Plant", "", "1/2")
    private val beetle = Cards.invent("Gravecrawl Beetle", "{1}{B}", "Creature — Insect", "", "2/1")

    private val dread = CounterKind.Named("dread")

    // ---- Zotlacai

    // CR 701.9a, 603.2 (three players): one opponent discards a land card (a Bat token), the other can't discard (a card drawn)
    @Test
    fun zotlacaiMakesABatForALandDiscardAndDrawsForAnOpponentWhoCant() = scenario(players = 3) {
        alice.battlefield(zotlacai)
        alice.library("Swamp", "Swamp")
        bob.hand("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(zotlacai)
        resolveAll()
        expect {
            bob.graveyard has "Forest"
            alice.battlefield has "Bat Token"
            "Bat Token" hasKeyword Keyword.Flying.kind
            alice.hand.size shouldBe 1
        }
    }

    // CR 712.14, 400.7: it dies and returns as a new object, tapped, its back face up
    @Test
    fun zotlacaiReturnsTappedAsItsLand() = scenario {
        alice.battlefield(zotlacai, "Swamp")
        alice.hand(rebuke)
        alice.casts(rebuke, target = zotlacai)
        resolveAll()
        expect {
            assertEquals("Sanctum of the Hollow", card(zotlacai).view.characteristics!!.name)
            assertTrue(zotlacai.tapped)
        }
    }

    // CR 602.5b: the land transforms only while some player (here alice, with one card) has one or fewer cards in hand
    @Test
    fun sanctumOfTheHollowTransformsOnlyWhileAPlayerHasASmallHand() = scenario {
        alice.battlefield(zotlacai, "Swamp", "Swamp", "Swamp")
        backFaceUp(zotlacai)
        alice.hand("Swamp", "Swamp")
        bob.hand("Forest", "Island")
        assertFailsWith<IllegalArgumentException> { alice.activates(zotlacai, ability = 1, paying = listOf("Swamp#0", "Swamp#1", "Swamp#2")) }
        alice.plays("Swamp")
        alice.activates(zotlacai, ability = 1, paying = listOf("Swamp#0", "Swamp#1", "Swamp#2"))
        resolveAll()
        expect { assertEquals("Zotlacai, Hollow Treachery", card(zotlacai).view.characteristics!!.name) }
    }

    // ---- Ojen Tavrix

    // CR 614.1a: 1 noncombat damage from a red source to an opponent becomes 4 (its power); to a creature it stays 1
    @Test
    fun tavrixRaisesSmallNoncombatDamageToAnOpponentToItsPower() = scenario {
        alice.battlefield(tavrix, "Mountain", "Mountain")
        alice.hand(prick, prick)
        bob.battlefield(sapling)
        alice.casts(prick, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
        alice.casts(prick, target = sapling)
        resolveAll()
        expect { sapling.view.obj?.damage shouldBe 1 }
    }

    // CR 614.1a: combat damage isn't changed; CR 120.1, 602.5b: the temple counts the noncombat damage red sources you controlled dealt
    @Test
    fun shrineOfFuryTransformsAfterFourNoncombatDamage() = scenario {
        alice.battlefield(tavrix, *Array(7) { "Mountain" })
        backFaceUp(tavrix)
        alice.hand(prick, prick, prick, prick)
        repeat(3) { i ->
            alice.casts(prick, target = "bob", paying = listOf("Mountain#$i"))
            resolveAll()
        }
        // three damage so far (no Tavrix on the battlefield to raise it)
        expect { bob.life shouldBe 17 }
        assertFailsWith<IllegalArgumentException> { alice.activates(tavrix, ability = 1, paying = listOf("Mountain#3", "Mountain#4", "Mountain#5")) }
        alice.casts(prick, target = "bob", paying = listOf("Mountain#3"))
        resolveAll()
        alice.activates(tavrix, ability = 1, paying = listOf("Mountain#4", "Mountain#5", "Mountain#6"))
        resolveAll()
        expect { assertEquals("Ojen Tavrix, Deepest Fury", card(tavrix).view.characteristics!!.name) }
    }

    // ---- Ojen Moskal

    // CR 510.2, 603.2: six combat damage reveals six cards; one creature card and one land card go onto the battlefield, the rest to the bottom
    @Test
    fun moskalPutsACreatureAndALandFromTheRevealedCards() = scenario {
        alice.battlefield(moskal)
        alice.library(sapling, "Forest", beetle, "Island", "Swamp", "Plains", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(moskal)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses(sapling)
        alice.chooses("Forest")
        resolveAll()
        expect {
            bob.life shouldBe 14
            alice.battlefield has sapling
            alice.battlefield has "Forest"
            alice.library.size shouldBe 5
            alice.library has beetle
        }
    }

    // ---- Ojen Quetzi

    // CR 702.88a: an instant cast from your hand gains rebound: exiled as it resolves, cast again at your next upkeep
    @Test
    fun quetziGivesAnInstantFromYourHandRebound() = scenario {
        alice.battlefield(quetzi, "Island")
        alice.hand(notion)
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.casts(notion)
        resolveAll()
        expect {
            alice.exile has notion
            alice.hand.size shouldBe 1
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has notion
            alice.hand.size shouldBe 2
        }
    }

    // CR 712.14, 122.6: it returns with three time counters; tapping the land for mana removes one (CR 602.5b: no transform while any remain)
    @Test
    fun quetziReturnsWithTimeCounters() = scenario {
        alice.battlefield(quetzi, "Swamp")
        alice.hand(rebuke)
        alice.casts(rebuke, target = quetzi)
        resolveAll()
        expect {
            assertEquals("Shrine of Turning Hours", card(quetzi).view.characteristics!!.name)
            quetzi.counters(CounterKind.Time) shouldBe 3
            assertTrue(quetzi.tapped)
        }
    }

    // ---- Ojen Tolqa

    // CR 614.1a: two Soldier tokens become six; the Treasure (not a creature token) stays one
    @Test
    fun tolqaTriplesCreatureTokensOnly() = scenario {
        alice.battlefield(tolqa, "Plains")
        alice.hand(pickets)
        alice.casts(pickets)
        resolveAll()
        expect {
            assertEquals(6, alice.battlefield.names.count { it == "Soldier Token" })
            assertEquals(1, alice.battlefield.names.count { it == "Treasure Token" })
        }
    }

    // CR 614.1a, 616.1f: a Treasure plus a Squirrel added by another replacement: only the Squirrel (a creature token) is tripled
    @Test
    fun tolqaTriplesOnlyTheCreatureTokensOfAMixedEvent() = scenario {
        alice.battlefield(tolqa, "Nutcracker, Squirrel Marshal", "Plains")
        alice.hand(cache)
        alice.casts(cache)
        resolveAll()
        expect {
            assertEquals(3, alice.battlefield.names.count { it == "Squirrel Token" })
            assertEquals(1, alice.battlefield.names.count { it == "Treasure Token" })
        }
    }

    // ---- The Crypt of Velmira

    // CR 601.2a, 400.7d, 613.1d: the creature cast from the graveyard enters with a finality counter and is also a Vampire
    @Test
    fun cryptCastsACreatureFromTheGraveyardAsAVampireWithFinality() = scenario {
        alice.battlefield(diary, "Swamp", "Swamp")
        backFaceUp(diary)
        alice.graveyard(beetle)
        alice.activates(diary, ability = 1)
        resolveAll()
        alice.casts(beetle, paying = listOf("Swamp", "Swamp#1"))
        resolveAll()
        expect {
            alice.battlefield has beetle
            beetle.counters(CounterKind.Finality) shouldBe 1
            assertTrue(Subtype("Vampire") in beetle.subtypes)
            assertTrue(Subtype("Insect") in beetle.subtypes)
        }
    }

    // ---- Clutching Gloom

    // CR 506.5, 701.27a: the creature attacking alone gains deathtouch and lifelink; the third dread counter transforms the enchantment, not
    // the creature
    @Test
    fun clutchingGloomTransformsItselfOnTheThirdCounter() = scenario {
        alice.battlefield(beetle)
        alice.battlefield(gloom, counters = listOf(dread to 2))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(beetle)
        resolveAll()
        expect {
            beetle hasKeyword Keyword.Deathtouch.kind
            beetle hasKeyword Keyword.Lifelink.kind
            assertEquals("Gloom's Den", card(gloom).view.characteristics!!.name)
            gloom.counters(dread) shouldBe 3
        }
    }
}
