package arcanum.engine.sets.dmr

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFails

/** Dominaria Remastered chunk 12 in play: damage triggers, a delayed return, destroy, discarding hands and drawing. */
class DmrChunk12DamageDelayedDestroyDiscardDrawScenariosTest {
    private val menders = Cards.invent("Hollow Menders", "{2}{R}", "Creature — Goblin Shaman",
        "Whenever this creature becomes tapped, it deals 1 damage to any target.", "1/1")
    private val touch = Cards.invent("Numbing Touch", "{U}", "Instant", "Tap target permanent.")
    private val pashar = Cards.invent("Pashar Hill", "{2}{R}", "Legendary Creature — Goblin Warrior", """
        Whenever Pashar Hill or another Goblin you control dies, Pashar Hill deals 1 damage to any target.
        {3}{R}, Sacrifice a Goblin: Create two 1/1 red Goblin creature tokens.
    """, "2/2")
    private val grub = Cards.invent("Grub Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val snare = Cards.invent("Gutter Snare", "{B}", "Instant", "Destroy target creature.")
    private val sandwind = Cards.invent("Sandwind", "{G}", "Instant", "Sandwind deals 1 damage to each attacking creature.")
    private val tiana = Cards.invent("Tiana, Hull Keeper", "{3}{R}{W}", "Legendary Creature — Angel Artificer", """
        Flying, first strike
        Whenever an Aura or Equipment you control is put into a graveyard from the battlefield, you may return that card to its owner's hand at the beginning of the next end step.
    """, "3/3")
    private val blade = Cards.invent("Rusty Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+0.")
    private val shatter = Cards.invent("Shatter Bolt", "{R}", "Instant", "Destroy target artifact.")
    private val noPity = Cards.invent("No Pity", "{2}{B}{B}", "Enchantment", "Whenever a creature deals damage to you, destroy it.")
    private val ox = Cards.invent("Dun Ox", "{2}", "Creature — Ox", "", "2/2")
    private val mindrender = Cards.invent("Mindrender", "{2}{B}{B}", "Creature — Horror", "When this creature dies, each player discards their hand.", "4/3")
    private val dreadMark = Cards.invent("Dread Mark", "{1}{B}", "Instant", "Destroy target nonartifact, nonblack creature. It can't be regenerated.")
    private val regenerator = Cards.invent("Reknit Boar", "{2}{G}", "Creature — Boar", "{G}: Regenerate this creature.", "2/2")
    private val golem = Cards.invent("Tin Golem", "{3}", "Artifact Creature — Golem", "", "2/2")
    private val ghoul = Cards.invent("Dusk Ghoul", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val paleGhoul = Cards.invent("Pale Ghoul", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val reader = Cards.invent("Phyrexian Reader", "{2}{B}", "Creature — Phyrexian Horror",
        "When this creature enters, you draw a card and you lose 1 life.", "2/2")
    private val stroke = Cards.invent("Stroke of Insight", "{X}{2}{U}", "Instant", "Target player draws X cards.")
    private val seer = Cards.invent("Mesa Seer", "{1}{W}{W}", "Creature — Human Druid", "Whenever you cast an enchantment spell, you may draw a card.", "0/2")
    private val glade = Cards.invent("Hushed Glade", "{W}", "Enchantment", "")
    private val cart = Cards.invent("Rotted Catapult", "{2}{R}", "Enchantment", "{R}, Sacrifice a Zombie: This enchantment deals 2 damage to any target.")

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 701.26b, 603.2: becoming tapped (here by another spell) triggers; CR 120.3
    @Test
    fun mendersDamageWhenTapped() = scenario {
        alice.battlefield(menders, "Island")
        alice.hand(touch)
        alice.casts(touch, target = menders)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 603.10a, 700.4: another Goblin you control dying triggers it; 3 players, the damage goes where chosen
    @Test
    fun pasharDamagesWhenAnotherGoblinDies() = scenario(players = 3) {
        alice.battlefield(pashar, grub, "Swamp")
        alice.hand(snare)
        alice.casts(snare, target = grub)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        resolveAll()
        expect {
            alice.graveyard has grub
            carol.life shouldBe 19
            bob.life shouldBe 20
        }
    }

    // CR 700.4: "Goblin you control": an opponent's Goblin dying doesn't trigger it
    @Test
    fun pasharIgnoresAnOpponentsGoblin() = scenario(players = 3) {
        alice.battlefield(pashar, "Swamp")
        bob.battlefield(grub)
        alice.hand(snare)
        alice.casts(snare, target = grub)
        resolveAll()
        expect {
            bob.graveyard has grub
            bob.life shouldBe 20
            carol.life shouldBe 20
            check(pending !is Decision.Targets)
        }
    }

    // CR 701.21a, 602.2: sacrificing a Goblin (here Pashar itself) as a cost makes two tokens; its own dies trigger fires too
    @Test
    fun pasharSacrificesAGoblinForTwoTokens() = scenario {
        alice.battlefield(pashar, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(pashar, choosing = listOf("Pashar Hill"))
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            alice.graveyard has pashar
            bob.life shouldBe 19
        }
        check(count("Goblin Token") == 2)
    }

    // CR 120.3, 508.1: the attacking 1/1 dies; the 2/2 that stays home is untouched
    @Test
    fun sandwindHitsOnlyAttackers() = scenario {
        alice.battlefield("Forest")
        alice.hand(sandwind)
        bob.battlefield(grub, ox)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks("Grub Goblin", defender = "alice")
        bob.passes()
        alice.casts(sandwind)
        resolveAll()
        expect {
            bob.graveyard has grub
            bob.battlefield has ox
        }
    }

    // CR 700.4, 603.7c, 603.5: an Equipment destroyed goes to the graveyard; at the next end step it may return to its owner's hand
    @Test
    fun tianaReturnsTheEquipmentAtTheEndStep() = scenario {
        alice.battlefield(tiana, blade)
        bob.battlefield("Mountain")
        bob.hand(shatter)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = bob)
        bob.casts(shatter, target = blade)
        resolveAll()
        expect { alice.graveyard has blade }
        advanceTo(TurnStep.End)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect { alice.hand has blade }
    }

    // CR 603.5: declining the may leaves the Equipment in the graveyard
    @Test
    fun tianaMayLeaveTheEquipmentInTheGraveyard() = scenario {
        alice.battlefield(tiana, blade)
        bob.battlefield("Mountain")
        bob.hand(shatter)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = bob)
        bob.casts(shatter, target = blade)
        resolveAll()
        advanceTo(TurnStep.End)
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.graveyard has blade
            alice.hand lacks blade
        }
    }

    // CR 510.2, 700.4: the creature that dealt combat damage to you is destroyed
    @Test
    fun noPityDestroysTheCreatureThatDamagedYou() = scenario {
        alice.battlefield(noPity)
        bob.battlefield(ox)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks("Dun Ox", defender = "alice")
        advanceTo(TurnStep.End)
        allPass()
        resolveAll()
        expect {
            alice.life shouldBe 18
            bob.graveyard has ox
        }
    }

    // CR 701.9a, 400.7: each of three players discards the whole hand, not one card
    @Test
    fun mindrenderMakesEveryPlayerDiscardTheirHand() = scenario(players = 3) {
        alice.battlefield(mindrender, "Swamp")
        alice.hand(snare, "Island", "Island")
        bob.hand("Forest", "Forest", "Forest")
        carol.hand("Mountain")
        alice.casts(snare, target = mindrender)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            bob.hand.size shouldBe 0
            carol.hand.size shouldBe 0
            alice.graveyard has "Island"
            bob.graveyard has "Forest"
        }
    }

    // CR 701.8a, 701.19c: the creature is destroyed even though it could regenerate
    @Test
    fun dreadMarkCantBeRegenerated() = scenario {
        alice.hand(dreadMark)
        alice.battlefield("Swamp", "Swamp")
        bob.battlefield(regenerator, "Forest")
        alice.casts(dreadMark, target = regenerator)
        alice.passes()
        bob.activates(regenerator)
        resolveAll()
        expect { bob.graveyard has regenerator }
    }

    // CR 115.4, 205.3: artifact creatures and black creatures aren't legal targets
    @Test
    fun dreadMarkNeedsANonartifactNonblackCreature() = scenario {
        alice.hand(dreadMark)
        alice.battlefield("Swamp", "Swamp")
        bob.battlefield(golem, ghoul)
        assertFails { alice.casts(dreadMark, target = golem) }
    }

    // CR 121.1, 119.3: both parts of "you draw a card and you lose 1 life" happen
    @Test
    fun readerDrawsAndLosesLife() = scenario {
        alice.hand(reader)
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.library("Island")
        alice.casts(reader)
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.life shouldBe 19
        }
    }

    // CR 107.3a, 601.2b: X is announced; the target player draws that many (3 players: only the chosen one draws)
    @Test
    fun strokeDrawsXForTheTargetPlayer() = scenario(players = 3) {
        alice.hand(stroke)
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        bob.library("Forest", "Forest", "Forest", "Forest")
        alice.casts(stroke, target = "bob", x = 3)
        resolveAll()
        expect {
            bob.hand.size shouldBe 3
            carol.hand.size shouldBe 0
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.2, 601.2i: the trigger fires for an enchantment spell and the "may" decides
    @Test
    fun seerDrawsWhenAnEnchantmentIsCast() = scenario {
        alice.battlefield(seer, "Plains")
        alice.hand(glade)
        alice.library("Island")
        alice.casts(glade)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 603.5: declining draws nothing
    @Test
    fun seerMayDeclineToDraw() = scenario {
        alice.battlefield(seer, "Plains")
        alice.hand(glade)
        alice.library("Island")
        alice.casts(glade)
        resolveAll()
        check(pending is Decision.May)
        answer(Answer.YesNo(false))
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 118.8, 701.21a: the Zombie sacrificed must be one you control; the opponent's is not offered
    @Test
    fun catapultSacrificesOnlyYourOwnZombie() = scenario {
        alice.battlefield(cart, ghoul, "Mountain")
        bob.battlefield(paleGhoul)
        alice.activates(cart, target = "bob")
        resolveAll()
        expect {
            alice.graveyard has ghoul
            bob.life shouldBe 18
            bob.battlefield has "Pale Ghoul"
        }
    }
}
