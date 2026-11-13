package arcanum.engine.sets.isd

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** ISD chunk 12: a death trigger that makes two tokens, a may-damage trigger limited to flying creatures, and a may-destroy trigger limited to
 * three creature types. Invented names carry the oracle text of the real cards. */
class IsdChunk12CounterTokensDamageDestroyScenariosTest {
    private val guard = Cards.invent("Cellar Warden", "{3}{W}", "Creature — Human Scout",
        "When this creature dies, create two 1/1 white Spirit creature tokens with flying.", "2/2")
    private val rig = Cards.invent("Skyhunter Rig", "{6}", "Artifact Creature — Construct",
        "When this creature enters, you may have it deal 4 damage to target creature with flying.", "4/5")
    private val slayer = Cards.invent("Grave Slayer", "{3}{W}", "Creature — Human Soldier",
        "When this creature enters, you may destroy target Vampire, Werewolf, or Zombie.", "3/2")
    private val hexMark = Cards.invent("Hex Mark", "{B}", "Instant", "Destroy target creature.")
    private val drake = Cards.invent("Sky Drake", "{3}{U}", "Creature — Drake", "Flying", "3/3")
    private val bear = Cards.invent("Moor Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val vampire = Cards.invent("Night Vampire", "{2}{B}", "Creature — Vampire", "", "2/2")
    private val werewolf = Cards.invent("Moor Wolf", "{2}{R}", "Creature — Werewolf", "", "2/2")
    private val zombie = Cards.invent("Pale Zombie", "{2}{B}", "Creature — Zombie", "", "2/2")

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 700.4, 603.10a: the dies trigger makes two tokens, each a 1/1 flying Spirit
    @Test
    fun guard_diesMakesTwoFlyingSpirits() = scenario {
        alice.battlefield(guard, "Swamp")
        alice.hand(hexMark)
        alice.casts(hexMark, target = guard)
        resolveAll()
        expect { alice.graveyard has guard }
        check(count("Spirit Token") == 2)
        expect { "Spirit Token" hasKeyword arcanum.api.Keyword.Flying.kind }
    }

    // CR 608.2d, 120.3: a flying creature takes 4 damage, a non-flying creature is not a legal target
    @Test
    fun rig_dealsFourToAFlyerOnly() = scenario {
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(rig)
        bob.battlefield(drake, bear)
        alice.casts(rig)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Object(card(drake)))))
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            drake.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 603.5: declining the "may" deals no damage
    @Test
    fun rig_decliningDealsNothing() = scenario {
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(rig)
        bob.battlefield(drake)
        alice.casts(rig)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Object(card(drake)))))
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect { drake.zone shouldBe Zone.Battlefield }
    }

    // CR 701.8a: the trigger may destroy a Werewolf of the opponent's, but not a Bear
    @Test
    fun slayer_destroysAWerewolfNotABear() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(slayer)
        bob.battlefield(werewolf, bear)
        alice.casts(slayer)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Object(card(werewolf)))))
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            werewolf.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.8a: Vampires and Zombies are legal too, and declining destroys nothing
    @Test
    fun slayer_decliningDestroysNothing() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(slayer)
        bob.battlefield(vampire, zombie)
        alice.casts(slayer)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Object(card(zombie)))))
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect {
            zombie.zone shouldBe Zone.Battlefield
            vampire.zone shouldBe Zone.Battlefield
        }
    }
}
