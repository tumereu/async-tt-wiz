package arcanum.engine.review

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Block
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Review 2: protection (DEBT: damage, enchanting/equipping, blocking, targeting, CR 702.16), ward and hexproof
 * (CR 702.21, 702.11), indestructible and umbra armor (CR 702.12, 702.89) — what "dies" and "destroy" mean around them.
 */
class Review2ProtectionTest {
    private val proRed = Cards.invent("Review Ember Warden", "{1}{W}", "Creature — Human Knight", "Protection from red", "2/2")
    private val hexproof = Cards.invent("Review Veiled Stalker", "{1}{G}", "Creature — Elf Scout", "Hexproof", "2/2")
    private val indestructible = Cards.invent("Review Stoneward", "{2}{W}", "Creature — Golem", "Indestructible", "2/2")
    private val trampler = Cards.invent("Review Cinder Brute", "{3}{R}", "Creature — Beast", "Trample", "4/4")
    private val umbra = Cards.invent("Review Mossy Umbra", "{1}{G}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.\nUmbra armor")

    // ---- Protection

    /** Damage from a red source that doesn't target (Blazing Confluence's "1 damage to each creature") is prevented. */
    // CR 702.16e
    @Test
    fun protectionPreventsDamageWithoutTargeting() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Blazing Confluence")
        bob.battlefield(proRed, "Elvish Seer")
        alice.casts("Blazing Confluence", modes = listOf(0, 0, 0))
        allPass()
        expect {
            proRed.zone shouldBe arcanum.api.Zone.Battlefield
            bob.graveyard has "Elvish Seer"
        }
        assertEquals(0, state.objOrThrow(card(proRed)).damage)
    }

    /** Protection from red: Storm Spark can't target it, and options don't offer it as a target. */
    // CR 702.16b
    @Test
    fun protectionFromRedCantBeTargetedByRed() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(proRed)
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertTrue(TargetRef.Object(card(proRed)) !in decision<Decision.Targets>().options)
    }

    /** An attacker with protection from red can't be blocked by red creatures. */
    // CR 702.16f
    @Test
    fun protectionFromRedCantBeBlockedByRed() = scenario {
        alice.battlefield(proRed)
        bob.battlefield("Brakka, Wild Whelp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(proRed)
        allPass()
        assertTrue(Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>().none { it.blocks.isNotEmpty() })
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card("Brakka, Wild Whelp"), card(proRed)))))
        }
    }

    /** A creature that gains protection from green loses its green Aura (state-based action). */
    // CR 702.16c, 704.5m
    @Test
    fun aGreenAuraFallsOffProtectionFromGreen() = scenario {
        alice.battlefield("Bestower of Sigils", "Elvish Seer", umbra)
        attach(umbra, "Elvish Seer")
        alice.activates("Bestower of Sigils", target = "Elvish Seer")
        allPass()
        answer(Answer.Option("a color"))
        answer(Answer.ColorChoice(Color.Green))
        expect { alice.graveyard has umbra }
    }

    /** Protection from colorless unattaches a colorless Equipment; the Equipment stays on the battlefield. */
    // CR 702.16d, 704.5n
    @Ignore("review2 #12: Equipment stays attached to a creature with protection from it")
    @Test
    fun equipmentFallsOffProtectionFromColorless() = scenario {
        alice.battlefield("Bestower of Sigils", "Brakka, Wild Whelp", "Bonevise")
        attach("Bonevise", "Brakka, Wild Whelp")
        alice.activates("Bestower of Sigils", target = "Brakka, Wild Whelp")
        allPass()
        answer(Answer.Option(decision<Decision.ChooseOption>().options.first { "colorless" in it.lowercase() }))
        expect { "Bonevise".zone shouldBe arcanum.api.Zone.Battlefield }
        assertNull(state.objOrThrow(card("Bonevise")).attachedTo)
    }

    /** Shalecoil Wyrm's protection from multicolored: Sudden Rot ({B}{G}) can't target it. */
    // CR 702.16b, 105.1
    @Test
    fun protectionFromMulticolored() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand("Sudden Rot")
        bob.battlefield("Shalecoil Wyrm", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        // Its only possible target has protection: Sudden Rot can't be cast
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card("Sudden Rot") })
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Cast(alice.id, card("Sudden Rot"))) }
    }

    /** "Destroy each" doesn't target: an overloaded Curse Word destroys a creature with protection (from red here);
     * protection stops targeting and damage, not effects like this. */
    // CR 702.16, 702.96b
    @Test
    fun overloadDestroysAProtectedCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        alice.hand("Curse Word")
        bob.battlefield(proRed)
        val overload = view()[card("Curse Word")]!!.characteristics!!.abilities
            .first { (it.ability as? arcanum.api.Ability.Static)?.mechanics?.any { m -> m is arcanum.api.Mechanic.AlternativeCost } == true }
        alice.casts("Curse Word", way = CastWay(alternative = overload.origin))
        allPass()
        expect { bob.graveyard has proRed }
    }

    /** A trampler blocked by a creature with protection from its colour assigns lethal damage to the blocker (where
     * it is prevented) and the rest to the player. */
    // CR 702.19c, 702.16e
    @Test
    fun trampleOverAProtectedBlocker() = scenario {
        alice.battlefield(trampler)
        bob.battlefield(proRed)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(trampler)
        allPass()
        bob.blocks(proRed to trampler)
        allPass()
        if (pending is Decision.Divide) divideAsSuggested()
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 18
            proRed.zone shouldBe arcanum.api.Zone.Battlefield
        }
    }

    // ---- Ward and hexproof

    /** Ward triggers on an activated ability targeting the permanent too (Cinder Spellcharge), and counters it unless
     * its controller pays. */
    // CR 702.21a
    @Test
    fun wardCountersAnAbility() = scenario {
        alice.battlefield("Cinder Spellcharge", "Mountain")
        bob.battlefield("Scrapquilt Automaton")
        alice.activates("Cinder Spellcharge", target = "Scrapquilt Automaton")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        expect { bob.battlefield has "Scrapquilt Automaton" }
    }

    /** Ward doesn't trigger on its controller's own spells. */
    // CR 702.21a
    @Test
    fun wardIgnoresItsControllersSpells() = scenario {
        bob.battlefield("Scrapquilt Automaton", "Island", "Island")
        bob.hand("Click Back")
        turn(number = 2, active = bob)
        bob.casts("Click Back", target = "Scrapquilt Automaton")
        expect { stack.size shouldBe 1 }
        allPass()
        expect { bob.hand has "Scrapquilt Automaton" }
    }

    /** A copy of a spell whose new target is a ward creature makes ward trigger: it "becomes the target". */
    // CR 702.21a, 707.10c
    @Test
    fun wardTriggersOnACopysNewTarget() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flash of Mirroring")
        bob.battlefield("Scrapquilt Automaton")
        alice.casts("Storm Spark", target = "bob")
        alice.casts("Flash of Mirroring", target = "Storm Spark")
        allPass()
        // The copy: choose a new target
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Scrapquilt Automaton"))))
        expect { stack.names.any { it?.startsWith("ability of Scrapquilt") == true } shouldBe true }
    }

    /** Hexproof: the opponent can't target it, its controller can. */
    // CR 702.11b
    @Test
    fun hexproofOnlyAgainstOpponents() = scenario {
        alice.battlefield("Island", "Island", "Elvish Seer")
        alice.hand("Click Back")
        bob.battlefield(hexproof, "Island", "Island")
        bob.hand("Click Back")
        perform(Input.Cast(alice.id, card("Click Back", owner = alice)))
        assertTrue(TargetRef.Object(card(hexproof)) !in decision<Decision.Targets>().options)
        perform(Input.Cancel(alice.id))
        alice.passes()
        perform(Input.Cast(bob.id, card("Click Back", owner = bob)))
        assertTrue(TargetRef.Object(card(hexproof)) in decision<Decision.Targets>().options)
    }

    /** Shroud in response (Mossbound Keeper sacrificing a land) makes the spell's only target illegal: it doesn't
     * resolve. */
    // CR 702.18a, 608.2b
    @Test
    fun shroudInResponse() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Mossbound Keeper", "Forest")
        alice.casts("Storm Spark", target = "Mossbound Keeper")
        alice.passes()
        bob.activates("Mossbound Keeper", target = "Mossbound Keeper", choosing = listOf("Forest"))
        allPass()
        allPass()
        expect {
            bob.battlefield has "Mossbound Keeper"
            alice.graveyard has "Storm Spark"
        }
    }

    // ---- Indestructible and umbra armor

    /** Indestructible survives lethal damage but not 0 toughness. */
    // CR 702.12b, 704.5f, 704.5g
    @Test
    fun indestructibleAndZeroToughness() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp", "Swamp")
        alice.hand("Storm Spark", "Sever Limb")
        bob.battlefield(indestructible)
        alice.casts("Storm Spark", target = indestructible)
        allPass()
        expect { bob.battlefield has indestructible }
        alice.casts("Sever Limb", target = indestructible)
        allPass()
        expect { bob.graveyard has indestructible }
    }

    /** An indestructible creature can be sacrificed (Bone Splinters' cost), and then it dies. */
    // CR 701.21a, 702.12b
    @Test
    fun indestructibleCanBeSacrificed() = scenario {
        alice.battlefield("Swamp", indestructible)
        alice.hand("Bone Splinters")
        bob.battlefield("Elvish Seer")
        alice.casts("Bone Splinters", target = "Elvish Seer", option = "Sacrifice", choosing = listOf(indestructible))
        expect { alice.graveyard has indestructible }
    }

    /** Umbra armor: instead of being destroyed, the creature loses all damage and the Aura is destroyed; "can't be
     * regenerated" doesn't stop it. */
    // CR 702.89a
    @Ignore("review2 #10: umbra armor (like 100+ other keywords without an expansion) silently does nothing")
    @Test
    fun umbraArmorSavesFromCurseWord() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Curse Word")
        bob.battlefield("Brakka, Wild Whelp", umbra)
        attach(umbra, "Brakka, Wild Whelp")
        alice.casts("Curse Word", target = "Brakka, Wild Whelp")
        allPass()
        expect {
            bob.battlefield has "Brakka, Wild Whelp"
            bob.graveyard has umbra
        }
    }

    /** Umbra armor against lethal damage: the state-based action's destruction is replaced, damage is removed. */
    // CR 702.89a, 704.5g
    @Ignore("review2 #10: umbra armor (like 100+ other keywords without an expansion) silently does nothing")
    @Test
    fun umbraArmorSavesFromLethalDamage() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Brakka, Wild Whelp", umbra)
        attach(umbra, "Brakka, Wild Whelp")
        alice.casts("Storm Spark", target = "Brakka, Wild Whelp")
        allPass()
        expect {
            bob.battlefield has "Brakka, Wild Whelp"
            bob.graveyard has umbra
        }
        assertEquals(0, state.objOrThrow(card("Brakka, Wild Whelp")).damage)
    }

    /** Umbra armor doesn't help against 0 toughness: that isn't destruction. */
    // CR 702.89a, 704.5f
    @Test
    fun umbraArmorDoesntSaveFromZeroToughness() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand("Sever Limb")
        bob.battlefield("Brakka, Wild Whelp", umbra)
        attach(umbra, "Brakka, Wild Whelp")
        alice.casts("Sever Limb", target = "Brakka, Wild Whelp")
        allPass()
        expect { bob.graveyard has "Brakka, Wild Whelp" }
    }
}
