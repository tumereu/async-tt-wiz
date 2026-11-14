package arcanum.engine.sets.one

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.rtr.may
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 04: Equipment. A "becomes attached" trigger whose copy effect lasts only while the Equipment stays
 * attached (CR 611.2a, 707.9), equip cost reductions for other Equipment only (CR 601.2f), "creatures you control that are equipped" with
 * three players (CR 301.5), an Equipment that makes a token as its equipped creature attacks.
 */
class OneChunk04EquipScenariosTest {
    private val blade = Cards.invent("Blade of Mingled Souls", "{2}{U}", "Artifact — Equipment", """
        For Mirrodin! (When this Equipment enters, create a 2/2 red Rebel creature token, then attach this to it.)
        Whenever this Equipment becomes attached to a creature, for as long as this Equipment remains attached to it, you may have that creature become a copy of another target creature you control.
        Equip {2}
    """.trimIndent())
    private val whip = Cards.invent("Bladeheld War-Whip", "{1}{R}{W}", "Artifact — Equipment", """
        Equip abilities you activate of other Equipment cost {1} less to activate.
        Equipped creature has double strike.
        Equip {3}{R}{W}
    """.trimIndent())
    private val wings = Cards.invent("Hexsilver Hoverwings", "{3}{W}", "Artifact — Equipment", """
        Equipped creature has flying.
        Creatures you control that are equipped get +1/+0.
        Equip {2}{W}
    """.trimIndent())
    private val buckler = Cards.invent("Plain Buckler", "{1}", "Artifact — Equipment", """
        Equipped creature gets +0/+1.
        Equip {1}
    """.trimIndent())
    private val fleshblade = Cards.invent("Infested Fleshblade", "{1}{W}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        Whenever equipped creature attacks, create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block." (Players dealt combat damage by it also get a poison counter.)
        Equip {2}{W}
    """.trimIndent())
    private val cub = Cards.invent("Thornpath Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val colossus = Cards.invent("Thornpath Colossus", "{4}{G}", "Creature — Giant", "", "5/5")

    // CR 702.163a, 611.2a, 707.9: the Rebel copies the Colossus only while the Blade is attached; moved on, it is a 2/2 again and the Cub copies
    @Test
    fun bladeCopyEndsWhenTheEquipmentMovesOn() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", colossus, cub)
        alice.hand(blade)
        alice.casts(blade)
        resolveAll()
        // the Rebel is the only creature the Blade is attached to; the Colossus and the Cub are legal copy targets
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(colossus))))
        resolveAll()
        may(true)
        resolveAll()
        expect {
            "Rebel Token".power shouldBe 5
            colossus.power shouldBe 5
        }
        alice.activates(blade, target = cub)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(colossus))))
        resolveAll()
        may(true)
        resolveAll()
        expect {
            "Rebel Token".power shouldBe 2
            cub.power shouldBe 5
        }
    }

    // CR 701.3: declining ("you may") copies nothing
    @Test
    fun bladeMayDeclineTheCopy() = scenario {
        alice.battlefield("Island", "Island", "Island", colossus)
        alice.hand(blade)
        alice.casts(blade)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(colossus))))
        resolveAll()
        may(false)
        resolveAll()
        expect { "Rebel Token".power shouldBe 2 }
    }

    // CR 601.2f: the War-Whip reduces another Equipment's equip cost by {1} (Plain Buckler's {1} becomes free) but not its own
    @Test
    fun whipReducesOtherEquipmentOnly() = scenario {
        alice.battlefield(whip, buckler, cub)
        alice.activates(buckler, target = cub)
        resolveAll()
        expect { cub.power shouldBe 2; cub.toughness shouldBe 3 }
        alice.activates(whip, target = cub)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 301.5, 3 players: only creatures its controller controls that carry an Equipment get +1/+0, whichever Equipment it is
    @Test
    fun hoverwingsBoostsOwnEquippedCreaturesOnly() = scenario(players = 3) {
        alice.battlefield(wings, buckler, cub, colossus)
        bob.battlefield(Cards.invent("Opposing Equipment", "{1}", "Artifact — Equipment", "Equipped creature gets +0/+1.\nEquip {1}"), "Forest")
        attach(buckler, cub)
        attach(wings, colossus)
        carol.battlefield(Cards.invent("Hostile Cub", "{1}{G}", "Creature — Bear", "", "2/2"))
        expect {
            cub.power shouldBe 3
            colossus.power shouldBe 6
            "Hostile Cub".power shouldBe 2
            colossus hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.2, 702.164a: attacking with the equipped creature makes a Mite with toxic; +2/+0 applies
    @Test
    fun fleshbladeMakesAMiteWhenTheEquippedCreatureAttacks() = scenario {
        alice.battlefield(fleshblade, cub)
        attach(fleshblade, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        resolveAll()
        expect {
            cub.power shouldBe 4
            "Phyrexian Mite Token".power shouldBe 1
            "Phyrexian Mite Token" hasKeyword Keyword.Toxic.Kind
        }
    }
}
