package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The cube's chunk 33 (creatures whose enters ability removes or takes something), played as a player would. */
class Chunk33EntersRemovalScenariosTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")
    private val ward = Cards.invent("Pale Ward", "{1}{W}", "Enchantment")
    private val relic = Cards.invent("Gilded Relic", "{5}", "Artifact")
    private val dart = Cards.invent("Grave Dart", "{1}{B}", "Instant", "Destroy target creature.")
    private val blade = Cards.invent("Plain Shortblade", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+0.\nEquip {1}")

    /** Chooses targets when asked; a slot with only as many legal targets as it needs is filled without asking (P5). */
    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }
    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** Each player sacrifices a nontoken creature of their choice. */
    @Test
    fun cursedPillager() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand("Cursed Pillager")
        bob.battlefield(ogre)

        alice.casts("Cursed Pillager")
        allPass()
        expect { stack.names shouldBe listOf("ability of Cursed Pillager") }
        allPass()
        // CR 701.21a: each player chooses what they sacrifice; Bob's only creature goes without a choice (P5)
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        alice.chooses(bear)

        expect {
            alice.graveyard has bear
            alice.battlefield has "Cursed Pillager"
            bob.graveyard has ogre
        }
    }

    /** Flying, lifelink; enters: look at an opponent's hand and exile a nonland card from it until it leaves. */
    @Test
    fun deepGrottoBat() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand("Deep-Grotto Bat", "Storm Spark")
        bob.hand("Cinder Bolt", "Forest")

        alice.casts("Deep-Grotto Bat")
        allPass()
        targets("bob")
        allPass()
        alice.chooses("Cinder Bolt")
        expect {
            "Deep-Grotto Bat" hasKeyword Keyword.Flying
            "Deep-Grotto Bat" hasKeyword Keyword.Lifelink
            bob.exile has "Cinder Bolt"
            bob.hand has "Forest"
        }
    }

    /** When the Bat leaves, the exiled card comes back. */
    // CR 610.3: "until … leaves the battlefield": a one-shot effect right after it leaves, without the Stack
    @Test
    fun deepGrottoBatGivesTheCardBack() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand("Deep-Grotto Bat", "Storm Spark")
        bob.hand("Cinder Bolt", "Forest")

        alice.casts("Deep-Grotto Bat")
        allPass()
        allPass()
        alice.chooses("Cinder Bolt")
        alice.casts("Storm Spark", target = "Deep-Grotto Bat")
        allPass()
        expect {
            stack.isEmpty()
            alice.graveyard has "Deep-Grotto Bat"
            bob.hand has "Cinder Bolt"
        }
    }

    /** Enters: an opponent reveals their hand, you exile a nonland card; leaves: the card returns to its owner's hand. */
    @Test
    fun hypnoticFiend() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand("Hypnotic Fiend", "Storm Spark")
        bob.hand("Cinder Bolt", "Forest")

        // Only one nonland card: it is chosen without asking (P5)
        alice.casts("Hypnotic Fiend")
        allPass()
        targets("bob")
        allPass()
        expect { bob.exile has "Cinder Bolt" }

        // CR 603.6c: the leaves-the-battlefield trigger returns the exiled card
        alice.casts("Storm Spark", target = "Hypnotic Fiend")
        allPass()
        expect { stack.names shouldBe listOf("ability of Hypnotic Fiend") }
        allPass()
        expect { bob.hand has "Cinder Bolt" }
    }

    /** The same pair of abilities on a white-black artifact creature; bounced, it gives the card back. */
    @Test
    fun ebbmarshPilferer() = scenario {
        alice.battlefield("Plains", "Swamp", "Mountain")
        alice.hand("Ebbmarsh Pilferer", "Storm Spark")
        bob.hand("Storm Spark", "Island")

        alice.casts("Ebbmarsh Pilferer")
        allPass()
        targets("bob")
        allPass()
        expect {
            bob.exile has "Storm Spark"
            "Ebbmarsh Pilferer".types shouldBe listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature)
        }

        alice.casts("Storm Spark", target = "Ebbmarsh Pilferer")
        allPass()
        allPass()
        expect {
            alice.graveyard has "Ebbmarsh Pilferer"
            bob.hand has "Storm Spark"
        }
    }

    /** Vigilance; enters: destroy up to one artifact or enchantment; {T}: you and an opponent each draw. */
    @Test
    fun velisMenderOfTheLostWay() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Velis, Mender of the Lost Way")
        alice.library("Island", "Island")
        bob.battlefield(ward)
        bob.library("Forest", "Forest")

        alice.casts("Velis, Mender of the Lost Way")
        allPass()
        targets(ward)
        allPass()
        expect {
            bob.graveyard has ward
            "Velis, Mender of the Lost Way" hasKeyword Keyword.Vigilance
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)

        alice.activates("Velis, Mender of the Lost Way", target = "bob")
        allPass()
        expect {
            alice.hand has "Island"
            bob.hand.size shouldBe 2
        }
    }

    /** Enters: you may destroy target artifact or enchantment. */
    @Test
    fun overgrowthSage() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Overgrowth Sage")
        bob.battlefield(idol)

        alice.casts("Overgrowth Sage")
        allPass()
        allPass()
        yes()
        expect { bob.graveyard has idol }
    }

    /** Changeling; enters: you may exile a creature card from your graveyard, and if you do, exile an opponent's
     * artifact or enchantment. */
    @Test
    fun veiledWrecker() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Veiled Wrecker")
        alice.graveyard(bear)
        bob.battlefield(ward)

        alice.casts("Veiled Wrecker")
        expect { stack.names shouldBe listOf("Veiled Wrecker") }
        allPass()
        targets(ward)
        allPass()
        // The only creature card in the graveyard is exiled once Alice says yes
        yes()
        expect {
            alice.exile has bear
            bob.exile has ward
            // CR 702.73a: every creature type
            assertEquals(true, "Veiled Wrecker".view.characteristics!!.hasSubtype(arcanum.api.Subtype("Elf")))
        }
    }

    /** Without a creature card to exile, nothing is exiled from the opponent either. */
    @Test
    fun veiledWreckerWithoutAGraveyard() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Veiled Wrecker")
        bob.battlefield(ward)

        alice.casts("Veiled Wrecker")
        allPass()
        targets(ward)
        allPass()
        expect {
            pending shouldBe null
            bob.battlefield has ward
        }
    }

    /** Enters: exile up to one nonland, nontoken permanent you don't control with mana value 4 or less; leaves: its owner
     * gets an X/X Illusion, X its mana value. */
    @Test
    fun cloudhavenPhantasm() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand("Cloudhaven Phantasm", "Storm Spark")
        bob.battlefield(ogre)

        alice.casts("Cloudhaven Phantasm")
        allPass()
        targets(ogre)
        allPass()
        expect { bob.exile has ogre }

        alice.casts("Storm Spark", target = "Cloudhaven Phantasm")
        allPass()
        allPass()
        expect {
            bob.exile has ogre
            bob.battlefield has "Illusion Token"
            "Illusion Token".power shouldBe 4
            "Illusion Token".toughness shouldBe 4
        }
    }

    /** Flash, lifelink; enters: exile up to one other creature, whose controller gains life equal to its power. */
    @Test
    fun seclusion() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand("Seclusion")
        bob.battlefield(ogre)
        bob.life = 10
        turn(2, active = bob)

        bob.passes()
        // CR 702.8a: flash: cast on the opponent's turn
        alice.casts("Seclusion")
        allPass()
        targets(ogre)
        allPass()
        expect {
            bob.exile has ogre
            bob.life shouldBe 14
            "Seclusion" hasKeyword Keyword.Lifelink
        }
    }

    /** Evoked by exiling a white card from the hand, it is sacrificed as it enters, but its enters ability works. */
    @Test
    fun seclusionEvoked() = scenario {
        alice.hand("Seclusion", "Cloudhaven Phantasm")
        bob.battlefield(ogre)

        // CR 702.74a: evoke
        val evoke = view()[card("Seclusion")]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost } == true
        }.origin
        alice.casts("Seclusion", way = CastWay(alternative = evoke), choosing = listOf("Cloudhaven Phantasm"))
        allPass()
        // Two enters triggers: the sacrifice and the exile; Alice orders them
        answer(Answer.Order(listOf(0, 1)))
        targets(ogre)
        resolveStackByPassing()
        expect {
            alice.exile has "Cloudhaven Phantasm"
            alice.graveyard has "Seclusion"
            bob.exile has ogre
            bob.life shouldBe 24
        }
    }

    /** Kicked with X, it takes control of an artifact with mana value X or less; an Equipment is attached to it. */
    @Test
    fun pilferingSkydiverKicked() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Pilfering Skydiver")
        bob.battlefield(blade, relic)

        alice.casts("Pilfering Skydiver", kicked = 1, x = 1)
        allPass()
        targets(blade)
        allPass()
        expect {
            blade.controller shouldBe alice.id
            "Pilfering Skydiver".power shouldBe 3
            "Pilfering Skydiver" hasKeyword Keyword.Flying
        }
        assertEquals(arcanum.engine.state.TargetRef.Object(card("Pilfering Skydiver")), state.objOrThrow(card(blade)).attachedTo)
    }

    /** Not kicked, its enters ability doesn't trigger (CR 603.4). */
    @Test
    fun pilferingSkydiverUnkicked() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Pilfering Skydiver")
        bob.battlefield(blade)

        alice.casts("Pilfering Skydiver", kicked = 0)
        allPass()
        expect {
            stack.isEmpty()
            blade.controller shouldBe bob.id
        }
    }

    /** Enters: return an opponent's creature to its owner's hand; they can't cast spells of that name until your next
     * turn. */
    @Test
    fun mirrorwardMage() = scenario {
        alice.battlefield("Plains", "Island", "Island")
        alice.hand("Mirrorward Mage")
        bob.battlefield(bear, "Forest", "Forest")
        alice.library("Island", "Island")
        bob.library("Forest", "Forest")

        alice.casts("Mirrorward Mage")
        allPass()
        targets(bear)
        allPass()
        expect { bob.hand has bear }

        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertFailsWith<IllegalArgumentException> { bob.casts(bear) }
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        bob.casts(bear)
        expect { stack.names shouldBe listOf(bear) }
    }

    /** Enters: deals damage equal to its power to any target; persist brings it back once with a -1/-1 counter. */
    @Test
    fun bloodthirstyRedcap() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Bloodthirsty Redcap", "Storm Spark")

        alice.casts("Bloodthirsty Redcap")
        allPass()
        targets("bob")
        allPass()
        expect { bob.life shouldBe 18 }

        // CR 702.79a: persist
        alice.casts("Storm Spark", target = "Bloodthirsty Redcap")
        allPass()
        allPass()
        expect {
            alice.battlefield has "Bloodthirsty Redcap"
            "Bloodthirsty Redcap".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
            "Bloodthirsty Redcap".power shouldBe 1
        }
        targets("bob")
        expect { stack.names shouldBe listOf("ability of Bloodthirsty Redcap") }
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** Flash, flying; enters: exile a spell with mana value 4 or less; leaves: its owner may cast it free. */
    @Test
    fun spellStifler() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp")
        alice.hand("Storm Spark", dart)
        bob.battlefield("Plains", "Island", "Island")
        bob.hand("Spell Stifler")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Spell Stifler")
        allPass()
        targets("Storm Spark")
        allPass()
        expect {
            alice.exile has "Storm Spark"
            bob.life shouldBe 20
            "Spell Stifler" hasKeyword Keyword.Flying
        }

        // Dies: the exiled card's owner may cast it without paying its mana cost (CR 118.9)
        alice.casts(dart, target = "Spell Stifler")
        allPass()
        expect { stack.names shouldBe listOf("ability of Spell Stifler") }
        allPass()
        yes()
        targets("bob")
        expect { stack.names shouldBe listOf("Storm Spark") }
        allPass()
        expect {
            bob.life shouldBe 17
            "Mountain".tapped shouldBe true
            alice.graveyard has "Storm Spark"
        }
    }

    /** Flash, flying; enters: counter a spell with mana value up to the number of Faeries you control. */
    @Test
    fun spellstammerPixie() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Hypnotic Fiend")
        alice.battlefield("Swamp")
        bob.battlefield("Island", "Island")
        bob.hand("Spellstammer Pixie")

        alice.casts("Hypnotic Fiend")
        alice.passes()
        bob.casts("Spellstammer Pixie")
        allPass()
        // One Faerie: the mana value 2 spell isn't a legal target, so the ability is removed (CR 603.3d)
        expect {
            stack.names shouldBe listOf("Hypnotic Fiend")
            bob.battlefield has "Spellstammer Pixie"
        }
    }

    /** With enough Faeries, the spell is countered. */
    @Test
    fun spellstammerPixieCounters() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.hand("Spellstammer Pixie")
        bob.battlefield("Island", "Island", "Spellstammer Pixie")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Spellstammer Pixie")
        allPass()
        targets("Storm Spark")
        allPass()
        expect {
            alice.graveyard has "Storm Spark"
            stack.isEmpty()
            bob.life shouldBe 20
        }
    }

    /** Enters: the next instant or sorcery with mana value 2 or less you cast this turn is copied. */
    @Test
    fun harborGateTempestcaller() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand("Harbor Gate Tempestcaller", "Storm Spark")

        alice.casts("Harbor Gate Tempestcaller", kicked = 0)
        allPass()
        allPass()
        alice.casts("Storm Spark", target = "bob")
        expect { stack.size shouldBe 2 }
        resolveStackByPassing()
        expect { bob.life shouldBe 14 }
    }

    /** Kicked, the spell is copied twice. */
    @Test
    fun harborGateTempestcallerKicked() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Mountain")
        alice.hand("Harbor Gate Tempestcaller", "Storm Spark")

        alice.casts("Harbor Gate Tempestcaller", kicked = 1)
        allPass()
        allPass()
        alice.casts("Storm Spark", target = "bob")
        resolveStackByPassing()
        expect { bob.life shouldBe 11 }
    }

    /** Players pass until the Stack is empty, answering "choose new targets" and similar with no change. */
    private fun Scenario.resolveStackByPassing() {
        var guard = 30
        while (state.stack.isNotEmpty() && guard-- > 0) {
            when (val d = pending) {
                null -> allPass()
                is Decision.May -> answer(Answer.YesNo(false))
                else -> kotlin.test.fail("Waiting for $d")
            }
        }
    }
}
