package arcanum.engine.sets.vow

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals

/** VOW chunk 07: training and "whenever this creature trains" (exiling until it leaves), counters on several Humans at once drawing one card
 * once a turn, removing counters from among creatures as a cost, a prevention that removes counters and deals the number removed, a pump only
 * at night, "if that opponent controls no other creatures" with three players, extra damage to a Spirit's controller, a static that turns on
 * once you've put a counter on a creature, and three distinct pump targets that gain vigilance. */
class VowChunk07TrainingAndConditionsScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val training = "Training (Whenever this creature attacks with another creature with greater power, put a +1/+1 counter on this creature.)"
    private val recruit = Cards.invent("Hooded Recruit", "{4}{G}", "Creature — Human Ranger", """
        $training
        Whenever one or more +1/+1 counters are put on one or more Humans you control, draw a card. This ability triggers only once each turn.
    """.trimIndent(), "2/4")
    private val warden = Cards.invent("Warden of Ollenmark", "{1}{W}{W}", "Creature — Human Soldier", """
        $training
        Whenever this creature trains, exile up to one other target creature from the battlefield or creature card from a graveyard.
        When this creature leaves the battlefield, put the exiled cards onto the battlefield under their owners' control.
    """.trimIndent(), "1/2")
    private val acolyte = Cards.invent("Earnest Acolyte", "{W}", "Creature — Human Warlock", """
        $training
        {2}{W}, Remove two +1/+1 counters from among creatures you control: Destroy target artifact or enchantment.
    """.trimIndent(), "1/2")
    private val bruiser = Cards.invent("Lava Bruiser", "{X}{R}{R}", "Creature — Elemental", """
        This creature enters with X +1/+1 counters on it.
        If damage would be dealt to this creature while it has a +1/+1 counter on it, prevent that damage and remove that many +1/+1 counters from it. When one or more counters are removed from this creature this way, it deals that much damage to any target.
    """.trimIndent(), "0/0")
    private val pounce = Cards.invent("Lupine Pounce", "{2}{G}", "Instant",
        "Target creature you control gets +2/+0 until end of turn if it's night. Then it deals damage equal to its power to target creature you don't control.")
    private val cutthroat = Cards.invent("Lurking Cutthroat", "{3}{B}", "Creature — Vampire Assassin",
        "When this creature enters, target creature an opponent controls gets -2/-2 until end of turn if that opponent controls no other creatures.", "4/2")
    private val blaze = Cards.invent("Tearing Blaze", "{2}{R}", "Instant",
        "Tearing Blaze deals 5 damage to target creature or planeswalker. If that permanent is a Spirit, Tearing Blaze also deals 2 damage to that permanent's controller.")
    private val templar = Cards.invent("Sigmark Templar", "{2}{G}{W}", "Creature — Human Knight", """
        As long as you've put one or more +1/+1 counters on a creature this turn, this creature has trample and lifelink.
        {1}{G}{W}: Target creature you control with a +1/+1 counter on it gains trample and lifelink until end of turn.
    """.trimIndent(), "4/4")
    private val rally = Cards.invent("Rally the Wardens", "{1}{W}{W}", "Sorcery",
        "Until end of turn, target creature gets +3/+3, up to one other target creature gets +2/+2, and up to one other target creature gets +1/+1. Those creatures gain vigilance until end of turn.")

    private val plowbeast = Cards.invent("Hulking Plowbeast", "{4}{G}", "Creature — Ox", "", "5/5")
    private val militia = Cards.invent("Hamlet Militia", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val haunt = Cards.invent("Barrow Haunt", "{2}{B}", "Creature — Spirit", "", "3/3")
    private val bear = Cards.invent("Vale Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val hound = Cards.invent("Ditch Hound", "{1}{B}", "Creature — Dog", "", "4/4")
    private val prod = Cards.invent("Ember Prod", "{R}", "Instant", "Ember Prod deals 2 damage to any target.")
    private val flood = Cards.invent("Searing Flood", "{R}", "Instant", "Searing Flood deals 5 damage to any target.")
    private val vigor = Cards.invent("Kindle Vigor", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val growth = Cards.invent("Rally Growth", "{G}", "Sorcery", "Put a +1/+1 counter on each creature you control.")
    private val gewgaw = Cards.invent("Brass Gewgaw", "{2}", "Artifact", "")
    private val moonhowl = Cards.inventFaces("Transform",
        face("Moonhowl Lad", "{1}{G}", "Creature — Human Werewolf",
            "Daybound (If a player casts no spells during their own turn, it becomes night next turn.)", "2/2", null, null),
        face("Moonhowl Brute", null, "Creature — Werewolf",
            "Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)", "4/4", null, "G"))

    private fun Scenario.targets(vararg names: String) = answer(Answer.Targets(names.map { target(it) }))

    // CR 702.149a (training puts a counter on it), 603.2c (one trigger for the counters), 603.2h (only once each turn: a second counter draws
    // nothing)
    @Test
    fun trainingCounterDrawsOnceEachTurn() = scenario {
        alice.battlefield(recruit, plowbeast, "Forest")
        alice.hand(vigor)
        alice.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(recruit, plowbeast)
        resolveAll()
        expect {
            recruit.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 2
        }
        alice.casts(vigor, target = recruit)
        resolveAll()
        expect {
            recruit.counters(p1p1) shouldBe 2
            alice.hand.size shouldBe 1
        }
    }

    // CR 603.2c: counters put on two Humans by one effect trigger it once; a non-Human's counter doesn't count
    @Test
    fun countersOnTwoHumansAtOnceDrawOneCard() = scenario {
        alice.battlefield(recruit, militia, bear, "Forest")
        alice.hand(growth)
        alice.library("Island", "Island")
        alice.casts(growth)
        resolveAll()
        expect {
            militia.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 1
        }
    }

    // CR 702.149c: a counter put by another effect isn't training
    @Test
    fun counterFromASpellIsNotTraining() = scenario {
        alice.battlefield(warden, "Forest")
        bob.battlefield(bear)
        alice.hand(vigor)
        alice.casts(vigor, target = warden)
        resolveAll()
        expect {
            warden.counters(p1p1) shouldBe 1
            bob.battlefield has bear
        }
    }

    // CR 702.149c (it trains), 607.2a, 610.3 (the exiled creature returns under its owner's control when it leaves)
    @Test
    fun trainsExilesACreatureUntilItLeaves() = scenario(players = 3) {
        alice.battlefield(warden, plowbeast, "Mountain")
        alice.hand(flood)
        carol.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(warden, plowbeast, defender = "bob")
        resolveAll()
        expect { warden.counters(p1p1) shouldBe 1 }
        targets(bear)
        resolveAll()
        expect { carol.exile has bear }
        alice.casts(flood, target = warden)
        resolveAll()
        expect {
            alice.graveyard has warden
            carol.battlefield has bear
            bear.controller shouldBe carol.id
        }
    }

    // CR 702.149c: or a creature card from a graveyard, put onto the battlefield under its owner's control later
    @Test
    fun trainsExilesACreatureCardFromAGraveyard() = scenario {
        alice.battlefield(warden, plowbeast, "Mountain")
        alice.hand(flood)
        bob.graveyard(hound)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(warden, plowbeast)
        resolveAll()
        targets(hound)
        resolveAll()
        expect { bob.exile has hound }
        alice.casts(flood, target = warden)
        resolveAll()
        expect { bob.battlefield has hound }
    }

    // CR 118.3, 122.1: two counters removed from among creatures you control pay the cost
    @Test
    fun removesTwoCountersFromAmongCreatures() = scenario {
        alice.battlefield(acolyte, "Plains", "Plains", "Plains")
        alice.battlefield(militia, bear, counters = listOf(p1p1 to 1))
        bob.battlefield(gewgaw)
        alice.activates(acolyte, target = gewgaw, choosing = listOf(militia, bear))
        resolveAll()
        expect {
            bob.graveyard has gewgaw
            militia.counters(p1p1) shouldBe 0
            bear.counters(p1p1) shouldBe 0
        }
    }

    // CR 615.5: the damage is prevented, as many counters are removed, and it deals that much damage
    @Test
    fun preventedDamageRemovesCountersAndIsDealtBack() = scenario {
        alice.battlefield(bruiser, "Mountain", counters = listOf(p1p1 to 3))
        alice.hand(prod)
        alice.casts(prod, target = bruiser)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            bruiser.counters(p1p1) shouldBe 1
            bruiser.view.obj?.damage shouldBe 0
            bob.life shouldBe 18
        }
    }

    // CR 615.1a (all the damage is prevented while it has a counter), 603.12: "that much" is the three counters removed, not the 5 damage;
    // with no counters left it dies as a 0/0 (CR 704.5f)
    @Test
    fun moreDamageThanCountersDealsTheNumberRemoved() = scenario {
        alice.battlefield(bruiser, "Mountain", counters = listOf(p1p1 to 3))
        alice.hand(flood)
        alice.casts(flood, target = bruiser)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            alice.graveyard has bruiser
            bob.life shouldBe 17
        }
    }

    // CR 732.1: by day the pump doesn't happen, and the creature still deals damage equal to its power
    @Test
    fun byDayNoPump() = scenario {
        alice.battlefield(militia, "Forest", "Forest", "Forest")
        alice.hand(pounce)
        bob.battlefield(hound)
        alice.casts(pounce, targets = listOf(militia, hound))
        resolveAll()
        expect {
            militia.power shouldBe 2
            hound.view.obj?.damage shouldBe 2
        }
    }

    // CR 732.1, 702.145g (a nightbound permanent makes it night): at night it gets +2/+0 first
    @Test
    fun atNightPumpThenDamage() = scenario {
        alice.battlefield(militia, moonhowl, "Forest", "Forest", "Forest")
        backFaceUp(moonhowl)
        alice.hand(pounce)
        bob.battlefield(hound)
        alice.casts(pounce, targets = listOf(militia, hound))
        resolveAll()
        expect {
            militia.power shouldBe 4
            bob.graveyard has hound
        }
    }

    // CR 608.2c, 608.2h: the target's controller controls no other creature: -2/-2
    @Test
    fun opponentWithOneCreatureLosesIt() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(cutthroat)
        bob.battlefield(bear)
        carol.battlefield(hound, militia)
        alice.casts(cutthroat)
        resolveAll()
        targets(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    // CR 608.2h: that opponent controls another creature: nothing happens (the other opponent's creatures don't count)
    @Test
    fun opponentWithAnotherCreatureKeepsIt() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(cutthroat)
        bob.battlefield(bear)
        carol.battlefield(hound, militia)
        alice.casts(cutthroat)
        resolveAll()
        targets(militia)
        resolveAll()
        expect {
            militia.power shouldBe 2
            carol.battlefield has militia
        }
    }

    // CR 608.2h, 120.3: a Spirit's controller is dealt 2 damage; another creature's isn't
    @Test
    fun spiritsControllerIsDealtTwo() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(blaze, blaze)
        bob.battlefield(haunt)
        carol.battlefield(hound)
        alice.casts(blaze, target = haunt)
        resolveAll()
        alice.casts(blaze, target = hound)
        resolveAll()
        expect {
            bob.graveyard has haunt
            carol.graveyard has hound
            bob.life shouldBe 18
            carol.life shouldBe 20
        }
    }

    // CR 611.3a: once you've put a +1/+1 counter on a creature this turn, it has trample and lifelink
    @Test
    fun counterThisTurnTurnsOnTrampleAndLifelink() = scenario {
        alice.battlefield(templar, "Forest")
        bob.battlefield(bear)
        alice.hand(vigor)
        expect { templar lacksKeyword Keyword.Trample.kind }
        alice.casts(vigor, target = bear)
        resolveAll()
        expect {
            templar hasKeyword Keyword.Trample.kind
            templar hasKeyword Keyword.Lifelink.kind
        }
    }

    // CR 115.3: the third "other target creature" can't be either earlier target; all three gain vigilance
    @Test
    fun threeDistinctTargetsGainVigilance() = scenario {
        alice.battlefield(militia, bear, hound, "Plains", "Plains", "Plains")
        alice.hand(rally)
        perform(arcanum.engine.input.Input.Cast(alice.id, card(rally)))
        targets(militia)
        targets(bear)
        val third = decision<Decision.Targets>().options
        assertEquals(setOf(target(hound)), third.toSet())
        targets(hound)
        playOut()
        expect {
            militia.power shouldBe 5
            bear.power shouldBe 4
            hound.power shouldBe 5
            militia hasKeyword Keyword.Vigilance.kind
            bear hasKeyword Keyword.Vigilance.kind
            hound hasKeyword Keyword.Vigilance.kind
        }
    }
}
