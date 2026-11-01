package arcanum.engine.cube

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The cube's chunk 41 (counters, pinging and artifact engines), played as a player would. */
class Chunk41CountersDamageAndArtifactEnginesScenariosTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Chooses targets when asked; a slot with only as many legal targets as it needs is filled without asking (P5). */
    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    /** {T}: a +1/+1 counter on each artifact creature you control. */
    @Test
    fun ironForeman() = scenario {
        alice.battlefield("Iron Foreman", "Volatile Myr", bear)
        bob.battlefield("Myr Gleaner")

        alice.activates("Iron Foreman")
        allPass()
        expect {
            "Iron Foreman".counters(p1p1) shouldBe 1
            "Volatile Myr".counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
            "Myr Gleaner".counters(p1p1) shouldBe 0
        }
    }

    /** Enters with X counters; {4}: another; remove a counter: 1 damage to any target. */
    @Test
    fun stridingArbalest() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Striding Arbalest")
        bob.battlefield(bear)

        alice.casts("Striding Arbalest", x = 2)
        allPass()
        expect { "Striding Arbalest".counters(p1p1) shouldBe 2 }
        alice.activates("Striding Arbalest")
        allPass()
        expect { "Striding Arbalest".counters(p1p1) shouldBe 3 }

        alice.activates("Striding Arbalest", ability = 1, target = "bob")
        allPass()
        alice.activates("Striding Arbalest", ability = 1, target = bear)
        allPass()
        alice.activates("Striding Arbalest", ability = 1, target = bear)
        allPass()
        expect {
            bob.life shouldBe 19
            bob.graveyard has bear
            "Striding Arbalest".counters(p1p1) shouldBe 0
            alice.graveyard has "Striding Arbalest"
        }
    }

    /** Enters with a counter; {1}, remove a counter: 1 damage to any target. */
    @Test
    fun uniskelion() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Uniskelion")

        alice.casts("Uniskelion")
        allPass()
        expect { "Uniskelion".power shouldBe 2 }
        alice.activates("Uniskelion", target = "bob")
        allPass()
        expect {
            bob.life shouldBe 19
            "Uniskelion".power shouldBe 1
        }
        // CR 602.2b: no counter left to remove
        assertFailsWith<IllegalArgumentException> { alice.activates("Uniskelion", target = "bob") }
    }

    /** {W}: exile a card from a graveyard, a permanent card adding a +1/+1 counter; equipped creature gets +1/+1 per
     * counter; reconfigure {2}. */
    @Test
    fun leopardSash() = scenario {
        alice.battlefield("Leopard Sash", bear, "Plains", "Plains", "Plains", "Plains")
        bob.graveyard(ogre, "Storm Spark")

        alice.activates("Leopard Sash", target = ogre)
        allPass()
        alice.activates("Leopard Sash", target = "Storm Spark")
        allPass()
        expect {
            bob.exile has ogre
            bob.exile has "Storm Spark"
            "Leopard Sash".counters(p1p1) shouldBe 1
            "Leopard Sash".power shouldBe 2
        }

        // CR 702.151a: reconfigure: attached, it isn't a creature
        alice.activates("Leopard Sash", ability = 1, target = bear)
        allPass()
        expect {
            bear.power shouldBe 3
            bear.toughness shouldBe 3
            "Leopard Sash".types shouldBe listOf(CardType.Artifact)
        }
    }

    /** {R}, {T}, exile two cards from your graveyard: 2 damage to any target. */
    @Test
    fun bleakMagmacaller() = scenario {
        alice.battlefield("Bleak Magmacaller", "Mountain")
        alice.graveyard("Forest", "Island", "Swamp")

        alice.activates("Bleak Magmacaller", target = "bob", choosing = listOf("Forest", "Island"))
        allPass()
        expect {
            bob.life shouldBe 18
            alice.exile.size shouldBe 2
            alice.graveyard.names shouldBe listOf("Swamp")
        }
    }

    /** {1}{R}, discard a land card: 2 damage to any target; {G}: return a tapped land you control to its owner's hand. */
    @Test
    fun breathingTornado() = scenario {
        alice.battlefield("Breathing Tornado", "Mountain", "Mountain", "Forest")
        alice.hand("Island", "Storm Spark")

        alice.activates("Breathing Tornado", target = "bob", choosing = listOf("Island"))
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has "Island"
        }

        alice.activates("Breathing Tornado", ability = 1)
        allPass()
        alice.chooses("Mountain")
        expect {
            alice.hand has "Mountain"
            alice.battlefield.names.count { it == "Mountain" } shouldBe 1
        }
    }

    /** Haste; each discard: 1 damage to each opponent; {1}{R}, discard a card: draw a card, only while attacking. */
    @Test
    fun glintTuskCorsair() = scenario {
        alice.battlefield("Glint-Tusk Corsair", "Mountain", "Mountain", sick = true)
        alice.hand("Forest")
        alice.library("Island")

        assertFailsWith<IllegalArgumentException> { alice.activates("Glint-Tusk Corsair", choosing = listOf("Forest")) }
        advanceTo(TurnStep.DeclareAttackers)
        // CR 702.10b: haste
        alice.attacks("Glint-Tusk Corsair")
        alice.activates("Glint-Tusk Corsair", choosing = listOf("Forest"))
        expect { stack.names shouldBe listOf("ability of Glint-Tusk Corsair", "ability of Glint-Tusk Corsair") }
        allPass()
        allPass()
        expect {
            bob.life shouldBe 19
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** {T}: a player sacrifices their artifact and returns an artifact card from their graveyard at the same time. */
    @Test
    fun goblinSolderer() = scenario {
        alice.battlefield("Goblin Solderer")
        bob.battlefield(idol)
        bob.graveyard("Lotus Frond")

        alice.activates("Goblin Solderer", targets = listOf(idol, "Lotus Frond"))
        allPass()
        expect {
            bob.graveyard has idol
            bob.battlefield has "Lotus Frond"
        }
    }

    /** Can't block; {1}{B}, exile another creature card from your graveyard: return it from your graveyard. */
    @Test
    fun junkpileScavenger() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard("Junkpile Scavenger", bear)
        bob.battlefield(ogre)

        alice.activates("Junkpile Scavenger", choosing = listOf(bear))
        allPass()
        expect {
            alice.battlefield has "Junkpile Scavenger"
            alice.exile has bear
        }

        alice.passes()
        expect { "Junkpile Scavenger" hasMechanic { it is arcanum.api.Mechanic.CantBlock } }
    }

    @Test
    fun junkpileScavengerCantBlock() = scenario {
        alice.battlefield(ogre)
        bob.battlefield("Junkpile Scavenger")

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        allPass()
        // CR 509.1a: no legal blocker, so damage follows without a declaration
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    /** Casting an artifact spell makes a 1/1 flying Thopter; {1}{U}, sacrifice two artifacts: draw a card. */
    @Test
    fun taviThopterSavant() = scenario {
        alice.battlefield("Tavi, Thopter Savant", "Island", "Island", "Island", "Island", idol)
        alice.hand("Volatile Myr")
        alice.library("Forest")

        alice.casts("Volatile Myr")
        allPass()
        expect {
            alice.battlefield has "Thopter Token"
            "Thopter Token" hasKeyword Keyword.Flying
        }
        allPass()

        alice.activates("Tavi, Thopter Savant", choosing = listOf(idol, "Thopter Token"))
        allPass()
        expect {
            alice.hand has "Forest"
            alice.graveyard has idol
            alice.battlefield lacks "Thopter Token"
        }
    }
}
