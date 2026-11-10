package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * INR bug-class sweep: siblings of the engine fixes INR and FDN made one card at a time (a later choice that doesn't see an earlier
 * one, an event's object lost by a later effect, a replacement skipped on an edge path, choices of several players in object order,
 * "if you do" asking whether a step was tried rather than done, counters put by an effect or not). Invented cards with oracle wordings.
 */
class InrSweepScenariosTest {
    private val Scenario.carol get() = players[2]
    private fun Scenario.libraries() = players.forEach { it.library("Island", "Island", "Island") }

    private val prowler = Cards.invent("Sweep Fen Prowler", "{2}{G}", "Creature — Beast", "", "3/3")
    private val duelist = Cards.invent("Sweep Thicket Duelist", "{1}{G}", "Creature — Elf Warrior", "", "2/2")
    private val sentry = Cards.invent("Sweep Ashen Sentry", "{1}{B}", "Creature — Zombie Soldier", "", "2/2")
    private val strider = Cards.invent("Sweep Brook Strider", "{1}{U}", "Creature — Merfolk", "", "1/2")

    // ---- An event's object, named by a delayed trigger created on resolution (sibling of inr chunks 05 and 14)

    private val warden = Cards.invent("Sweep Night Warden", "{2}{B}", "Enchantment", """
        Whenever another nontoken creature you control enters, sacrifice it at the beginning of the next end step.
    """)

    // CR 603.7c, 608.2h: "it" is the creature that entered, fixed as the delayed trigger is created; the end step's own event names no object
    @Test
    fun delayedSacrificeFindsTheCreatureThatEntered() = scenario {
        alice.battlefield(warden)
        alice.hand(prowler)
        alice.castsWithoutCosts(prowler)
        resolveAll()
        expect { alice.battlefield has prowler }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has prowler }
    }

    // ---- A copy's new targets: a later slot sees the earlier ones (sibling of inr chunk 01's trigger target fix)

    private val brawl = Cards.invent("Sweep Ember Brawl", "{1}{G}", "Sorcery", "Target creature you control fights another target creature.")
    private val echo = Cards.invent("Sweep Mirror Echo", "{U}{U}", "Instant", "Copy target instant or sorcery spell. You may choose new targets for the copy.")
    private val rebuke = Cards.invent("Sweep Kindled Rebuke", "{1}{R}", "Sorcery",
        "Sweep Kindled Rebuke deals 2 damage to target opponent or planeswalker and 2 damage to up to one target creature that player or that planeswalker's controller controls.")

    private fun Scenario.ref(name: String): TargetRef = TargetRef.Object(card(name))

    // CR 707.10c, 115.3: the copy's new "another target creature" can't be the creature newly chosen as its first target
    @Test
    fun copysSecondTargetSeesItsNewFirstTarget() = scenario {
        alice.battlefield(prowler, duelist)
        bob.battlefield(sentry)
        alice.hand(brawl, echo)
        alice.castsWithoutCosts(brawl, targets = listOf(prowler, sentry))
        alice.castsWithoutCosts(echo, target = brawl)
        resolve()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(ref(duelist))))
        answer(Answer.YesNo(true))
        val second = decision<Decision.Targets>()
        assertFalse(ref(duelist) in second.options, "another target creature: not the copy's own first target")
    }

    // CR 707.10c, 601.2c: with a new first target (Carol), "that player" in the second slot is Carol
    @Test
    fun copysThatPlayerIsItsNewTarget() = scenario(players = 3) {
        alice.battlefield(prowler)
        bob.battlefield(sentry)
        carol.battlefield(strider)
        alice.hand(rebuke, echo)
        alice.castsWithoutCosts(rebuke, targets = listOf("bob", sentry))
        alice.castsWithoutCosts(echo, target = rebuke)
        resolve()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        answer(Answer.YesNo(true))
        val second = decision<Decision.Targets>()
        assertTrue(ref(strider) in second.options, "Carol's creature")
        assertFalse(ref(sentry) in second.options, "not Bob's")
        answer(Answer.Targets(listOf(ref(strider))))
        resolveAll()
        expect {
            carol.life shouldBe 18
            carol.graveyard has strider
            bob.life shouldBe 18
            bob.graveyard has sentry
        }
    }

    // ---- Draw replacements with an empty library (sibling of inr chunk 07)

    private val thief = Cards.invent("Sweep Whisper Thief", "{2}{U}{B}", "Creature — Human Rogue", """
        Flash
        If an opponent would draw a card except the first one they draw in each of their draw steps, instead that player skips that draw and you draw a card.
    """, "3/1")
    private val insight = Cards.invent("Sweep Borrowed Insight", "{U}", "Instant", "Target player draws a card.")
    private val study = Cards.invent("Sweep Quiet Study", "{U}", "Instant", "Draw a card.")
    private val dredger = Cards.invent("Sweep Silt Dredger", "{1}{B}", "Creature — Zombie", "Dredge 2", "2/2")

    // CR 614.11, 121.6a: the replacement applies though Carol's library is empty: she skips the draw (and doesn't lose), Alice draws
    @Test
    fun thiefReplacesADrawFromAnEmptyLibrary() = scenario(players = 3) {
        alice.battlefield(thief)
        alice.library("Plains")
        bob.library("Island")
        alice.hand(insight)
        alice.castsWithoutCosts(insight, target = "carol")
        resolveAll()
        assertFalse(state.player(carol.id).lost, "Carol skipped the draw")
        expect { alice.hand has "Plains" }
    }

    // CR 702.52a, 702.52b, 704.5b: with fewer cards in the library than dredge needs, dredge can't replace the draw: the player loses
    @Test
    fun dredgeCantReplaceADrawFromAnEmptyLibrary() = scenario {
        alice.graveyard(dredger)
        alice.hand(study)
        bob.library("Island")
        alice.castsWithoutCosts(study)
        resolveAll()
        assertFalse(pending is Decision.May, "no dredge offer")
        assertTrue(state.player(alice.id).lost)
    }

    // ---- A mode that couldn't choose its targets (sibling of fdn's spell and triggered modes)

    private val totem = Cards.invent("Sweep Vigil Totem", "{2}", "Artifact", """
        {T}: Choose one —
        • This artifact deals 1 damage to target creature.
        • You gain 1 life.
    """)

    // CR 700.2a: a modal activated ability's mode with no legal target can't be chosen
    @Test
    fun totemsDamageModeNeedsACreature() = scenario {
        alice.battlefield(totem)
        alice.activates(totem)
        val modes = decision<Decision.Modes>()
        assertFalse(modes.options[0].available, "no creature to target")
        assertTrue(modes.options[1].available)
    }

    // ---- Counters put by an effect or not (sibling of fdn's doubled entering counters)

    private val grove = Cards.invent("Sweep Doubling Grove", "{4}{G}", "Enchantment", """
        If an effect would put one or more counters on a permanent you control, it puts twice that many of those counters on that permanent instead.
    """)
    private val saga = Cards.invent("The Sweep Saga", "{2}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — You gain 1 life.
        II — You gain 2 life.
        III — You gain 4 life.
    """)
    private val walker = Cards.invent("Sweep Grove Sage", "{2}{G}", "Legendary Planeswalker — Sage", "[+1]: You gain 1 life.", loyalty = "3")

    // CR 714.3c: the precombat main phase's lore counter is a turn-based action, not an effect: it isn't doubled (chapter II only)
    @Test
    fun mainPhaseLoreCounterIsntDoubled() = scenario {
        libraries()
        alice.battlefield(grove)
        alice.battlefield(saga, counters = listOf(CounterKind.Lore to 1))
        turn(step = TurnStep.Draw)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        expect {
            saga.counters(CounterKind.Lore) shouldBe 2
            alice.life shouldBe 22
        }
    }

    // CR 606.4, 118.3: a loyalty ability's +1 is its cost, not an effect: one counter
    @Test
    fun loyaltyCostIsntDoubled() = scenario {
        alice.battlefield(grove)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(walker)
        expect { walker.counters(CounterKind.Loyalty) shouldBe 4 }
    }

    // ---- "If you do" after a mandatory step that can fail (sibling of fdn's Blot fix)

    private val host = Cards.invent("Sweep Plainsrider Host", "{6}{R}", "Creature — Human Knight", """
        Trample, haste
        At the beginning of your end step, if this creature attacked this turn, return it to its owner's hand. If you do, create a number of 1/1 white Human Soldier creature tokens equal to its toughness.
    """, "7/4")
    private val ruin = Cards.invent("Sweep Sudden Ruin", "{1}{B}", "Instant", "Destroy target creature.")

    // CR 608.2c, 400.7: destroyed with the trigger on the Stack, it can't be returned (it's a new object in the graveyard), so no tokens
    @Test
    fun hostLeftBeforeItsReturnMakesNoTokens() = scenario {
        libraries()
        alice.battlefield(host)
        bob.hand(ruin)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(host, defender = "bob")
        advanceTo(TurnStep.End)
        bob.castsWithoutCosts(ruin, target = host)
        resolve()
        resolveAll()
        expect {
            alice.graveyard has host
            alice.hand lacks host
        }
        assertEquals(0, state.battlefield.count { state.obj(it)?.definition?.faces?.firstOrNull()?.name == "Human Soldier Token" }, "no tokens")
    }

    // ---- "An opponent chooses" with several opponents (sibling of inr chunk 08's "any opponent may")

    private val teachings = Cards.invent("Sweep Elemental Lessons", "{4}{G}", "Instant", """
        Search your library for up to four land cards with different names and reveal them. An opponent chooses two of those cards. Put the chosen cards into your graveyard and the rest onto the battlefield tapped, then shuffle.
    """)

    // CR 102.3, 101.4: with two opponents, Alice picks the one who chooses (Carol here), not the first in turn order
    @Test
    fun anOpponentChoosesIsPickedByTheController() = scenario(players = 3) {
        alice.library("Plains", "Island", "Swamp", "Forest")
        bob.library("Island"); carol.library("Island")
        alice.hand(teachings)
        alice.castsWithoutCosts(teachings)
        resolve()
        val search = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(search.options.take(4)))
        val who = decision<Decision.ChoosePlayer>()
        assertEquals(alice.id, who.player)
        assertEquals(setOf(bob.id, carol.id), who.options.toSet())
        answer(Answer.Player(carol.id))
        val pick = decision<Decision.ChooseObjects>()
        assertEquals(carol.id, pick.player)
        answer(Answer.Objects(pick.options.take(2)))
        resolveAll()
        expect { alice.graveyard.size shouldBe 3 }
    }

    // ---- Choices of several players in turn order (sibling of inr chunk 09's "for each" fix)

    private val edict = Cards.invent("Sweep Grim Edict", "{1}{B}", "Instant", "Each player sacrifices a creature.")

    // CR 101.4: on Bob's turn, Bob chooses first, then Carol, then Alice
    @Test
    fun edictChoicesGoInTurnOrder() = scenario(players = 3) {
        libraries()
        alice.battlefield(prowler, duelist)
        bob.battlefield(sentry, sentry)
        carol.battlefield(strider, strider)
        alice.hand(edict)
        turn(number = 2, active = bob)
        alice.castsWithoutCosts(edict)
        resolve()
        val order = mutableListOf<arcanum.engine.state.PlayerId>()
        while (pending is Decision.ChooseObjects) {
            val d = decision<Decision.ChooseObjects>()
            order += d.player
            answer(Answer.Objects(listOf(d.options.first())))
        }
        assertEquals(listOf(bob.id, carol.id, alice.id), order)
    }
}
