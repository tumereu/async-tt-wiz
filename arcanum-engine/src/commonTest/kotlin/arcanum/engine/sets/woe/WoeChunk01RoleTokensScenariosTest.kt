package arcanum.engine.sets.woe

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/**
 * WOE chunk 01, Role tokens (CR 111.10j–r, 303.7a): the invented names carry the oracle text of Monstrous Rage, Eriette's Whisper,
 * Asinine Antics, Embereth Veteran, Living Lectern, Charmed Clothier, Merry Bards and Lord Skitter's Blessing.
 */
class WoeChunk01RoleTokensScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val cub = Cards.invent("Thistledown Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val otherCub = Cards.invent("Gorsewood Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Furrowfield Ox", "{2}{G}", "Creature — Ox", "", "3/4")
    /** Not a predefined Role: an Aura Role card, to give a Role to another player or set one up. */
    private val mantle = Cards.invent("Borrowed Mantle", "{W}", "Enchantment — Aura Role", "Enchant creature\nEnchanted creature gets +1/+1.")

    private val temper = Cards.invent("Beastly Temper", "{R}", "Instant",
        "Target creature gets +2/+0 until end of turn. Create a Monster Role token attached to it. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample.)")
    private val murmur = Cards.invent("Witch's Murmur", "{3}{B}", "Sorcery",
        "Target opponent discards two cards. Create a Wicked Role token attached to up to one target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)")
    private val frolics = Cards.invent("Foolish Frolics", "{2}{U}{U}", "Sorcery", """
        You may cast this spell as though it had flash if you pay {2} more to cast it.
        For each creature your opponents control, create a Cursed Role token attached to that creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature is 1/1.)
    """)
    private val oldTimer = Cards.invent("Cinderhold Old-Timer", "{R}", "Creature — Human Knight",
        "{1}, Sacrifice this creature: Create a Young Hero Role token attached to another target creature. (If you control another Role on it, put that one into the graveyard. Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\")", "2/1")
    private val podium = Cards.invent("Walking Podium", "{1}{U}", "Artifact Creature — Construct",
        "{1}, Sacrifice this creature: Draw a card. Create a Sorcerer Role token attached to up to one other target creature you control. Activate only as a sorcery. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has \"Whenever this creature attacks, scry 1.\")", "0/4")
    private val seamstress = Cards.invent("Enchanted Seamstress", "{4}{W}", "Creature — Faerie Advisor", """
        Flying
        When this creature enters, create a Royal Role token attached to another target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has ward {1}.)
    """, "3/3")
    private val minstrels = Cards.invent("Jolly Minstrels", "{2}{R}", "Creature — Human Bard",
        "When this creature enters, you may pay {1}. When you do, create a Young Hero Role token attached to target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\")", "3/2")
    private val boon = Cards.invent("Rat King's Boon", "{1}{B}", "Enchantment", """
        When this enchantment enters, create a Wicked Role token attached to target creature you control. (Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)
        At the beginning of your draw step, if you control an enchanted creature, you lose 1 life and you draw an additional card.
    """)

    // CR 111.10k, 303.4f: the Monster Role enters attached to the target: +1/+1 and trample on top of the +2/+0
    @Test
    fun beastlyTemperAttachesAMonsterRole() = scenario {
        alice.battlefield(cub, "Mountain")
        alice.hand(temper)
        alice.casts(temper, target = cub)
        resolveAll()
        expect {
            cub.power shouldBe 5
            cub.toughness shouldBe 3
            cub hasKeyword Keyword.Trample.kind
            "Monster".subtypes shouldBe listOf(Subtype("Aura"), Subtype("Role"))
            "Monster".colors shouldBe emptyList()
        }
    }

    // CR 303.7a, 704.5z: Alice's newer Role puts her older one into the graveyard; CR 111.10q, 603.10a: the Wicked Role leaving makes each
    // opponent lose 1 life
    @Test
    fun aNewerRoleReplacesTheOlderAndTheWickedRoleDrainsEachOpponent() = scenario(players = 3) {
        alice.battlefield(cub, "Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(murmur, temper)
        bob.hand("Island", "Island")
        alice.casts(murmur, targets = listOf("bob", cub), paying = listOf("Swamp", "Swamp#1", "Swamp#2", "Swamp#3"))
        resolveAll()
        expect {
            alice.battlefield has "Wicked"
            cub.power shouldBe 3
            bob.hand.size shouldBe 0
        }
        alice.casts(temper, target = cub)
        resolveAll()
        expect {
            alice.battlefield has "Monster"
            alice.battlefield lacks "Wicked"
            cub.power shouldBe 5
            alice.life shouldBe 20
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 303.7a: Roles of different controllers on one creature all stay
    @Test
    fun anotherPlayersRoleStays() = scenario {
        alice.battlefield(cub, "Mountain")
        bob.battlefield(mantle)
        attach(mantle, cub)
        alice.hand(temper)
        alice.casts(temper, target = cub)
        resolveAll()
        expect {
            alice.battlefield has "Monster"
            bob.battlefield has mantle
            cub.power shouldBe 6
            cub.toughness shouldBe 4
        }
    }

    // CR 303.4i: a token Aura to be attached to an undefined object isn't created (no choice of something else to enchant)
    @Test
    fun noTargetForTheRoleCreatesNoToken() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(murmur)
        bob.battlefield(ox)
        bob.hand("Island", "Island")
        alice.casts(murmur, target = "bob")
        resolveAll()
        expect {
            pending shouldBe null
            alice.battlefield lacks "Wicked"
            bob.battlefield lacks "Wicked"
            ox.power shouldBe 3
        }
    }

    // CR 111.10j, 613.4b–c: the Cursed Roles set base power and toughness 1/1 (counters still apply) on each opponent's creatures, not Alice's
    @Test
    fun foolishFrolicsCursesEveryOpponentsCreature() = scenario(players = 3) {
        alice.battlefield(cub, "Island", "Island", "Island", "Island")
        alice.hand(frolics)
        bob.battlefield(ox, counters = listOf(p1p1 to 1))
        carol.battlefield(otherCub)
        alice.casts(frolics)
        // in its main phase: not the extra {2} for flash (CR 702.8a)
        may(false)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            ox.power shouldBe 2
            ox.toughness shouldBe 2
            otherCub.power shouldBe 1
            otherCub.toughness shouldBe 1
            cub.power shouldBe 2
            alice.battlefield.names.count { it == "Cursed" } shouldBe 2
        }
    }

    private fun youngHeroAttack(creature: String, counters: Int) = scenario {
        alice.battlefield(oldTimer, creature, "Mountain")
        alice.activates(oldTimer, target = creature)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(creature)
        resolveAll()
        expect { creature.counters(p1p1) shouldBe counters }
    }

    // CR 111.10r, 603.4: the granted attack trigger puts a +1/+1 counter on a creature with toughness 3 or less
    @Test
    fun youngHeroRoleGrowsASmallAttacker() = youngHeroAttack(cub, counters = 1)

    // CR 603.4: toughness 4: the intervening "if" is false, no counter
    @Test
    fun youngHeroRoleIgnoresAToughAttacker() = youngHeroAttack(ox, counters = 0)

    // CR 111.10n: +1/+1 and "Whenever this creature attacks, scry 1"; CR 602.5d: the Podium is sacrificed as a cost and draws
    @Test
    fun walkingPodiumGivesASorcererRole() = scenario {
        alice.battlefield(podium, cub, "Island")
        alice.library("Island", "Plains", "Forest")
        alice.activates(podium, target = cub)
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.graveyard has podium
            cub.power shouldBe 3
            cub.toughness shouldBe 3
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        resolveAll()
        // scry 1: the Plains to the bottom
        choose("Plains")
        expect { alice.library.names.last() shouldBe "Plains" }
    }

    // CR 111.10m: Royal Role: +1/+1 and ward {1}
    @Test
    fun enchantedSeamstressGivesARoyalRole() = scenario {
        alice.battlefield(cub, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(seamstress)
        alice.casts(seamstress)
        resolveAll()
        expect {
            cub.power shouldBe 3
            cub hasKeyword Keyword.Ward.Kind
            seamstress lacksKeyword Keyword.Ward.Kind
        }
    }

    // CR 603.12: paying {1} makes the reflexive trigger, which targets as it is put on the stack
    @Test
    fun jollyMinstrelsPaysForAYoungHeroRole() = scenario {
        alice.battlefield(cub, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(minstrels)
        alice.casts(minstrels, paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        resolveAll()
        may(true)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        answer(Answer.Targets(listOf(target(cub))))
        resolveAll()
        expect {
            alice.battlefield has "Young Hero"
            minstrels.counters(p1p1) shouldBe 0
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        resolveAll()
        expect { cub.counters(p1p1) shouldBe 1 }
    }

    private fun Scenario.toAliceDrawStep() {
        alice.library("Island", "Plains", "Forest")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Draw, turn = 3)
        resolveAll()
    }

    // CR 603.4, 504.2: at Alice's draw step, with a creature of hers enchanted, she loses 1 life and draws one more card
    @Test
    fun ratKingsBoonDrawsWithAnEnchantedCreature() = scenario {
        alice.battlefield(boon, cub, mantle)
        attach(mantle, cub)
        toAliceDrawStep()
        expect {
            alice.life shouldBe 19
            alice.hand.size shouldBe 2
        }
    }

    // CR 603.4: an enchanted creature Bob controls doesn't count
    @Test
    fun ratKingsBoonIgnoresAnOpponentsEnchantedCreature() = scenario {
        alice.battlefield(boon)
        bob.battlefield(cub, mantle)
        attach(mantle, cub)
        toAliceDrawStep()
        expect {
            alice.life shouldBe 20
            alice.hand.size shouldBe 1
        }
    }
}
