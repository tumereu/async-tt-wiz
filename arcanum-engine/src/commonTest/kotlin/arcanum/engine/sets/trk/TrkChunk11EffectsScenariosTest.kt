package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Star Trek chunk 11 in play: cards shuffled into their owners' libraries, sharing a card type among exiled cards, a hasty token copy sacrificed
 * at the end step, an exiled card's copy ability, not losing at 0 life, each opponent losing the life they lost this turn, face-down exiles each
 * player may play, playing from outside the game, a control change per opponent, a return-to-hand condition, lands from looked-at or revealed
 * cards, and searches by its last controller, by name and with charge counters. */
class TrkChunk11EffectsScenariosTest {
    private val tinkerers = Cards.invent("Those Elder Tinkerers", "{2}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw
        step, add a lore counter. Sacrifice after III.)
        I — Draw two cards, then discard two cards.
        II — Return up to one target artifact, instant, or sorcery card from your graveyard to your hand.
        III — Choose up to two target cards in graveyards. The owners of those cards shuffle them into their libraries.
    """.trimIndent())
    private val ending = Cards.invent("Every Fine Ending...", "{3}{G}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III — Draw two cards, then exile a card from your hand.
        IV — If all cards exiled with this Saga share a card type, you gain 10 life.
    """.trimIndent())
    private val leechjelly = Cards.invent("Synaptic Leechjelly", "{3}{B}", "Creature — Jellyfish Leech", """
        Flying
        When this creature enters, exile target creature card with mana value 3 or less from your graveyard. Create a token that's a copy of it. That token gains haste. Sacrifice it at the beginning of the next end step.
        Whenever one or more cards leave your graveyard, each opponent loses 1 life and you gain 1 life.
    """.trimIndent(), "2/2")
    private val zed = Cards.invent("Zed, Shuffling the Odds", "{2}{R}{R}", "Legendary Creature — Q",
        """When Zed enters, shuffle your library, then exile cards from the top of your library until you exile a nonland card. That nonland card gains "Exile a card from your hand: Create a copy of this card. You may cast the copy. Activate only during your turn and only if this card is exiled."""",
        "4/4")
    private val varlessa = Cards.invent("Varlessa, Mirror Empress", "{4}{B}{B}", "Legendary Creature — Human Rogue Noble", """
        You don't lose the game for having 0 or less life.
        At the beginning of your end step, you lose 3 life and draw a card. Then if your life total is 0 or less, create ten 1/1 red Officer creature tokens.
    """.trimIndent(), "4/6")
    private val varek = Cards.invent("Gul Varek, Occupation Prefect", "{4}{B}{B}{B}", "Legendary Creature — Cardassian Rogue", """
        Menace
        At the beginning of each end step, each opponent loses life equal to the life that player lost this turn. You gain life equal to the life lost this way. (Damage causes loss of life.)
    """.trimIndent(), "6/6")
    private val mindLink = Cards.invent("Psionic Mind-Link", "{U}", "Sorcery",
        "Target opponent looks at the top card of your library, then exiles it face down. You look at the top two cards of their library, then exile them face down. For each card exiled this way, the player who exiled it may play it for as long as it remains exiled. Mana of any type can be spent to cast spells this way.")
    private val simDeck = Cards.invent("The Simulation Deck", "{7}", "Legendary Artifact",
        "Once during each of your turns, you may play a permanent card you own from outside the game or from your graveyard.")
    private val korvath = Cards.invent("Korvath, Hired Spy", "{2}{R}", "Legendary Creature — Klingon Warrior Spy",
        "When Korvath enters, for each opponent, gain control of up to one target creature that player controls with mana value 3 or less until end of turn. Untap those creatures. Until end of turn, they become Warriors in addition to their other types and gain haste.",
        "3/2")
    private val tamsin = Cards.invent("Tamsin Rourke, Relay Chief", "{2}{W}", "Legendary Creature — Human Artificer", """
        When Tamsin Rourke enters, return up to one other target creature or artifact you control to its owner's hand.
        At the beginning of each end step, if a permanent was returned to your hand from the battlefield this turn, draw a card.
    """.trimIndent(), "3/3")
    private val punch = Cards.invent("Punch It Now!", "{1}{R}{G}", "Enchantment", """
        Creatures you control have haste.
        When this enchantment enters, look at the top five cards of your library. Put a land card from among them onto the battlefield tapped and the rest into your graveyard.
    """.trimIndent())
    private val jorin = Cards.invent("Jorin Vask, Away Team Lead", "{3}{R}", "Legendary Creature — Human Officer",
        "At the beginning of combat on your turn, reveal cards from the top of your library until you reveal two land cards. Put those land cards onto the battlefield and the rest on the bottom of your library in a random order. Sacrifice those lands at the beginning of your next end step.",
        "4/3")
    private val parsecs = Cards.invent("Ninety Parsecs from Port", "{1}{W}", "Sorcery",
        "Exile target creature or Spacecraft. Its controller may search their library for a basic land card, reveal it, put it into their hand, then shuffle.")
    private val drones = Cards.invent("Galactic Replicant Drones", "{2}{W}", "Artifact Creature — Android",
        "When this creature enters, you may search your library for any number of cards named Galactic Replicant Drones, reveal them, put them into your hand, then shuffle.",
        "3/1")
    private val orrin = Cards.invent("Captain Orrin Delacroix", "{2}{W}", "Legendary Creature — Human Officer", """
        Other creatures you control get +1/+1.
        Whenever you cast a creature or Spacecraft spell, create a 1/1 red Officer creature token.
        Tap seven untapped Officers you control: Search your library for a Spacecraft card, put it onto the battlefield with seven charge counters on it, then shuffle.
    """.trimIndent(), "2/3")

    private val cub = Cards.invent("Trk11 Moss Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk11 Plains Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bigOx = Cards.invent("Trk11 Great Ox", "{4}{G}", "Creature — Ox", "", "5/5")
    private val drain = Cards.invent("Trk11 Life Drain", "{B}", "Instant", "Target player loses 3 life.")
    private val sip = Cards.invent("Trk11 Life Sip", "{B}", "Instant", "Target player loses 1 life.")
    private val jolt = Cards.invent("Trk11 Jolt of Cheer", "{R}", "Instant", "You gain 2 life.")
    private val quip = Cards.invent("Trk11 Quip of Cheer", "{U}", "Instant", "You gain 2 life.")
    private val hymn = Cards.invent("Trk11 Hymn of Cheer", "{G}", "Instant", "You gain 2 life.")
    private val ensign = Cards.invent("Trk11 Deck Ensign", "{W}", "Creature — Human Officer", "", "1/1")
    private val shuttle = Cards.invent("Trk11 Survey Shuttle", "{3}", "Artifact — Spacecraft", "")

    private fun Scenario.castOptions(player: ScenarioPlayer) = Engine.options(state, player.id).filterIsInstance<Input.Cast>().map { it.card }

    // ---- Sagas

    // CR 400.3, 701.24a: chapter III puts each chosen card into its owner's library, from two opponents' graveyards
    @Test
    fun tinkerersShuffleCardsIntoTheirOwnersLibraries() = scenario(players = 3) {
        bob.graveyard(cub)
        carol.graveyard(ox)
        carol.library("Forest")
        loreChapter(tinkerers, 3, "Island", "Island")
        resolveAll()
        targets(cub, ox)
        resolveAll()
        expect {
            bob.library has cub
            carol.library has ox
            bob.graveyard lacks cub
            carol.graveyard lacks ox
        }
    }

    // CR 607.2a: the lands exiled by chapters II and III share a card type, so chapter IV gains 10 life
    @Test
    fun everyFineEndingGainsWhenTheExiledCardsShareAType() = scenario {
        loreChapter(ending, 2, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        resolveAll()
        alice.chooses("Island")
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        resolveAll()
        alice.chooses("Island")
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        resolveAll()
        expect {
            alice.exile.size shouldBe 2
            alice.life shouldBe 30
        }
    }

    // CR 607.2a: a land and a creature share no card type: no life
    @Test
    fun everyFineEndingGainsNothingWithoutASharedType() = scenario {
        alice.hand(cub)
        loreChapter(ending, 2, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        resolveAll()
        alice.chooses("Island")
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        resolveAll()
        alice.chooses(cub)
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        resolveAll()
        expect {
            alice.exile has cub
            alice.life shouldBe 20
        }
    }

    // ---- Copies and granted abilities

    // CR 707.2, 611.2a, 603.7: the token copy has haste and is sacrificed at the next end step; the card leaving the graveyard drains each
    // opponent (CR 603.2c)
    @Test
    fun leechjellysHastyCopyIsSacrificedAtTheEndStep() = scenario(players = 3) {
        alice.graveyard(cub)
        alice.hand(leechjelly)
        alice.castsWithoutCosts(leechjelly)
        resolveAll()
        resolveAll()
        val copy = state.battlefield.single { state.obj(it)!!.token }
        assertTrue(view()[copy]!!.characteristics!!.hasKeyword(Keyword.Haste.kind))
        expect {
            alice.exile has cub
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 21
        }
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(state.battlefield.none { state.obj(it)!!.token })
        expect { alice.battlefield has leechjelly }
    }

    // CR 113.6m, 602.5b, 707.12: the exiled card's ability works in exile on its owner's turn; the copy is cast paying its costs and resolves as
    // a token (CR 111.1); the card stays exiled
    @Test
    fun zedsExiledCardCastsCopiesOfItself() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(zed, "Island")
        alice.library("Forest", cub, "Forest")
        alice.castsWithoutCosts(zed)
        resolveAll()
        resolveAll()
        expect { alice.exile has cub }
        alice.activates(cub, choosing = listOf("Island"))
        playOut()
        expect {
            alice.exile has cub
            alice.exile has "Island"
            alice.battlefield has cub
        }
        assertTrue(state.battlefield.any { state.obj(it)!!.token })
    }

    // CR 602.5b: not during an opponent's turn
    @Test
    fun zedsExiledCardWorksOnlyDuringItsOwnersTurn() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(zed, "Island")
        alice.library("Forest", cub, "Forest", "Forest")
        bob.library("Swamp", "Swamp")
        alice.castsWithoutCosts(zed)
        resolveAll()
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.activates(cub, choosing = listOf("Island")) }
    }

    // ---- Life

    // CR 704.5a: at -1 life she stays in the game and makes ten Officers
    @Test
    fun varlessaSurvivesAtZeroLifeAndMakesOfficers() = scenario {
        alice.battlefield(varlessa)
        alice.library("Swamp", "Swamp")
        alice.life = 2
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.life shouldBe -1
            alice.battlefield.names.count { it == "Officer Token" } shouldBe 10
            alice.hand has "Swamp"
            alice.battlefield has varlessa
        }
    }

    // CR 119.3, 608.2e: each opponent loses again what they lost this turn, and Varek's controller gains it all (3 players)
    @Test
    fun varekDoublesEachOpponentsLifeLoss() = scenario(players = 3) {
        alice.battlefield(varek)
        alice.hand(drain, sip)
        alice.castsWithoutCosts(drain, target = "bob")
        resolveAll()
        alice.castsWithoutCosts(sip, target = "carol")
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 14
            carol.life shouldBe 18
            alice.life shouldBe 24
        }
    }

    // ---- Looks, plays and control

    // CR 406.3, 609.4b: each player may play only the cards they exiled, with mana of any type; a third player none (3 players)
    @Test
    fun mindLinkLetsEachExilerPlayWhatTheyExiled() = scenario(players = 3) {
        alice.battlefield("Plains")
        bob.battlefield("Plains")
        carol.battlefield("Plains")
        alice.hand(mindLink)
        alice.library(jolt, "Island")
        bob.library(quip, hymn, "Swamp")
        alice.castsWithoutCosts(mindLink, target = "bob")
        resolveAll()
        expect {
            alice.exile has jolt
            bob.exile has quip
            bob.exile has hymn
        }
        val aliceCan = castOptions(alice)
        assertTrue(card(quip) in aliceCan && card(hymn) in aliceCan && card(jolt) !in aliceCan)
        alice.casts(quip, paying = listOf("Plains"))
        alice.passes()
        val bobCan = castOptions(bob)
        assertTrue(card(jolt) in bobCan && card(hymn) !in bobCan)
        bob.passes()
        assertTrue(castOptions(carol).none { it == card(jolt) || it == card(hymn) })
        carol.passes()
        expect { alice.life shouldBe 22 }
    }

    // CR 400.11, 601.3: one permanent card from outside the game or the graveyard each turn
    @Test
    fun simulationDeckPlaysOnePermanentFromOutsideOrGraveyardEachTurn() = scenario {
        alice.battlefield(simDeck, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.outside(ox)
        alice.graveyard(cub)
        assertTrue(card(ox) in castOptions(alice) && card(cub) in castOptions(alice))
        alice.casts(ox)
        resolveAll()
        expect { alice.battlefield has ox }
        assertTrue(card(cub) !in castOptions(alice))
    }

    // CR 400.11: not during an opponent's turn
    @Test
    fun simulationDeckWorksOnlyOnYourTurn() = scenario {
        alice.battlefield(simDeck, "Forest", "Forest", "Forest")
        alice.outside(ox)
        turn(number = 2, active = bob, priority = alice)
        assertTrue(card(ox) !in castOptions(alice))
    }

    // CR 601.2c, 205.1b, 611.2c: one creature with mana value 3 or less from each opponent, untapped, a Warrior with haste until end of turn
    @Test
    fun korvathTakesOneSmallCreatureFromEachOpponent() = scenario(players = 3) {
        bob.battlefield(cub, bigOx, tapped = true)
        carol.battlefield(ox)
        alice.hand(korvath)
        alice.castsWithoutCosts(korvath)
        resolveAll()
        val d = decision<Decision.Targets>()
        assertTrue(card(bigOx) !in d.options.map { (it as? arcanum.engine.state.TargetRef.Object)?.id })
        targets(cub)
        targets(ox)
        resolveAll()
        expect {
            cub.controller shouldBe alice.id
            ox.controller shouldBe alice.id
            cub.tapped shouldBe false
            assertTrue(Subtype("Warrior") in cub.subtypes && Subtype("Bear") in cub.subtypes)
            cub hasKeyword Keyword.Haste.kind
            bigOx.controller shouldBe bob.id
        }
    }

    // CR 603.4: a creature returned to your hand this turn draws a card at the end step
    @Test
    fun tamsinDrawsAfterAReturnToHand() = scenario {
        alice.battlefield(cub)
        alice.hand(tamsin)
        alice.library("Plains", "Plains")
        alice.castsWithoutCosts(tamsin)
        resolveAll()
        if (pending is Decision.Targets) targets(cub)
        resolveAll()
        expect { alice.hand has cub }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 603.4: nothing returned, no card
    @Test
    fun tamsinDrawsNothingWithoutAReturn() = scenario {
        alice.battlefield(cub)
        alice.hand(tamsin)
        alice.library("Plains", "Plains")
        alice.castsWithoutCosts(tamsin)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(emptyList()))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 609.3: a land among the five onto the battlefield tapped, the rest into the graveyard
    @Test
    fun punchItPutsALandTappedAndMillsTheRest() = scenario {
        alice.hand(punch)
        alice.library("Island", cub, "Forest", ox, "Swamp", "Plains")
        alice.castsWithoutCosts(punch)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.battlefield has "Forest"
            "Forest".tapped shouldBe true
            alice.graveyard.size shouldBe 4
            alice.library.size shouldBe 1
        }
    }

    // CR 701.20, 603.7: two lands put onto the battlefield, the others to the bottom, the lands sacrificed at your end step
    @Test
    fun jorinsLandsAreSacrificedAtYourEndStep() = scenario {
        alice.battlefield(jorin)
        alice.library(cub, "Island", ox, "Forest", "Swamp")
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            alice.battlefield has "Island"
            alice.battlefield has "Forest"
            alice.library.size shouldBe 3
        }
        assertEquals("Swamp", state.obj(state.player(alice.id).library.first())!!.definition.faces[0].name)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has "Island"
            alice.graveyard has "Forest"
        }
    }

    // ---- Searches

    // CR 608.2h: the creature's controller as it last existed searches, not its owner (3 players)
    @Test
    fun parsecsLetsTheLastControllerSearch() = scenario(players = 3) {
        carol.battlefield(cub)
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf(cub), duration = Duration.Indefinitely)
        bob.library("Plains", "Swamp")
        carol.library("Plains", "Swamp")
        alice.hand(parsecs)
        alice.castsWithoutCosts(parsecs, target = cub)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        bob.chooses("Plains")
        resolveAll()
        expect {
            carol.exile has cub
            bob.hand has "Plains"
            carol.hand.size shouldBe 0
        }
    }

    // CR 701.23a: every card of that name in the library
    @Test
    fun dronesFindEveryCopyOfThemselves() = scenario {
        alice.hand(drones)
        alice.library(drones, "Island", drones)
        alice.castsWithoutCosts(drones)
        resolveAll()
        answer(Answer.YesNo(true))
        answer(Answer.Objects((pending as Decision.ChooseObjects).options))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.library.size shouldBe 1
        }
    }

    // CR 122.6: the Spacecraft enters with seven charge counters
    @Test
    fun orrinPutsASpacecraftWithSevenChargeCounters() = scenario {
        alice.battlefield(orrin, ensign, ensign, ensign, ensign, ensign, ensign)
        alice.library(shuttle, "Plains")
        alice.activates(orrin)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(shuttle)
        resolveAll()
        expect {
            alice.battlefield has shuttle
            shuttle.counters(arcanum.api.KeywordExpansions.CHARGE) shouldBe 7
        }
    }
}
