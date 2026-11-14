package arcanum.engine.sets.dmr

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

/** DMR sweep: the siblings of the set's engine fixes (other paths with the same gap), each with the rule it follows. */
class DmrSweepScenariosTest {
    // ---- Sacrifice only what you control (CR 701.21a), sibling: the delayed "sacrifice them" of created tokens

    private val rally = Cards.invent("Kindlecall Rally", "{1}{R}", "Sorcery",
        "Create two 1/1 red Goblin creature tokens with haste. Sacrifice them at the beginning of the next end step.")
    private val wrest = Cards.invent("Wrested Allegiance", "{2}{R}", "Instant",
        "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.")

    // CR 701.21a: at the end step alice sacrifices the token she still controls; the one bob took is his, so it stays (3 players)
    @Test
    fun delayedSacrificeOfTokensSkipsTheOneAnotherPlayerControls() = scenario(players = 3) {
        alice.hand(rally)
        bob.hand(wrest)
        alice.castsWithoutCosts(rally)
        resolve()
        alice.passes()
        bob.castsWithoutCosts(wrest, target = "Goblin Token")
        resolve()
        expect { card("Goblin Token", 0).controller shouldBe bob.id }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.battlefield has "Goblin Token"
            alice.battlefield lacks "Goblin Token"
        }
    }

    // ---- "Defending player" of a creature attacking a battle is its protector (CR 310.9d, 506.2), siblings of the attack restriction

    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Ashen Ford", "{2}{R}", "Battle — Siege", "", defense = "5"),
        OracleCard("Ashen Ford Titan", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val scout = Cards.invent("Tidewading Scout", "{U}", "Creature — Merfolk Scout", "Islandwalk", "2/1")
    private val devourer = Cards.invent("Hollowmaw Devourer", "{5}", "Creature — Eldrazi", "Annihilator 1", "4/4")
    private val sentry = Cards.invent("Fordbank Sentry", "{G}", "Creature — Human Soldier", "", "1/3")

    /** Alice (with [attacker] and an Island of her own) casts the Siege with [protector] protecting it, then attacks it. */
    private fun Scenario.attackSiege(attacker: String, protector: String) {
        alice.battlefield(attacker, "Island", "Mountain", "Mountain", "Mountain")
        alice.hand(siege)
        alice.casts(siege)
        allPass()
        assertIs<Decision.ChoosePlayer>(pending)
        answer(Answer.Player(named(protector).id))
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(attacker), TargetRef.Object(card(siege))))))
    }

    private fun Scenario.named(name: String) = when (name) { "bob" -> bob; "carol" -> carol; else -> alice }

    // CR 702.14c, 310.9d: islandwalk looks at the defending player, the battle's protector: bob has no Island, so he may block though
    // alice (the battle's controller) has one (3 players)
    @Test
    fun landwalkAttackingABattleLooksAtItsProtector() = scenario(players = 3) {
        bob.battlefield(sentry)
        carol.battlefield("Island")
        attackSiege(scout, "bob")
        allPass()
        bob.blocks(sentry to scout)
        expect { card(sentry).zone shouldBe Zone.Battlefield }
    }

    // CR 702.14c, 310.9d: the protector controls an Island: the walker can't be blocked
    @Test
    fun landwalkAttackingABattleWhoseProtectorHasTheLand() = scenario(players = 3) {
        bob.battlefield(sentry, "Island")
        attackSiege(scout, "bob")
        allPass()
        assertFails { bob.blocks(sentry to scout) }
    }

    // CR 702.86a, 310.9d: annihilator makes the defending player, the battle's protector, sacrifice; alice, the battle's controller, doesn't
    @Test
    fun annihilatorAttackingABattleMakesItsProtectorSacrifice() = scenario(players = 3) {
        bob.battlefield("Forest")
        carol.battlefield("Plains")
        attackSiege(devourer, "carol")
        resolveAll()
        if (pending is Decision.ChooseObjects) carol.chooses("Plains")
        expect {
            carol.graveyard has "Plains"
            bob.battlefield has "Forest"
            alice.battlefield has siege
            alice.battlefield has "Island"
        }
    }

    // ---- Control and affected objects fixed as the effect begins (CR 611.2c), sibling: a pump of a set of creatures

    private val siphon = Cards.invent("Silt Siphon", "{1}{B}", "Instant", "Creatures your opponents control get -1/-0 until end of turn.")
    private val plodder = Cards.invent("Ironhide Plodder", "{1}{G}", "Creature — Beast", "", "2/2")
    private val lurker = Cards.invent("Cliffside Lurker", "{1}{G}", "Creature — Beast", "", "3/3")

    // CR 611.2c: the creatures affected are found as the effect begins: a creature an opponent puts onto the battlefield later isn't
    // affected (3 players)
    @Test
    fun aPumpFromAResolvedSpellDoesntAffectCreaturesThatComeLater() = scenario(players = 3) {
        bob.battlefield(plodder)
        carol.hand(lurker)
        alice.hand(siphon)
        alice.castsWithoutCosts(siphon)
        resolve()
        alice.passes()
        bob.passes()
        carol.castsWithoutCosts(lurker)
        resolve()
        expect {
            plodder.power shouldBe 1
            lurker.power shouldBe 3
        }
    }

    // ---- An "instead" replacement of one object of a multi-object event replaces only that part (CR 614.6), siblings of the mill fix

    private val colossus = Cards.invent("Wellspring Colossus", "{6}", "Artifact Creature — Golem", """
        If Wellspring Colossus would be put into a graveyard from anywhere, reveal Wellspring Colossus and shuffle it into its owner's library instead.
    """, "6/6")
    private val reckoning = Cards.invent("Tidal Reckoning", "{2}{W}{W}", "Sorcery", "Destroy all creatures.")
    private val grovebeast = Cards.invent("Stubborn Grovebeast", "{3}{G}{G}", "Creature — Beast", """
        If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
    """, "4/4")
    private val demand = Cards.invent("Wrenching Demand", "{1}{B}", "Sorcery", "Target player discards two cards.")
    private val ledger = Cards.invent("Marsh Ledger Sage", "{1}{U}", "Sorcery", "Each player mills two cards.")

    // CR 614.6, 701.8a: destroying all creatures, the Colossus is shuffled into its owner's library instead; the others are still destroyed
    @Test
    fun destroyAllStillDestroysTheOthersWhenOneIsReplaced() = scenario {
        alice.battlefield(colossus, plodder)
        bob.battlefield(lurker)
        alice.library("Island")
        alice.hand(reckoning)
        alice.castsWithoutCosts(reckoning)
        resolve()
        expect {
            alice.graveyard has plodder
            bob.graveyard has lurker
            alice.library has colossus
            alice.graveyard lacks colossus
        }
        assertEquals(2, state.player(alice.id).library.size)
    }

    // CR 614.6, 701.9a: discarding two cards to an opponent's spell, the Grovebeast enters the battlefield instead; the other card is
    // still discarded
    @Test
    fun discardingTwoWithOneReplacedStillDiscardsTheOther() = scenario {
        alice.hand(grovebeast, "Island")
        bob.hand(demand)
        alice.passes()
        bob.castsWithoutCosts(demand, target = "alice")
        resolve()
        if (pending is Decision.ChooseObjects) alice.chooses(grovebeast, "Island")
        expect {
            alice.battlefield has grovebeast
            alice.graveyard has "Island"
            alice.hand.size shouldBe 0
        }
    }

    // CR 614.6, 701.17a: each player mills two; the Colossus in bob's library is shuffled back, every other milled card is milled
    // (3 players)
    @Test
    fun eachPlayerMillsWithOneCardReplaced() = scenario(players = 3) {
        alice.library("Island", "Island", "Island")
        bob.library(colossus, "Forest", "Forest")
        carol.library("Plains", "Plains", "Plains")
        alice.hand(ledger)
        alice.castsWithoutCosts(ledger)
        resolve()
        expect {
            alice.graveyard.size shouldBe 3
            carol.graveyard.size shouldBe 2
            bob.graveyard.size shouldBe 1
            bob.graveyard has "Forest"
            bob.library has colossus
        }
        assertEquals(2, state.player(bob.id).library.size)
    }

    // ---- A delayed trigger a spell's static ability creates is not an ability of the permanent (CR 603.7a), siblings of the Mirage fix

    private val marauder = Cards.invent("Cinderdash Marauder", "{3}{R}", "Creature — Goblin Berserker", "Dash {1}{R}", "3/1")
    private val ruffian = Cards.invent("Gravelblitz Ruffian", "{3}{B}", "Creature — Human Rogue", "Blitz {1}{B}", "3/2")
    private val hush = Cards.invent("Hushfall Decree", "{2}{U}", "Enchantment", "Creatures lose all abilities.")

    // CR 702.109a, 603.7a: dash's return is a delayed trigger created by a static ability of the spell: the permanent returns to its
    // owner's hand at the next end step even though it has no abilities on the battlefield
    @Test
    fun dashedCreatureReturnsEvenWithoutAbilities() = scenario {
        bob.battlefield(hush)
        alice.battlefield("Mountain", "Mountain")
        alice.hand(marauder)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(marauder, way = alternativeOf(marauder, Keyword.Dash.Kind))
        allPass()
        expect { alice.battlefield has marauder }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.hand has marauder }
    }

    // CR 702.152a, 603.7a: blitz's sacrifice is a delayed trigger created by a static ability of the spell: still sacrificed without abilities
    @Test
    fun blitzedCreatureIsSacrificedEvenWithoutAbilities() = scenario {
        bob.battlefield(hush)
        alice.battlefield("Swamp", "Swamp")
        alice.hand(ruffian)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(ruffian, way = alternativeOf(ruffian, Keyword.Blitz.Kind))
        allPass()
        expect { alice.battlefield has ruffian }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.graveyard has ruffian }
    }

    private val courier = Cards.invent("Voidskip Courier", "{3}{U}", "Creature — Human Pilot", "Warp {U}", "2/2")

    // CR 702.185a, 608.3g: warp's exile is a delayed trigger created by a static ability of the spell as the permanent enters: the
    // permanent is exiled at the next end step even though it has no abilities on the battlefield
    @Test
    fun warpedCreatureIsExiledEvenWithoutAbilities() = scenario {
        bob.battlefield(hush)
        alice.battlefield("Island")
        alice.hand(courier)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(courier, way = alternativeOf(courier, Keyword.Warp.Kind))
        allPass()
        expect { alice.battlefield has courier }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.exile has courier }
    }

    private val rebuke = Cards.invent("Quelling Rebuke", "{U}{U}", "Instant", "Counter target spell.")

    // CR 608.3g: the delayed trigger is created as the permanent enters: a countered dash spell creates none, so nothing triggers at
    // the end step
    @Test
    fun counteredDashSpellCreatesNoDelayedTrigger() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(marauder)
        bob.hand(rebuke)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(marauder, way = alternativeOf(marauder, Keyword.Dash.Kind))
        alice.passes()
        bob.castsWithoutCosts(rebuke, target = marauder)
        resolve()
        allPass()
        expect { alice.graveyard has marauder }
        advanceTo(TurnStep.End)
        expect { stack.size shouldBe 0 }
    }

    // CR 702.109a: dashed and still on the battlefield at the end step of the turn it was cast, but the trigger happens only once: cast
    // again for its mana cost later, it stays
    @Test
    fun dashReturnHappensOnlyOnce() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(marauder)
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        alice.casts(marauder, way = alternativeOf(marauder, Keyword.Dash.Kind))
        allPass()
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.hand has marauder }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(marauder)
        allPass()
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.battlefield has marauder }
    }

    // ---- A land's mana replaced (CR 106.12b), siblings of the Damping Sphere fix: the payment planner and triggered mana

    private val orb = Cards.invent("Hushing Orb", "{2}", "Artifact",
        "If a land is tapped for two or more mana, it produces {C} instead of any other type and amount.")
    private val ruin = Cards.invent("Twinvault Ruin", null, "Land", "{T}: Add {C}{C}.")
    private val idol = Cards.invent("Plain Brass Idol", "{2}", "Artifact", "")
    private val tithe = Cards.invent("Verdure Tithe", "{G}", "Enchantment — Aura", """
        Enchant land
        Whenever enchanted land is tapped for mana, its controller adds an additional {G}.
    """)

    private fun Scenario.canCast(name: String) =
        arcanum.engine.Engine.options(state, alice.id).filterIsInstance<Input.Cast>().any { it.card == card(name) }

    // CR 106.12b, 601.2g: castability can't count on the replaced land for two mana: with the Ruin alone a {2} spell can't be cast;
    // with a Forest beside it, the Ruin's one {C} and the Forest pay {2}
    @Test
    fun aReplacedLandCountsAsOneManaForCastability() = scenario {
        alice.battlefield(orb, ruin)
        alice.hand(idol)
        assertEquals(false, canCast(idol))
        alice.battlefield("Forest")
        assertEquals(true, canCast(idol))
        alice.casts(idol)
        allPass()
        expect { alice.battlefield has idol }
    }

    // CR 106.12a, b, 605.1b: the replacement looks at the mana the land's own ability produces (one {G}); the Aura's triggered mana
    // ability adds its {G} apart from it, so the Forest gives two
    @Test
    fun triggeredAdditionalManaIsntPartOfTheLandsProduction() = scenario {
        alice.battlefield(orb, "Forest", tithe)
        attach(tithe, "Forest")
        alice.activates("Forest")
        expect { alice.pool.map { it.color } shouldBe listOf(arcanum.api.Color.Green, arcanum.api.Color.Green) }
    }

    // ---- A later instruction sees what an earlier one did to each player (CR 608.2c), sibling of "the life lost this way"

    private val extortion = Cards.invent("Gloomwake Extortion", "{2}{B}", "Sorcery",
        "Each opponent discards a card. You gain 2 life for each card discarded this way.")

    // CR 608.2c, 701.9a: the cards each opponent discarded are counted together; an opponent with no cards discards none (3 players)
    @Test
    fun lifeForEachCardDiscardedByEachOpponent() = scenario(players = 3) {
        bob.hand("Island", "Island")
        carol.hand("Plains")
        alice.hand(extortion)
        alice.castsWithoutCosts(extortion)
        resolveAll()
        while (pending is Decision.ChooseObjects) (if (state.player(bob.id).hand.size == 2) bob.chooses("Island") else carol.chooses("Plains"))
        expect {
            alice.life shouldBe 24
            bob.hand.size shouldBe 1
            carol.hand.size shouldBe 0
        }
    }

    @Test
    fun anOpponentWithoutCardsDiscardsNone() = scenario(players = 3) {
        bob.hand("Island")
        alice.hand(extortion)
        alice.castsWithoutCosts(extortion)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses("Island")
        expect { alice.life shouldBe 22 }
    }

    // ---- "Since your last turn" when the host trims the log (CR 500.1, 800.4i), sibling of the per-player last turn

    private val warden = Cards.invent("Hollowgate Warden", "{1}{W}", "Enchantment",
        "At the beginning of your upkeep, if you haven't been dealt combat damage since your last turn, you draw a card and you lose 1 life.")
    private val raider = Cards.invent("Brineback Raider", "{1}{U}", "Creature — Merfolk", "", "2/2")

    // CR 603.4, 800.4i: bob's combat damage on turn 2 is since alice's last turn (turn 1) though older than last turn (3 players): no
    // draw; the host's log trim at alice's upkeep keeps turn 2 for such questions
    @Test
    fun combatDamageTwoTurnsAgoIsSinceYourLastTurnAfterALogTrim() = scenario(players = 3) {
        alice.battlefield(warden)
        bob.battlefield(raider)
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        carol.library("Island", "Island", "Island")
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(raider, defender = "alice")
        advanceTo(TurnStep.Upkeep, turn = 4)
        expect { alice.life shouldBe 18 }
        state.forgetOlderTurns()
        assertEquals(true, state.eventsOf(2).isNotEmpty())
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.hand.size shouldBe 0
        }
    }
}
