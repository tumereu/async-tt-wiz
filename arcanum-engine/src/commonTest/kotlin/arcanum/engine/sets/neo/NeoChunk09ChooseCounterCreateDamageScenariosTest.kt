package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.dft.yes
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chunk 9 of NEO: a mode about "that Vehicle" in an attack trigger, put-or-remove for each kind of counter, a return that needs a sacrifice
 * sharing a card type, counters distributed among a target player's creatures and Vehicles, an intervening "if it doesn't have a counter" and
 * "combat damage is dealt to you", "X minus one" tokens for each opponent, "X plus 2" damage, split damage to a target opponent's creatures,
 * damage to each of X targets, and a search for a card with the same name as the target. */
class NeoChunk09ChooseCounterCreateDamageScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val indestructible = CounterKind.Keyword(Keyword.Indestructible.kind)

    private val wingrider = Cards.invent("Kitsune Wingrider", "{1}{W}", "Creature — Fox Pilot", """
        Whenever a Vehicle you control attacks, choose one —
        • That Vehicle gains first strike until end of turn.
        • Untap this creature.
    """, "2/2")
    private val marionette = Cards.invent("Playwright's Marionette", "{4}", "Artifact Creature — Construct",
        "When this creature enters, for each kind of counter on target permanent, put another counter of that kind on it or remove one from it.", "2/4")
    private val summons = Cards.invent("Sister-Spirit's Summons", "{3}{W}{B}", "Enchantment",
        "At the beginning of your end step, choose target permanent card in your graveyard. You may sacrifice a permanent that shares a card type with the chosen card. If you do, return the chosen card from your graveyard to the battlefield and it gains \"If this permanent would leave the battlefield, exile it instead of putting it anywhere else.\"")
    private val justice = Cards.invent("Call Forth Justice", "{1}{W}{W}{W}{W}", "Sorcery",
        "Return target permanent card from your graveyard to the battlefield, then distribute four +1/+1 counters among any number of creatures and/or Vehicles target player controls.")
    private val kaede = Cards.invent("Kaede, Asari Captain", "{1}{R}{W}", "Legendary Creature — Human Samurai", """
        Haste
        Whenever Kaede deals combat damage to a player, if it doesn't have an indestructible counter on it, put an indestructible counter on it.
        Whenever combat damage is dealt to you, remove an indestructible counter from Kaede.
    """, "3/3")
    private val uprising = Cards.invent("Sokenzan Uprising", "{X}{R}{W}", "Sorcery", """
        Create X 2/2 white Samurai creature tokens with vigilance. They gain menace and haste until end of turn.
        Each opponent creates X minus one 2/2 white Samurai creature tokens with vigilance.
    """)
    private val discharge = Cards.invent("Blaze Discharge", "{X}{R}", "Instant",
        "Blaze Discharge deals X damage to target creature or planeswalker. If you controlled a modified creature as you cast this spell, it deals X plus 2 damage instead. (Equipment, Auras you control, and counters are modifications.)")
    private val tremor = Cards.invent("Tremor Wave", "{2}{R}", "Instant",
        "Tremor Wave deals 2 damage to any target and 1 damage to each nonartifact creature target opponent controls.")
    private val kick = Cards.invent("Whirling Wheel Kick", "{X}{X}{G}{G}", "Sorcery",
        "Target creature you control deals damage equal to its power to each of X target creatures and/or planeswalkers.")
    private val march = Cards.invent("March of Sprouting Kin", "{X}{G}", "Instant", """
        As an additional cost to cast this spell, you may exile any number of green cards from your hand. This spell costs {2} less to cast for each card exiled this way.
        Choose target creature with mana value less than X. Search your library for a creature card with the same name as that creature, put it onto the battlefield tapped, then shuffle.
    """)

    private val cart = Cards.invent("Neo Nine Cart", "{2}", "Artifact — Vehicle",
        "Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)", "3/3")
    private val bear = Cards.invent("Neo Nine Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Neo Nine Ox", "{3}{G}", "Creature — Ox", "", "3/4")
    private val golem = Cards.invent("Neo Nine Golem", "{2}", "Artifact Creature — Golem", "", "1/1")
    private val shrine = Cards.invent("Neo Nine Shrine", "{1}", "Artifact", "")
    private val doom = Cards.invent("Neo Nine Doom", "{0}", "Instant", "Destroy target creature.")

    private val weave = Cards.invent("Talespinner's Weave", "{0}", "Instant", """
        Choose one —
        • Put two +1/+1 counters on target creature you control.
        • Put two lore counters on target Saga you control. The next time one or more enchantment creatures you control enter this turn, each enters with two additional +1/+1 counters on it.
    """)
    private val tale = Cards.invent("Neo Nine Tale", "{1}{G}", "Enchantment — Saga",
        "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after V.)\nI, II, III, IV, V — You gain 1 life.")
    private val spirits = Cards.invent("Neo Nine Spirit Call", "{0}", "Sorcery", "Create two 1/1 white Spirit enchantment creature tokens.")
    private val raise = Cards.invent("Neo Nine Raising", "{0}", "Sorcery",
        "Return up to two target enchantment creature cards from your graveyard to the battlefield.")
    private val dryad = Cards.invent("Neo Nine Dryad", "{1}{G}", "Enchantment Creature — Dryad", "", "1/1")
    private val nymph = Cards.invent("Neo Nine Nymph", "{1}{W}", "Enchantment Creature — Nymph", "", "2/2")

    private fun Scenario.p1p1On(id: arcanum.engine.state.ObjectId): Int = state.obj(id)!!.counters.filter { it.kind == p1p1 }.sumOf { it.count }

    // CR 614.1c, 616.1: the next time enchantment creatures enter, each of them gets the counters (both tokens of one event); the effect is
    // then used up, so the creatures of a later event get none
    @Test
    fun weaveGivesEachTokenOfTheNextEventCounters() = scenario {
        alice.battlefield(tale)
        alice.hand(weave, spirits, raise)
        alice.graveyard(dryad)
        alice.casts(weave, target = tale, modes = listOf(1))
        resolveAll()
        alice.casts(spirits)
        resolveAll()
        alice.casts(raise, targets = listOf(dryad))
        resolveAll()
        val made = tokens("Spirit Token")
        assertEquals(listOf(2, 2), made.map { p1p1On(it) })
        expect { dryad.counters(p1p1) shouldBe 0 }
    }

    // CR 614.1c, 616.1, 614.12: two cards returned together both enter with the counters
    @Test
    fun weaveGivesEachCardReturnedTogetherCounters() = scenario {
        alice.battlefield(tale)
        alice.hand(weave, raise)
        alice.graveyard(dryad, nymph)
        alice.casts(weave, target = tale, modes = listOf(1))
        resolveAll()
        alice.casts(raise, targets = listOf(dryad, nymph))
        resolveAll()
        expect {
            dryad.counters(p1p1) shouldBe 2
            nymph.counters(p1p1) shouldBe 2
        }
    }

    // CR 508.1a, 700.2: the Vehicle crewed by the Wingrider attacks; the first mode gives that Vehicle first strike
    @Test
    fun wingriderGivesTheAttackingVehicleFirstStrike() = scenario {
        alice.battlefield(wingrider, cart)
        alice.activates(cart, choosing = listOf(wingrider))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cart)
        modes(0)
        resolveAll()
        expect { cart hasKeyword Keyword.FirstStrike.kind }
    }

    // CR 700.2: the second mode untaps the Wingrider, tapped to crew
    @Test
    fun wingriderUntapsItselfInstead() = scenario {
        alice.battlefield(wingrider, cart)
        alice.activates(cart, choosing = listOf(wingrider))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cart)
        modes(1)
        resolveAll()
        expect {
            wingrider.tapped shouldBe false
            cart lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 122.1, 608.2d: for each kind on the target, a choice: one more +1/+1 counter, one fewer charge counter
    @Test
    fun marionettePutsOneKindAndRemovesTheOther() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(marionette)
        bob.battlefield(ox, counters = listOf(p1p1 to 2, CounterKind.Named("charge") to 1))
        alice.casts(marionette)
        resolveAll()
        answer(Answer.Targets(listOf(target(ox))))
        resolveAll()
        while (pending is Decision.ChooseOption) {
            val d = decision<Decision.ChooseOption>()
            answer(Answer.Option(d.options.first { if ("+1/+1" in it) it.startsWith("Put") else it.startsWith("Remove") }))
            resolveAll()
        }
        expect {
            ox.counters(p1p1) shouldBe 3
            ox.counters(CounterKind.Named("charge")) shouldBe 0
        }
    }

    // CR 118.12, 400.7: a creature sacrificed for a creature card returns it; CR 614.1a: when it would leave again, it's exiled instead
    @Test
    fun summonsTradesACreatureForTheChosenCardWhichIsExiledLater() = scenario {
        alice.battlefield(summons, bear)
        alice.graveyard(ox)
        alice.hand(doom)
        advanceTo(TurnStep.End)
        resolveAll()
        yes()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ox.zone shouldBe Zone.Battlefield
        }
        alice.casts(doom, target = ox)
        resolveAll()
        expect { ox.zone shouldBe Zone.Exile }
    }

    // CR 118.12: nothing Alice controls shares a card type with the creature card, so it can't be paid and the card stays
    @Test
    fun summonsNeedsAPermanentSharingACardType() = scenario {
        alice.battlefield(summons, shrine)
        alice.graveyard(ox)
        advanceTo(TurnStep.End)
        resolveAll()
        if (pending is Decision.May) yes()
        expect {
            shrine.zone shouldBe Zone.Battlefield
            ox.zone shouldBe Zone.Graveyard
        }
    }

    // CR 115.1, 608.2d: Bob is the target player; his creature and his noncreature Vehicle share the four counters; Carol's creature can't
    @Test
    fun justiceDistributesAmongTheTargetPlayersCreaturesAndVehicles() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(justice)
        alice.graveyard(shrine)
        bob.battlefield(bear, cart)
        carol.battlefield(ox)
        alice.casts(justice, targets = listOf(shrine, "bob"))
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(setOf(card(bear), card(cart)), choice.options.toSet())
        alice.chooses(bear, cart)
        answer(Answer.Division(listOf(Share(target(bear), 3), Share(target(cart), 1))))
        resolveAll()
        expect {
            shrine.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 3
            cart.counters(p1p1) shouldBe 1
            ox.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.4, 122.1b: unblocked, Kaede gets an indestructible counter
    @Test
    fun kaedeGetsAnIndestructibleCounter() = scenario {
        alice.battlefield(kaede)
        hits(kaede)
        resolveAll()
        expect {
            kaede.counters(indestructible) shouldBe 1
            kaede hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 603.4: with a counter already on it, the ability doesn't trigger again
    @Test
    fun kaedeGetsNoSecondCounter() = scenario {
        alice.battlefield(kaede, counters = listOf(indestructible to 1))
        hits(kaede)
        resolveAll()
        expect { kaede.counters(indestructible) shouldBe 1 }
    }

    // CR 510.2, 603.2c: Bob's two attackers deal combat damage to Alice at once: one trigger removes one counter
    @Test
    fun kaedeLosesOneCounterWhenAliceIsDealtCombatDamage() = scenario {
        alice.battlefield(kaede, counters = listOf(indestructible to 2))
        bob.battlefield(bear, ox)
        turn(number = 2, active = bob, step = TurnStep.DeclareAttackers)
        bob.attacks(bear, ox)
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) alice.blocks()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.life shouldBe 15
            kaede.counters(indestructible) shouldBe 1
        }
    }

    // CR 107.3a, 111.2: X is 3: Alice creates three Samurai, Bob and Carol two each
    @Test
    fun uprisingGivesEachOpponentOneFewer() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains", "Plains")
        alice.hand(uprising)
        alice.casts(uprising, x = 3)
        resolveAll()
        expect {
            samuraiOf(alice) shouldBe 3
            samuraiOf(bob) shouldBe 2
            samuraiOf(carol) shouldBe 2
            tokens("Samurai Token").filter { state.obj(it)?.controller == alice.id }.forEach { arcanum.engine.scenario.CardRef(it, "Samurai") hasKeyword Keyword.Haste.kind }
            tokens("Samurai Token").filter { state.obj(it)?.controller == bob.id }.forEach { arcanum.engine.scenario.CardRef(it, "Samurai") lacksKeyword Keyword.Haste.kind }
        }
    }

    // CR 107.1b: X is 1: the opponents create none
    @Test
    fun uprisingWithXOneGivesOpponentsNothing() = scenario(players = 3) {
        alice.battlefield("Mountain", "Plains", "Plains")
        alice.hand(uprising)
        alice.casts(uprising, x = 1)
        resolveAll()
        expect {
            samuraiOf(alice) shouldBe 1
            samuraiOf(bob) shouldBe 0
            samuraiOf(carol) shouldBe 0
        }
    }

    // CR 700.9, 601.2: Alice controlled a creature with a counter as she cast it: X plus 2 is 4 damage
    @Test
    fun dischargeDealsTwoMoreWithAModifiedCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(discharge)
        bob.battlefield(ox)
        alice.casts(discharge, target = ox, x = 2)
        resolveAll()
        expect { ox.zone shouldBe Zone.Graveyard }
    }

    // CR 608.2c: without a modified creature, X damage only
    @Test
    fun dischargeDealsXWithoutOne() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(discharge)
        bob.battlefield(ox)
        alice.casts(discharge, target = ox, x = 2)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Battlefield
            ox.view.obj?.damage shouldBe 2
        }
    }

    // CR 120.2, 115.1: 2 damage to Bob, 1 to each nonartifact creature Carol (the target opponent) controls; Bob's creature and Carol's
    // artifact creature are untouched
    @Test
    fun tremorWaveHitsOnlyTheTargetOpponentsNonartifactCreatures() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(tremor)
        bob.battlefield(bear)
        carol.battlefield(ox, golem)
        alice.casts(tremor, targets = listOf("bob", "carol"))
        resolveAll()
        expect {
            bob.life shouldBe 18
            bear.view.obj?.damage shouldBe 0
            ox.view.obj?.damage shouldBe 1
            golem.view.obj?.damage shouldBe 0
        }
    }

    // CR 107.3a: exactly X targets; CR 115.3: the creature Alice controls may be one of them, and deals damage to itself too
    @Test
    fun kickHitsEachOfXTargetsItselfIncluded() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", ox)
        alice.hand(kick)
        bob.battlefield(bear, golem)
        alice.casts(kick, targets = listOf(ox, ox, bear), x = 2)
        resolveAll()
        expect {
            ox.view.obj?.damage shouldBe 3
            bear.zone shouldBe Zone.Graveyard
            golem.zone shouldBe Zone.Battlefield
        }
    }

    // CR 107.3a: mana value less than X (3); CR 201.2: the card found has the target's name; it enters tapped (CR 701.23a)
    @Test
    fun marchFindsACardWithTheTargetsName() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(march)
        alice.library(bear, "Forest")
        bob.battlefield(bear, ox)
        alice.casts(march, x = 3)
        val targets = decision<Decision.Targets>()
        assertTrue(card(ox, zone = Zone.Battlefield) !in targets.options.map { (it as arcanum.engine.state.TargetRef.Object).id })
        answer(Answer.Targets(targets.options.take(1)))
        // no green card to exile for the additional cost: none chosen
        (pending as? Decision.ChooseObjects)?.takeIf { it.options.isEmpty() }?.let { answer(Answer.Objects(emptyList())) }
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        val found = decision<Decision.ChooseObjects>()
        assertEquals(listOf(card(bear, zone = Zone.Library)), found.options)
        answer(Answer.Objects(found.options))
        resolveAll()
        val mine = state.battlefield.filter { state.obj(it)?.controller == alice.id && view()[it]?.characteristics?.name == bear }
        assertEquals(1, mine.size)
        assertTrue(state.obj(mine.single())!!.tapped)
    }

    private fun Scenario.samuraiOf(player: ScenarioPlayer): Int = tokens("Samurai Token").count { state.obj(it)?.controller == player.id }
}
