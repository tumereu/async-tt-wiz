package arcanum.engine.sets.mom

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MOM chunk 02: transforming double-faced cards and Sieges, part 2: a reflexive trigger sized by the cards revealed, a Dragon's own attack
 * damage, a creature that is also a land, a Praetor's hand-size and spells-cast activation restrictions, a Saga's draw, unlimited hand,
 * free casts from hand and casts from any graveyard, damage to an opponent and only their creatures with three players, counting
 * transformed permanents (a token copy of one too), a trigger for each blocker, an opponent's random card exiled and played by you and an
 * attacking copy token with counters sacrificed at the end step. */
class MomChunk02TransformScenariosTest {
    private val drakmoor = Cards.inventFaces("Transform",
        OracleCard("Invasion of Drakmoor", "{1}{R}", "Battle — Siege",
            "When this Siege enters, reveal any number of Dragon cards from your hand. When you do, this Siege deals X plus 2 damage to any other target, where X is the number of cards revealed this way. (X can be 0.)",
            defense = "5"),
        OracleCard("Unbowed Stormwing", null, "Creature — Dragon", "Flying, trample\nWhenever a Dragon you control attacks, it deals 2 damage to any target.", "4", "4",
            colorIndicator = "R"))
    // the back faces of Sieges, alone (a Siege's back face is reached by defeating it, CR 310.12b)
    private val stormwing = Cards.invent("Momentide Stormwing", "{3}{R}{R}", "Creature — Dragon", """
        Flying, trample
        Whenever a Dragon you control attacks, it deals 2 damage to any target.
    """.trimIndent(), "4/4")
    private val cliffwaker = Cards.invent("Momentide Cliffwaker", "{3}{G}", "Creature — Elemental", """
        Vigilance, haste
        As long as this creature is on the battlefield, it's a land in addition to its other types.
        {T}: Add one mana of any color.
    """.trimIndent(), "4/4")
    private val velQuorias = Cards.inventFaces("Transform",
        face("Vel-Quorias", "{3}{U}{U}", "Legendary Creature — Phyrexian Praetor", """
            Ward {2}
            Whenever you cast a noncreature spell with mana value 3 or greater, draw a card.
            {3}{U}: Exile Vel-Quorias, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery and only if you have seven or more cards in hand.
        """.trimIndent(), "5/5", null, null),
        face("The Grand Distillation", null, "Enchantment — Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Draw cards equal to the number of cards in your hand. You have no maximum hand size for as long as you control this Saga.
            II — Return all non-Phyrexian creatures to their owners' hands.
            III — You may cast any number of spells from your hand without paying their mana costs. Exile this Saga, then return it to the battlefield (front face up).
        """.trimIndent(), null, null, "U"))
    private val kharavex = Cards.inventFaces("Transform",
        face("Kharavex", "{2}{R}{R}", "Legendary Creature — Phyrexian Praetor", """
            First strike
            Whenever you cast an instant or sorcery spell, Kharavex deals 1 damage to target opponent. Add {R}.
            {R}: Exile Kharavex, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery and only if you've cast three or more instant and/or sorcery spells this turn.
        """.trimIndent(), "4/4", null, null),
        face("The Grand Labor", null, "Enchantment — Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — This Saga deals 3 damage to target opponent and each creature they control.
            II — Create three Treasure tokens.
            III — Until end of turn, you may cast instant and sorcery spells from any graveyard. If a spell cast this way would be put into a graveyard, exile it instead. Exile this Saga, then return it to the battlefield (front face up).
        """.trimIndent(), null, null, "R"))
    private val appraiser = Cards.invent("Mutant Appraiser", "{1}{G}{U}", "Creature — Vedalken Mutant", """
        Flying, vigilance
        This creature gets +1/+0 for each transformed permanent you control.
    """.trimIndent(), "0/5")
    private val drakeling = Cards.invent("Lensborn Drakeling", "{3}{U}", "Creature — Phyrexian Dragon", """
        Flying
        As long as you control a transformed permanent, this creature has "When this creature dies, draw a card."
    """.trimIndent(), "3/2")
    private val grazer = Cards.inventFaces("Transform",
        face("Yoked Grazer", "{4}{G}", "Creature — Beast", "{4}{R/P}: Transform this creature. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)", "4/5", null, null),
        face("Armored Kilngrazer", null, "Creature — Phyrexian Beast", "Menace (This creature can't be blocked except by two or more creatures.)", "7/5", null, "GR"))
    private val tinker = Cards.inventFaces("Transform",
        face("Hurried Tinker", "{2}{R}", "Creature — Human Artificer", "Haste\n{3}{W/P}: Transform this creature. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)", "2/3", null, null),
        face("Oilwing Skinner", null, "Creature — Phyrexian Artificer", "Flying, haste", "3/4", null, "RW"))
    private val glass = Cards.inventFaces("Transform",
        face("Order of the Glass", "{1}{U}", "Creature — Human Knight", "{3}{W/P}: Transform this creature. Activate only as a sorcery. ({W/P} can be paid with either {W} or 2 life.)", "2/1", null, null),
        face("Order of the Chalk Legion", null, "Creature — Phyrexian Knight", "Whenever this creature becomes blocked by a creature, the blocking creature gets -1/-1 until end of turn.", "3/3", null, "WU"))
    private val maeve = Cards.inventFaces("Transform",
        face("Maeve, Herald of Ruin", "{1}{U}", "Legendary Creature — Human Wizard", """
            Whenever you cast a legendary spell, untap Maeve.
            {T}: Draw a card, then discard a card.
            {5}{B/P}: Transform Maeve. Activate only as a sorcery. ({B/P} can be paid with either {B} or 2 life.)
        """.trimIndent(), "1/3", null, null),
        face("Maeve, Academy Ravager", null, "Legendary Creature — Phyrexian Wizard", """
            Trample
            Whenever a source deals damage to Maeve, that source's controller exiles a card from their hand at random. If it's a land card, you may put it onto the battlefield under your control. Otherwise, you may cast it without paying its mana cost.
        """.trimIndent(), "5/5", null, "UB"))
    private val warden = Cards.inventFaces("Transform",
        face("Dawn-Kissed Warden", "{1}{W}", "Creature — Human Cleric", "{5}{R/P}: Transform this creature. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)", "2/2", null, null),
        face("Forge-Kissed Victor", null, "Creature — Phyrexian Cleric",
            "Whenever this creature attacks, create a tapped and attacking token that's a copy of it. Put a +1/+1 counter on that token for each +1/+1 counter on this creature. Sacrifice that token at the beginning of the next end step.",
            "3/3", null, "WR"))

    private val wyrmling = Cards.invent("Momentide Wyrmling", "{1}{R}", "Creature — Dragon", "", "1/1")
    private val lifewyrm = Cards.invent("Momentide Lifewyrm", "{2}{W}", "Creature — Dragon", "Lifelink", "2/2")
    private val grizzly = Cards.invent("Momentide Grizzly", "{1}{G}", "Creature — Bear", "", "2/2")
    private val footman = Cards.invent("Momentide Footman", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val brute = Cards.invent("Momentide Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val bolt = Cards.invent("Momentide Bolt", "{R}", "Instant", "Momentide Bolt deals 3 damage to any target.")
    private val quake = Cards.invent("Momentide Quake", "{2}{R}", "Instant", "Destroy target land.")
    private val spark = Cards.invent("Momentide Spark", "{R}", "Instant", "Draw a card.")
    private val flare = Cards.invent("Momentide Flare", "{R}", "Instant", "Draw a card.")
    private val glimpse = Cards.invent("Momentide Glimpse", "{R}", "Sorcery", "Draw a card.")

    private fun Scenario.libraries(count: Int = 20) = players.forEach { p -> p.library(*Array(count) { "Island" }) }
    private fun Scenario.faceName(name: String): String? = view()[card(name, zone = Zone.Battlefield)]?.characteristics?.name

    // CR 603.12: revealing two Dragons makes the reflexive trigger deal 2 plus 2; CR 608.2h: X counted as it resolves; the cards stay in hand
    @Test
    fun drakmoorDealsTwoPlusTheDragonsRevealed() = scenario {
        alice.hand(drakmoor, wyrmling, lifewyrm, brute)
        alice.battlefield("Mountain", "Mountain")
        libraries()
        alice.casts(drakmoor)
        resolveAll()
        alice.chooses(wyrmling, lifewyrm)
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.hand has wyrmling
            alice.hand has lifewyrm
        }
    }

    // CR 603.12: "X can be 0": revealing nothing still triggers it, for 2
    @Test
    fun drakmoorRevealingNothingStillDealsTwo() = scenario {
        alice.hand(drakmoor, wyrmling)
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(brute)
        libraries()
        alice.casts(drakmoor)
        resolveAll()
        alice.chooses()
        answer(Answer.Targets(listOf(target(brute))))
        resolveAll()
        expect { brute.view.obj?.damage shouldBe 2 }
    }

    // CR 508.1, 702.15b: the attacking Dragon deals the damage, so its lifelink gains life
    @Test
    fun stormwingMakesTheAttackingDragonDealTheDamage() = scenario {
        alice.battlefield(stormwing, lifewyrm)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lifewyrm)
        resolveAll()
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 205.1b, 613.1d: a land in addition to its other types on the battlefield, so a land spell can destroy it; in the hand it is only a creature card
    @Test
    fun cliffwakerIsALandOnlyOnTheBattlefield() = scenario {
        alice.battlefield(cliffwaker)
        alice.hand(cliffwaker)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(quake)
        libraries()
        expect {
            (CardType.Land in cliffwaker.types) shouldBe true
            (CardType.Creature in cliffwaker.types) shouldBe true
            (CardType.Land in card(cliffwaker, 1).types) shouldBe false
        }
        alice.passes()
        bob.casts(quake, target = cliffwaker)
        resolveAll()
        expect { alice.graveyard has cliffwaker }
    }

    // CR 602.5b: "only if you have seven or more cards in hand": not with six
    @Test
    fun velQuoriasCantTransformWithSixCards() = scenario {
        alice.battlefield(velQuorias, "Island", "Island", "Island", "Island")
        alice.hand(spark, flare, glimpse, bolt, quake, brute)
        libraries()
        assertFailsWith<IllegalArgumentException> { alice.activates(velQuorias) }
    }

    // CR 714.3a: it returns as a Saga with a lore counter, chapter I draws as many cards as are in hand; CR 402.2, 514.1: no discard to hand size
    // while you control the Saga
    @Test
    fun velQuoriasTransformsDrawsAndKeepsAFullHand() = scenario {
        alice.battlefield(velQuorias, "Island", "Island", "Island", "Island")
        alice.hand(spark, flare, glimpse, bolt, quake, brute, grizzly)
        libraries()
        alice.activates(velQuorias)
        resolveAll()
        assertEquals("The Grand Distillation", faceName(velQuorias))
        expect { alice.hand.size shouldBe 14 }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { alice.hand.size shouldBe 14 }
    }

    // CR 608.2g: the chosen spells are cast for free while chapter III resolves; CR 305.9: the land stays; CR 712.14: it returns front face up
    @Test
    fun theGrandDistillationCastsFromHandForFree() = scenario {
        alice.battlefield(velQuorias, counters = listOf(CounterKind.Lore to 2))
        backFaceUp(velQuorias)
        alice.hand(brute, bolt, "Forest")
        bob.battlefield(grizzly)
        libraries()
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        alice.chooses(brute, bolt)
        answer(Answer.Targets(listOf(target(grizzly))))
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            bob.graveyard has grizzly
            alice.hand has "Forest"
        }
        assertEquals("Vel-Quorias", faceName(velQuorias))
    }

    // CR 602.5b: three instant and/or sorcery spells cast this turn allow it; CR 714.3a: chapter I on entering deals 3 to the opponent and their
    // creatures (CR 120.2)
    @Test
    fun kharavexTransformsAfterThreeSpells() = scenario {
        alice.battlefield(kharavex, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(spark, flare, glimpse)
        bob.battlefield(grizzly)
        libraries()
        alice.casts(spark)
        resolveAll()
        alice.casts(flare)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(kharavex) }
        alice.casts(glimpse)
        resolveAll()
        alice.activates(kharavex)
        resolveAll()
        assertEquals("The Grand Labor", faceName(kharavex))
        expect {
            bob.life shouldBe 14
            bob.graveyard has grizzly
        }
    }

    // CR 120.2: chapter I deals 3 to the target opponent and each creature they control, not another opponent's (three players)
    @Test
    fun theGrandLaborHitsOnlyTheTargetOpponentsCreatures() = scenario(players = 3) {
        alice.battlefield(kharavex, counters = listOf(CounterKind.Lore to 0))
        backFaceUp(kharavex)
        bob.battlefield(grizzly)
        carol.battlefield(brute)
        libraries()
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 20
            bob.graveyard has grizzly
            brute.view.obj?.damage shouldBe 0
        }
    }

    // CR 611.2c: the permission covers an opponent's graveyard and a card put into a graveyard later in the turn; CR 614.1a: each spell cast
    // this way is exiled; a spell cast from hand goes to the graveyard as usual
    @Test
    fun theGrandLaborCastsFromAnyGraveyardAndExilesThem() = scenario {
        alice.battlefield(kharavex, counters = listOf(CounterKind.Lore to 2))
        backFaceUp(kharavex)
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(flare)
        bob.graveyard(bolt)
        libraries()
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        assertEquals("Kharavex", faceName(kharavex))
        alice.casts(bolt, target = "bob")
        resolveAll()
        expect { bob.exile has bolt }
        alice.casts(flare)
        resolveAll()
        expect { alice.graveyard has flare }
        alice.casts(flare)
        resolveAll()
        expect {
            alice.exile has flare
            bob.life shouldBe 14
        }
    }

    // CR 701.27g: only a permanent with its back face up counts; CR 613.1f: the dies trigger is granted while you control one (CR 603.10a)
    @Test
    fun transformedPermanentsPumpTheAppraiserAndArmTheDrakeling() = scenario {
        alice.battlefield(appraiser, drakeling, grazer, tinker)
        backFaceUp(grazer)
        bob.battlefield("Mountain")
        bob.hand(bolt)
        libraries()
        expect { appraiser.power shouldBe 1 }
        alice.passes()
        bob.casts(bolt, target = drakeling)
        resolveAll()
        expect {
            alice.graveyard has drakeling
            alice.hand.size shouldBe 1
        }
    }

    // CR 701.27g: without a transformed permanent the Drakeling has no dies trigger
    @Test
    fun theDrakelingDrawsNothingWithoutATransformedPermanent() = scenario {
        alice.battlefield(appraiser, drakeling, tinker)
        bob.battlefield("Mountain")
        bob.hand(bolt)
        libraries()
        expect { appraiser.power shouldBe 0 }
        alice.passes()
        bob.casts(bolt, target = drakeling)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 509.3d: it triggers once for each blocker, and each blocker gets -1/-1
    @Test
    fun theChalkLegionShrinksEachBlocker() = scenario {
        alice.battlefield(glass)
        backFaceUp(glass)
        bob.battlefield(grizzly, footman)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glass)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(grizzly to glass, footman to glass)
        resolveAll()
        expect {
            grizzly.power shouldBe 1
            grizzly.toughness shouldBe 1
            footman.power shouldBe 1
            footman.toughness shouldBe 1
        }
    }

    // CR 120.3: the source's controller (another opponent, three players) exiles a card at random; you may cast it for free (CR 608.2g)
    @Test
    fun maeveCastsTheCardTheSourcesControllerExiles() = scenario(players = 3) {
        alice.battlefield(maeve)
        backFaceUp(maeve)
        bob.hand(grizzly)
        carol.battlefield("Mountain")
        carol.hand(brute, bolt)
        libraries()
        turn(priority = carol)
        carol.casts(bolt, target = maeve)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            brute.controller shouldBe alice.id
            bob.hand has grizzly
        }
    }

    // a land card exiled this way may be put onto the battlefield under your control
    @Test
    fun maeveTakesAnExiledLand() = scenario {
        alice.battlefield(maeve)
        backFaceUp(maeve)
        bob.battlefield("Mountain")
        bob.hand(bolt, "Forest")
        libraries()
        turn(priority = bob)
        bob.casts(bolt, target = maeve)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            "Forest".zone shouldBe Zone.Battlefield
            "Forest".controller shouldBe alice.id
        }
    }

    // CR 508.4: the copy enters tapped and attacking; CR 707.8a: a copy of the transformed permanent enters back face up (a transformed
    // permanent too); it gets a counter for each of the Victor's; CR 603.7: sacrificed at the next end step
    @Test
    fun theVictorsAttackingCopyHitsAndIsSacrificed() = scenario {
        alice.battlefield(warden, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        backFaceUp(warden)
        alice.battlefield(appraiser)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(warden)
        resolveAll()
        expect {
            alice.battlefield.size shouldBe 3
            appraiser.power shouldBe 2
        }
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.life shouldBe 10 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.size shouldBe 2 }
    }
}
