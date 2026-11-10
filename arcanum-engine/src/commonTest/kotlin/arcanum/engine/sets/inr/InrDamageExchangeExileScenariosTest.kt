package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** INR chunk 08: combat damage dealt again to each creature, a cast-only permission for a red exiled card, each Wolf dealing its own damage,
 * a type change that lets a control ability take the creature, "any opponent may", a life total exchanged with toughness, two linked exile
 * abilities, and a weakening of creatures attacking you. Invented names carry the oracle text of the real cards. */
class InrDamageExchangeExileScenariosTest {
    private val wyrm = Cards.invent("Pyreheart Wyrm", "{5}{R}{R}", "Creature — Dragon", """
        Flying
        Whenever this creature deals combat damage to a player, it deals that much damage to each creature that player controls.
    """.trimIndent(), "6/6")
    private val vessa = Cards.invent("Vessa, Flame Dancer", "{1}{R}{R}", "Legendary Planeswalker — Vessa", """
        [+1]: Add {R}. Vessa deals 1 damage to up to one target player or planeswalker.
        [+1]: Exile the top card of your library. If it's red, you may cast it this turn.
        [−7]: Exile the top five cards of your library. You may cast red spells from among them this turn. You get an emblem with "Whenever you cast a red spell, this emblem deals X damage to any target, where X is the amount of mana spent to cast that spell."
    """.trimIndent(), null)
    private val pursuit = Cards.invent("Lunar Pursuit", "{1}{G}", "Instant",
        "Choose target creature you don't control. Each creature you control that's a Wolf or a Werewolf deals damage equal to its power to that creature.", null)
    private val mirela = Cards.invent("Mirela Vonkarth", "{2}{B}{R}", "Legendary Creature — Vampire", """
        Flying
        {1}{R}: Mirela Vonkarth deals 1 damage to another target creature. That creature becomes a Vampire in addition to its other types. Put a +1/+1 counter on Mirela Vonkarth.
        {3}{B}{B}: Gain control of target Vampire for as long as you control Mirela Vonkarth.
    """.trimIndent(), "3/3")
    private val imp = Cards.invent("Taunting Imp", "{R}", "Creature — Devil",
        "When this creature enters, any opponent may have it deal 4 damage to them. If a player does, sacrifice this creature.", "4/3")
    private val oak = Cards.invent("Gallows Oak", "{3}{B}", "Creature — Plant", """
        Defender
        {T}: Exchange target opponent's life total with this creature's toughness.
    """.trimIndent(), "0/13")
    private val warden = Cards.invent("Ghoul Warden", "{1}{W}{W}", "Creature — Human Cleric", """
        When this creature enters, you may exile another target creature.
        When this creature leaves the battlefield, return the exiled card to the battlefield under its owner's control.
    """.trimIndent(), "1/3")
    private val pane = Cards.invent("Shuttered Pane", "{3}", "Artifact", """
        Creatures attacking you get -1/-0.
        At the beginning of each end step, if you were dealt 4 or more damage this turn, exile this artifact.
    """.trimIndent(), null)

    private val pup = Cards.invent("Cinder Pup", "{R}", "Creature — Elemental Dog", "", "1/1")
    private val hart = Cards.invent("Hollow Hart", "{2}{W}", "Creature — Elk", "", "3/3")
    private val brute = Cards.invent("Bog Brute", "{5}{B}", "Creature — Horror", "", "7/7")
    private val moonPup = Cards.invent("Moon Pup", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val howler = Cards.invent("Ridge Howler", "{2}{G}", "Creature — Human Werewolf", "", "3/3")
    private val wanderer = Cards.invent("Elm Wanderer", "{3}{G}", "Creature — Treefolk", "", "4/4")
    private val behemoth = Cards.invent("Stone Behemoth", "{4}{G}", "Creature — Giant", "", "5/5")
    private val sage = Cards.invent("Watchful Sage", "{1}{U}", "Creature — Human Wizard", "", "1/3")
    private val altar = Cards.invent("Gravel Altar", "{1}", "Artifact", "Sacrifice a creature: You gain 1 life.", null)
    private val ivra = Cards.invent("Ivra, Hill Seer", "{2}{G}", "Legendary Planeswalker — Ivra", "[+1]: You gain 1 life.", null)

    private fun Scenario.libraries() { players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") } }

    // CR 510.2: 6 combat damage to Bob, then 6 to each creature he controls (CR 120.3); the 3/3 dies, the 7/7 doesn't
    @Test
    fun wyrmDealsTheCombatDamageAgainToEachCreature() = scenario {
        libraries()
        alice.battlefield(wyrm)
        bob.battlefield(hart, brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wyrm, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 14
            bob.graveyard has hart
            bob.battlefield has brute
        }
    }

    // CR 601.2a, 305.9: the exiled red card may be cast this turn, from exile
    @Test
    fun vessaLetsYouCastTheExiledRedCard() = scenario {
        alice.battlefield(vessa, counters = listOf(CounterKind.Loyalty to 3))
        alice.battlefield("Mountain")
        alice.library(pup, "Island")
        bob.library("Island")
        alice.activates(vessa, ability = 1)
        resolveAll()
        expect { alice.exile has pup }
        alice.casts(pup)
        resolveAll()
        expect { alice.battlefield has pup }
    }

    // CR 120.3: the Wolf and the Werewolf each deal their own power, 2 + 3, all at once; the Treefolk deals none
    @Test
    fun pursuitHasEachWolfAndWerewolfDealItsPower() = scenario {
        alice.battlefield(moonPup, howler, wanderer, "Forest", "Forest")
        alice.hand(pursuit)
        bob.battlefield(behemoth)
        alice.casts(pursuit, target = behemoth)
        resolveAll()
        expect { bob.graveyard has behemoth }
    }

    // CR 205.1b, 613.1d: the creature becomes a Vampire for good, so the second ability can take it (CR 611.2b)
    @Test
    fun mirelaMakesAVampireAndTakesIt() = scenario {
        alice.battlefield(mirela, "Mountain", "Mountain", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(sage)
        alice.activates(mirela, ability = 0, target = sage, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        expect { mirela.power shouldBe 4 }
        alice.activates(mirela, ability = 1, target = sage)
        resolveAll()
        expect { sage.controller shouldBe alice.id }
    }

    // CR 101.4: Bob, then Carol, may have it deal the damage; Carol does, so it deals 4 to her and is sacrificed (CR 608.2c)
    @Test
    fun impAsksEachOpponentInTurnOrderUntilOneAccepts() = scenario(players = 3) {
        libraries()
        alice.battlefield("Mountain")
        alice.hand(imp)
        alice.casts(imp)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        assertEquals(players[2].id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 20
            players[2].life shouldBe 16
            alice.graveyard has imp
        }
    }

    @Test
    fun impStaysWhenNoOpponentAccepts() = scenario(players = 3) {
        libraries()
        alice.battlefield("Mountain")
        alice.hand(imp)
        alice.casts(imp)
        resolveAll()
        answer(Answer.YesNo(false))
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.battlefield has imp
            bob.life shouldBe 20
            players[2].life shouldBe 20
        }
    }

    // CR 701.12g: Bob's life becomes 13 and the toughness becomes his old 20 (layer 7b, CR 613.4b)
    @Test
    fun oakExchangesBobsLifeWithItsToughness() = scenario {
        alice.battlefield(oak)
        alice.activates(oak, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 13
            oak.toughness shouldBe 20
            oak.power shouldBe 0
        }
    }

    // CR 607.2a: the leaves ability returns the card the enters ability exiled, under its owner's control
    @Test
    fun wardenReturnsTheExiledCreatureWhenItLeaves() = scenario {
        alice.battlefield(altar, "Plains", "Plains", "Plains")
        alice.hand(warden)
        bob.battlefield(hart)
        alice.casts(warden)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect { bob.exile has hart }
        alice.activates(altar, choosing = listOf(warden))
        resolveAll()
        expect {
            bob.battlefield has hart
            hart.controller shouldBe bob.id
        }
    }

    // CR 603.3b, 607.2a: the warden leaves before its enters ability resolves; the leaves ability returns nothing, then the creature is exiled
    // for good (two abilities, not an "until" exile)
    @Test
    fun wardenLeavingFirstExilesTheCreatureForGood() = scenario {
        alice.battlefield(altar, "Plains", "Plains", "Plains")
        alice.hand(warden)
        bob.battlefield(hart)
        alice.casts(warden)
        resolve()
        alice.activates(altar, choosing = listOf(warden))
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.exile has hart
            alice.graveyard has warden
        }
    }

    // CR 506.3: the creature attacking Alice gets -1/-0, the one attacking her planeswalker doesn't; CR 603.4: 4 damage to her, so the pane
    // is exiled at the end step
    @Test
    fun paneWeakensCreaturesAttackingYouAndLeavesAfterFourDamage() = scenario {
        libraries()
        alice.battlefield(pane)
        alice.battlefield(ivra, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(behemoth, hart)
        turn(active = bob, step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(bob.id, listOf(
            Attack(card(behemoth), TargetRef.Player(alice.id)),
            Attack(card(hart), TargetRef.Object(card(ivra))))))
        expect {
            behemoth.power shouldBe 4
            hart.power shouldBe 3
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.life shouldBe 16
            alice.exile has pane
        }
    }
}
