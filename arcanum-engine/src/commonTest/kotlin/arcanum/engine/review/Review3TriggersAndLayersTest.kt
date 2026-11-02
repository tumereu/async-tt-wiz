package arcanum.engine.review

import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: triggers (per-turn limits with their own results, "that creature's controller" from last known
 * information, APNAP order, casting from elsewhere), priority after resolution, and layer interactions with type
 * changes and control.
 */
class Review3TriggersAndLayersTest {
    /** Elvish Drillmaster's token is itself an Elf entering: "this ability triggers only once each turn" stops a second
     * token. */
    // CR 603.2h
    @Test
    fun drillmastersOwnTokenDoesntTriggerItAgain() = scenario {
        alice.battlefield("Elvish Drillmaster")
        alice.hand("Elvish Seer")
        alice.castsWithoutCosts("Elvish Seer")
        resolve()
        resolveStack()
        expect { alice.battlefield.names.count { it == "Elf Warrior Token" } shouldBe 1 }
    }

    /** Fertile Passing: "that creature's controller may draw" is read from the creature as it last existed: bob
     * controlled alice's Elvish Seer when it died, so bob may draw. */
    // CR 603.10a, 608.2h
    @Test
    fun thatCreaturesControllerIsItsLastController() = scenario {
        alice.battlefield("Fertile Passing", "Elvish Seer")
        alice.hand("Storm Spark")
        bob.library("Island")
        alice.library("Forest")
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Elvish Seer"))
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        resolve()
        val may = decision<Decision.May>()
        assertEquals(bob.id, may.player)
        yes()
        expect {
            bob.hand has "Island"
            alice.hand.size shouldBe 0
        }
    }

    /** One death triggers alice's Crimson Mourner and bob's Fertile Passing: APNAP puts the active player's on the Stack
     * first, so bob's resolves first. */
    // CR 603.3b, 101.4
    @Test
    fun apnapOrderOfTwoPlayersTriggers() = scenario {
        alice.battlefield("Crimson Mourner")
        alice.hand("Storm Spark")
        bob.battlefield("Fertile Passing", "Elvish Seer")
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        expect { stack.names shouldBe listOf("ability of Crimson Mourner", "ability of Fertile Passing") }
    }

    /** After a spell resolves, the active player gets priority, not its caster (bob's instant in alice's turn). */
    // CR 117.3b
    @Test
    fun theActivePlayerGetsPriorityAfterResolution() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "alice")
        allPass()
        expect { alice.life shouldBe 17 }
        assertEquals(alice.id, state.priority.holder)
    }

    /** A spell cast with rebound from exile is cast from somewhere other than the hand: Plague Shamblers returns. */
    // CR 702.88a, 603.2
    @Test
    fun reboundCastTriggersPlagueShamblers() = scenario {
        alice.graveyard("Plague Shamblers")
        alice.battlefield("Plains", "Tinkered Mite")
        alice.hand("Fleeting Recall")
        alice.library("Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.casts("Fleeting Recall", target = "Tinkered Mite")
        allPass()
        expect { alice.exile has "Fleeting Recall" }
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        if (pending is Decision.May) yes()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Tinkered Mite"))))
        passUntilEmpty(mays = true)
        expect { alice.battlefield has "Plague Shamblers" }
    }

    /** Madness casts the card from exile: Plague Shamblers sees a spell cast from somewhere other than the hand. */
    // CR 702.35a, 603.2
    @Test
    fun madnessCastTriggersPlagueShamblers() = scenario {
        alice.graveyard("Plague Shamblers")
        alice.battlefield("Mountain", "Ghoul Outbreak")
        alice.hand("Sunning Mudlizard", "Island")
        alice.activates("Ghoul Outbreak", choosing = listOf("Sunning Mudlizard", "Island"))
        allPass()
        val madness = castOptions(alice, "Sunning Mudlizard").firstOrNull()
        if (madness != null) perform(madness) else if (pending is Decision.May) yes()
        passUntilEmpty(mays = true)
        expect {
            alice.battlefield has "Sunning Mudlizard"
            alice.battlefield has "Plague Shamblers"
        }
    }

    /** Glass Polymorph's X=3: alice's creatures have base 3/3 and every creature type, so Elvish Hierophant's anthem
     * (other Elves) reaches Tinkered Mite: 4/4. */
    // CR 613.1d, 613.4b, 613.4c, 702.73a
    @Test
    fun allCreatureTypesFeedAnElfLord() = scenario {
        alice.battlefield("Glass Polymorph", "Elvish Hierophant", "Tinkered Mite", "Island", "Island", "Island")
        alice.activates("Glass Polymorph", x = 3)
        allPass()
        expect {
            "Tinkered Mite".power shouldBe 4
            "Elvish Hierophant".power shouldBe 3
        }
    }

    /** A lord stolen by bob pumps bob's Elves, not alice's. */
    // CR 613.1b, 613.4c
    @Test
    fun aStolenLordPumpsItsNewControllersCreatures() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer")
        bob.battlefield("Thicketwood Elves")
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Elvish Hierophant"))
        expect {
            "Elvish Seer".power shouldBe 1
            "Thicketwood Elves".power shouldBe 2
        }
    }

    /** Rapid Retrofit makes Lord of the Aetherforge a noncreature Vehicle: its "other artifact creatures you control
     * get +1/+1" still applies. */
    // CR 613.1d, 205.1b
    @Test
    fun aRetrofittedLordStillPumps() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Tinkered Mite", "Rapid Retrofit")
        attach("Rapid Retrofit", "Lord of the Aetherforge")
        expect {
            "Lord of the Aetherforge".types.contains(arcanum.api.CardType.Creature) shouldBe false
            "Tinkered Mite".power shouldBe 2
        }
    }

    /** Ignia's −2 casts an instant from the graveyard: Plague Shamblers triggers (cast from anywhere other than the
     * hand), and the spell is exiled after. */
    // CR 601.2, 603.2
    @Test
    fun igniaCastFromTheGraveyardTriggersShamblers() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield("Ignia, Acolyte of Embers", counters = listOf(arcanum.api.CounterKind.Loyalty to 3))
        alice.graveyard("Plague Shamblers", "Storm Spark")
        alice.activates("Ignia, Acolyte of Embers", ability = 2, target = "Storm Spark")
        allPass()
        if (pending is Decision.May) yes()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        passUntilEmpty(mays = true)
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
            alice.battlefield has "Plague Shamblers"
        }
    }

    /** Verdant Hollowmere: "any player may cast creature spells with mana value 3 or less without paying their mana
     * costs and as though they had flash": bob casts his Elvish Seer free in alice's turn. */
    // CR 601.2, 702.8a
    @Test
    fun hollowmereLetsTheOpponentCastFree() = scenario {
        alice.battlefield("Verdant Hollowmere")
        bob.hand("Elvish Seer")
        alice.passes()
        val free = castOptions(bob, "Elvish Seer").firstOrNull { it.way.permission != null }
        assertTrue(free != null, "bob is offered the free cast")
        perform(free)
        allPass()
        expect { bob.battlefield has "Elvish Seer" }
    }

    /** Myriad-Faced Shade's token copy of a legendary attacker (Brakka) enters: the legend rule asks alice which to keep
     * (CR 704.5j), and one stays. */
    // CR 704.5j, 707.2
    @Test
    fun aLegendaryCopyAsksTheLegendRule() = scenario {
        alice.battlefield("Brakka, Wild Whelp", "Tinkered Mite", "Island", "Island", "Island", "Island")
        alice.hand("Myriad-Faced Shade")
        alice.library("Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Brakka, Wild Whelp", "Tinkered Mite")
        allPass()
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates("Myriad-Faced Shade", choosing = listOf("Tinkered Mite"))
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Brakka, Wild Whelp"))))
        passUntilEmpty(mays = true)
        if (pending is Decision.LegendRule) answer(Answer.Keep(decision<Decision.LegendRule>().among.first()))
        passUntilEmpty(mays = true)
        expect { alice.battlefield.names.count { it == "Brakka, Wild Whelp" } shouldBe 1 }
    }

    /** A card a permission lets bob cast from alice's graveyard isn't listed for alice, and a permission for one player
     * isn't usable by the other (Velmora's "once during each of your turns" is alice's). */
    // CR 601.2a
    @Test
    fun aPermissionIsItsHoldersOnly() = scenario {
        alice.battlefield("Velmora of the Sleep-Den")
        bob.graveyard("Elvish Seer")
        bob.battlefield("Forest")
        alice.graveyard("Tinkered Mite")
        assertTrue(castOptions(alice, "Tinkered Mite").isNotEmpty(), "alice may cast her Mite from her graveyard")
        alice.passes()
        assertTrue(castOptions(bob, "Elvish Seer").isEmpty(), "bob may not")
        val refused = refuses(Input.Cast(bob.id, card("Elvish Seer"), CastWay()))
        assertTrue(refused)
    }
}
