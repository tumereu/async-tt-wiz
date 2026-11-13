package arcanum.engine.sets.lci

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Decision
import arcanum.engine.state.TargetRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.payIfAsked
import arcanum.engine.sets.dgm.targets
import arcanum.engine.sets.blb.chooseNone
import arcanum.engine.sets.fin.backFaceUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** LCI chunk 07: fathomless descent, mill and scry played: an activation counting permanent types (not every card type) in the graveyard,
 * mana and a target's mana value limited by the permanent cards there, a mill then an untargeted return, an enters trigger counting the
 * creatures that left this turn, a land that animates and mills on attack, a third landmark counter that transforms an artifact, abilities
 * for three counters of any kinds and an additional cost of one of two kinds. Invented names carry the oracle text. */
class LciChunk07FathomlessMillScryScenariosTest {
    private val ixtalli = Cards.inventFaces("Transform",
        face("Ixtalli, the Deep Gate", "{3}", "Legendary Artifact", """
            {T}: Draw a card, then discard a card.
            {4}, {T}: Transform Ixtalli. Activate only if there are four or more permanent types among cards in your graveyard. (Artifact, battle, creature, enchantment, land, and planeswalker are permanent types.)
        """, null, null, null),
        face("The Hollow Heart", null, "Legendary Land", """
            (Transforms from Ixtalli.)
            Fathomless descent — {T}: Add X mana of any one color, where X is the number of permanent cards in your graveyard.
        """, null, null, null),
    )
    private val resurgence = Cards.invent("Wriggling Resurgence", "{1}{B}{G}", "Sorcery",
        "Fathomless descent — Return to the battlefield target nonland permanent card in your graveyard with mana value less than or equal to the number of permanent cards in your graveyard.")
    private val breath = Cards.invent("Second Breath", "{2}{B}", "Instant",
        "You may mill two cards. Then return up to two creature cards from your graveyard to your hand. (To mill two cards, put the top two cards of your library into your graveyard.)")
    private val reef = Cards.invent("Brinehollow Reef", null, "Land", """
        This land enters tapped.
        {T}: Add {U} or {B}.
        {2}{U}{B}: Until end of turn, this land becomes a 4/4 blue and black Shark creature with deathtouch. It's still a land.
        Whenever this land attacks, target player mills four cards.
    """)
    private val outrider = Cards.invent("Tazca's Outrider", "{2}{W}", "Creature — Cat Warrior", """
        Flash
        When this creature enters, choose one —
        • Put a +1/+1 counter on this creature for each creature that left the battlefield under your control this turn.
        • You gain 2 life and scry 2.
        • Exile target player's graveyard.
    """, "3/1")
    private val chart = Cards.inventFaces("Transform",
        face("Explorer's Chart", "{2}", "Artifact", """
            {1}, {T}: Scry 1. Put a landmark counter on this artifact. Then if there are three or more landmark counters on it, remove those counters, transform this artifact, and create three Treasure tokens. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
        """, null, null, null),
        face("Hidden Hoard Cove", null, "Land", """
            (Transforms from Explorer's Chart.)
            {T}: Add {C}.
            {T}, Sacrifice a Treasure: Draw a card.
        """, null, null, null),
    )
    private val sentinel = Cards.invent("Sentinel of the Upper Vault", "{W}", "Creature — Human Soldier", """
        As long as this creature has three or more counters on it, it has flying and vigilance.
        Tap three untapped artifacts and/or creatures you control: Put a +1/+1 counter on this creature. Scry 1. Activate only as a sorcery.
    """, "1/2")
    private val remnants = Cards.invent("Remnants of the Fallen", "{1}{B}", "Creature — Spirit", """
        As an additional cost to cast this spell, discard a card or sacrifice a permanent.
        Fathomless descent — Remnants of the Fallen's power is equal to the number of permanent cards in your graveyard and its toughness is equal to that number plus 1.
    """, "*/1+*")
    private val sentry = Cards.invent("Cogwork Sentry", "{3}", "Artifact Creature — Construct", "", "2/2")
    private val ward = Cards.invent("Glimmering Ward", "{1}{W}", "Enchantment", "")
    private val rite = Cards.invent("Kindred Lurker Rite", "{1}{G}", "Kindred Instant — Elf", "Draw a card.")
    private val delve = Cards.invent("Sudden Delve", "{U}", "Sorcery", "Draw a card.")
    private val shrew = Cards.invent("Tomb Shrew", "{B}", "Creature — Shrew", "", "1/1")
    private val grazer = Cards.invent("Mossback Grazer", "{4}{G}{G}", "Creature — Beast", "", "6/6")
    private val spit = Cards.invent("Cinder Spit", "{R}", "Sorcery", "Cinder Spit deals 2 damage to each creature.")

    // CR 110.4a, 602.5b: an artifact creature, a land, a kindred instant and a sorcery are six card types but three permanent types; an
    // enchantment makes four
    @Test
    fun ixtalliTransformsOnlyWithFourPermanentTypes() = scenario {
        alice.battlefield(ixtalli, "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(sentry, "Plains", rite, delve)
        alice.hand(ward)
        assertFailsWith<IllegalArgumentException> { alice.activates(ixtalli, ability = 1) }
        move(ward, Zone.Graveyard)
        alice.activates(ixtalli, ability = 1)
        resolveAll()
        expect { assertEquals("The Hollow Heart", card(ixtalli).view.characteristics!!.name) }
    }

    // CR 106.1b, 107.3c: X is the permanent cards in your graveyard (the artifact creature, the land and the enchantment, not the instant)
    @Test
    fun hollowHeartAddsManaPerPermanentCard() = scenario {
        alice.battlefield(ixtalli)
        backFaceUp(ixtalli)
        alice.graveyard(sentry, "Plains", rite, ward)
        alice.activates(ixtalli, color = Color.Black)
        expect { alice.pool.size shouldBe 3 }
    }

    // CR 115.1, 608.2b: a 3-mana-value card with three permanent cards in the graveyard (itself counted) is a legal target; a 6 isn't
    @Test
    fun resurgenceReturnsACardNoCostlierThanTheCount() = scenario {
        alice.battlefield("Swamp", "Forest", "Plains")
        alice.graveyard(sentry, grazer, "Plains")
        alice.hand(resurgence)
        alice.casts(resurgence)
        assertEquals(listOf<TargetRef>(TargetRef.Object(card(sentry))), decision<Decision.Targets>().options)
        targets(sentry)
        payIfAsked()
        resolveAll()
        expect {
            alice.battlefield has sentry
            alice.graveyard has grazer
        }
    }

    // CR 701.17a: the milled cards can be returned; CR 115.10a: the creature cards are chosen as it resolves (up to two)
    @Test
    fun secondBreathMillsThenReturnsUpToTwoCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.library(shrew, "Island", "Plains")
        alice.graveyard(sentry, grazer)
        alice.hand(breath)
        alice.casts(breath)
        resolveAll()
        may(true)
        assertEquals(2, decision<Decision.ChooseObjects>().max)
        alice.chooses(shrew, grazer)
        resolveAll()
        expect {
            alice.hand has shrew
            alice.hand has grazer
            alice.graveyard has sentry
            alice.graveyard has "Island"
            alice.library.size shouldBe 1
        }
    }

    // CR 400.7, 603.10a: the two creatures alice controlled that died this turn count; bob's doesn't
    @Test
    fun outriderCountsCreaturesThatLeftUnderYourControl() = scenario {
        alice.battlefield(shrew, sentry, "Mountain", "Plains", "Plains", "Plains")
        alice.hand(spit, outrider)
        bob.battlefield("Plains")
        bob.graveyard("Island")
        alice.casts(spit)
        resolveAll()
        expect { alice.graveyard has sentry }
        alice.casts(outrider)
        resolveAll()
        modes(0)
        resolveAll()
        expect {
            outrider.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            outrider.power shouldBe 5
        }
    }

    // CR 613.1d, 613.1e, 613.4b: a 4/4 blue and black Shark land with deathtouch; CR 508.1m, 115.1: its attack trigger targets any player
    @Test
    fun reefAnimatesAndMillsTheTargetPlayer() = scenario(players = 3) {
        alice.battlefield(reef, "Island", "Swamp", "Island", "Swamp")
        bob.library("Plains", "Plains", "Plains", "Plains", "Plains")
        carol.library("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(reef, ability = 1)
        resolveAll()
        expect {
            reef.power shouldBe 4
            reef.colors.toSet() shouldBe setOf(Color.Blue, Color.Black)
            reef hasKeyword Keyword.Deathtouch.kind
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(reef, defender = "bob")
        resolveAll()
        targets("carol")
        resolveAll()
        expect {
            carol.graveyard.size shouldBe 4
            carol.library.size shouldBe 1
            bob.graveyard.size shouldBe 0
        }
    }

    // CR 122.1, 712.3: the third landmark counter: they are removed, it transforms and three Treasures are created
    @Test
    fun chartTransformsOnTheThirdLandmark() = scenario {
        alice.battlefield(chart, counters = listOf(CounterKind.Named("landmark") to 2))
        alice.battlefield("Plains")
        alice.library("Island")
        alice.activates(chart)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            assertEquals("Hidden Hoard Cove", card(chart).view.characteristics!!.name)
            card(chart).counters(CounterKind.Named("landmark")) shouldBe 0
            alice.battlefield.names.count { it == "Treasure Token" } shouldBe 3
        }
    }

    // CR 122.1: two +1/+1 counters and an oil counter are three counters; CR 302.6: a summoning-sick creature can be tapped for a cost
    // without the tap symbol
    @Test
    fun sentinelFliesWithThreeCountersOfAnyKinds() = scenario {
        alice.battlefield(sentinel, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 1))
        alice.battlefield(sentry, ward)
        alice.battlefield(shrew, grazer, sick = true)
        alice.library("Island")
        expect { sentinel lacksKeyword Keyword.Flying.kind }
        alice.activates(sentinel, choosing = listOf(sentry, shrew, grazer))
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            sentinel hasKeyword Keyword.Flying.kind
            sentinel hasKeyword Keyword.Vigilance.kind
            shrew.tapped shouldBe true
        }
    }

    // CR 601.2f, 601.2h: the sacrificed permanent is paid as it is cast; CR 604.3: */1+* is the permanent cards in your graveyard (the
    // sacrificed artifact creature and a land: 2/3)
    @Test
    fun remnantsSacrificesAPermanentAndCountsPermanentCards() = scenario {
        alice.battlefield(sentry, "Swamp", "Swamp")
        alice.graveyard("Plains", delve)
        alice.hand(remnants, shrew)
        alice.casts(remnants, option = "Sacrifice", choosing = listOf(sentry))
        resolveAll()
        expect {
            alice.graveyard has sentry
            alice.hand has shrew
            remnants.power shouldBe 2
            remnants.toughness shouldBe 3
        }
    }
}
