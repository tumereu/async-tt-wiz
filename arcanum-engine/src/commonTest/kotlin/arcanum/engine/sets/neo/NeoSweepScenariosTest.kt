package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** NEO sweep: siblings of the engine and parser fixes NEO's cards needed, played on other paths (spells, copies, reflexive triggers, statics,
 * 3+ players) before a card finds them. */
class NeoSweepScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Resolves the Stack, saying yes to each "you may", paying as suggested, choosing [x] for a number and [targets] in order. */
    private fun Scenario.drive(x: Int = 0, vararg targets: String) {
        val left = targets.toMutableList()
        var guard = 80
        while (guard-- > 0) {
            when (val d = pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.ChooseNumber -> answer(Answer.Number(x))
                is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
                is Decision.Targets -> answer(Answer.Targets(listOf(target(left.removeAt(0)))))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.map { it.index }))
                null -> if (state.stack.isEmpty()) return else allPass()
                else -> return
            }
        }
    }

    // ---- per-event "next time" shield (Storyweave fix: CR 614.1c, 616.1) on the paths the fix didn't play

    private val weave = Cards.invent("Neo Sweep Weave", "{0}", "Instant",
        "The next time one or more enchantment creatures you control enter this turn, each enters with two additional +1/+1 counters on it.")
    private val dryad = Cards.invent("Neo Sweep Dryad", "{G}", "Enchantment Creature — Dryad", "", "1/1")
    private val nymph = Cards.invent("Neo Sweep Nymph", "{G}", "Enchantment Creature — Nymph", "", "2/2")
    private val mirror = Cards.invent("Neo Sweep Mirror Rite", "{0}", "Sorcery", "Create a token that's a copy of target creature you control.")

    // CR 614.1c, 616.1: a creature spell resolving is the next such event: it gets the counters; the next spell's creature gets none
    @Test
    fun weaveShieldReachesACreatureSpellOnceOnly() = scenario {
        alice.hand(weave, dryad, nymph)
        alice.battlefield("Forest", "Forest")
        alice.casts(weave)
        resolveAll()
        alice.casts(dryad)
        resolveAll()
        alice.casts(nymph)
        resolveAll()
        expect {
            dryad.counters(p1p1) shouldBe 2
            nymph.counters(p1p1) shouldBe 0
        }
    }

    // CR 614.1c, 616.1, 707.2: a token copy of an enchantment creature entering is such an event: it gets the counters once
    @Test
    fun weaveShieldReachesATokenCopy() = scenario {
        alice.battlefield(dryad)
        alice.hand(weave, mirror)
        alice.casts(weave)
        resolveAll()
        val original = card(dryad)
        alice.casts(mirror, target = dryad)
        resolveAll()
        val copy = tokens(dryad).single()
        assertEquals(listOf(2, 0), listOf(copy, original).map { id -> state.obj(id)!!.counters.filter { it.kind == p1p1 }.sumOf { it.count } })
    }

    // ---- a filter on the spell a card would become (resulting-spell fix: CR 601.2a) in a static permission

    private val necro = Cards.invent("Neo Sweep Grave Muse", "{2}{B}", "Enchantment", "You may cast creature spells from your graveyard.")
    private val ogre = Cards.invent("Neo Sweep Ogre", "{1}", "Creature — Ogre", "", "2/2")
    private val zap = Cards.invent("Neo Sweep Zap", "{0}", "Instant", "Neo Sweep Zap deals 3 damage to any target.")

    // CR 601.2a, 601.3: the card becomes a creature spell as it is cast, so "creature spells" lets a creature card in the graveyard be cast
    // and nothing else
    @Test
    fun creatureSpellPermissionFromTheGraveyard() = scenario {
        alice.battlefield(necro, "Swamp")
        alice.graveyard(ogre, zap)
        val options = Engine.options(state, alice.id)
        assertTrue(options.any { it is Input.Cast && it.card == card(ogre) }, "the creature card can be cast")
        assertTrue(options.none { it is Input.Cast && it.card == card(zap) }, "the instant can't")
    }

    // ---- a reflexive trigger keeps the trigger's "it" (chunk 07 fix: CR 603.12, 608.2c) on other reflexive readings

    private val patron = Cards.invent("Neo Sweep Patron", "{1}{G}", "Creature — Human",
        "Whenever another creature you control enters, you may pay {X}. When you do, put X +1/+1 counters on it.", "1/1")
    private val keeper = Cards.invent("Neo Sweep Grave Keeper", "{1}{B}", "Creature — Human",
        "Whenever another creature you control dies, you may pay {1}. When you do, return it to its owner's hand.", "1/1")

    // CR 603.12, 608.2c, 107.3: X paid as the trigger resolves; "it" is the creature that entered
    @Test
    fun payXReflexiveTriggerPutsCountersOnTheEnteringCreature() = scenario {
        alice.battlefield(patron, "Forest", "Forest", "Forest")
        alice.hand(ogre)
        alice.casts(ogre)
        drive(x = 2)
        expect {
            ogre.counters(p1p1) shouldBe 2
            patron.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.12, 603.10a, 400.7: "it" in the reflexive trigger of a dies trigger is the card it became in the graveyard
    @Test
    fun diesReflexiveTriggerReturnsTheCardThatDied() = scenario {
        alice.battlefield(keeper, ogre, "Swamp")
        alice.hand(zap)
        alice.casts(zap, target = ogre)
        drive()
        expect {
            alice.hand has ogre
            alice.battlefield has keeper
        }
    }

    // ---- defending player of a becomes-blocked trigger (chunk 04 fix) for creatures attacking different players

    private val drummer = Cards.invent("Neo Sweep War Drummer", "{2}{R}", "Creature — Goblin",
        "Whenever a creature you control becomes blocked, defending player loses 1 life.", "1/1")
    private val raider = Cards.invent("Neo Sweep Raider", "{1}{R}", "Creature — Goblin", "", "2/2")
    private val wall = Cards.invent("Neo Sweep Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    private val wall2 = Cards.invent("Neo Sweep Other Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")

    // CR 506.2, 509.1h, 603.2: each trigger's defending player is the player the blocked creature attacked
    @Test
    fun becomesBlockedDefendingPlayerPerAttacker() = scenario(players = 3) {
        alice.battlefield(drummer, ogre, raider)
        bob.battlefield(wall)
        carol.battlefield(wall2)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(ogre), TargetRef.Player(bob.id)), Attack(card(raider), TargetRef.Player(carol.id)))))
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to ogre)
        carol.blocks(wall2 to raider)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    // ---- cards exiled "with" an object (chunk 07 fix: CR 607.2a) by its other kinds of abilities

    private val vault = Cards.invent("Neo Sweep Vault", "{2}", "Artifact", """
        When this artifact enters, you may pay {1}. When you do, exile target card from a graveyard.
        {T}: Put a card exiled with this artifact into its owner's hand.
    """)
    // CR 607.2a, 603.12: the card exiled by the reflexive trigger of its enters ability is exiled with it
    @Test
    fun cardExiledByAReflexiveTriggerIsExiledWithIt() = scenario {
        alice.hand(vault)
        alice.battlefield("Plains", "Plains", "Plains")
        bob.graveyard(ogre)
        alice.casts(vault)
        drive(0, ogre)
        expect { bob.exile has ogre }
        alice.activates(vault)
        resolveAll()
        expect { bob.hand has ogre }
    }

    // ---- "it" after a condition about the attached permanent (chunk 01/08 fixes: CR 301.5a, 303.4e) on Equipment and other effects

    private val blade = Cards.invent("Neo Sweep Blade", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        As long as equipped creature is a Samurai, it has first strike.
        Equip {1}
    """)
    private val samurai = Cards.invent("Neo Sweep Samurai", "{1}{W}", "Creature — Human Samurai", "", "2/2")
    private val veil = Cards.invent("Neo Sweep Veil", "{U}", "Enchantment — Aura", """
        Enchant creature
        As long as enchanted creature is a Human, it can't be blocked.
    """)

    // CR 301.5a, 611.3a: "it" is the equipped creature
    @Test
    fun equipmentConditionGrantsTheEquippedCreature() = scenario {
        alice.battlefield(blade, samurai)
        attach(blade, samurai)
        expect { samurai hasKeyword Keyword.FirstStrike.kind }
    }

    // CR 303.4e, 509.1b: "it" is the enchanted creature, which can't be blocked
    @Test
    fun auraConditionMakesTheEnchantedCreatureUnblockable() = scenario {
        alice.battlefield(veil, samurai)
        bob.battlefield(wall)
        attach(veil, samurai)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(samurai)
        advanceTo(TurnStep.DeclareBlockers)
        assertTrue(runCatching { bob.blocks(wall to samurai) }.isFailure, "the enchanted Human can't be blocked")
    }
}
