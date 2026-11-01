package arcanum.engine.sba

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** State-based actions (CR 704): checked before a player would get priority, all at once, until none apply. */
class StateBasedActionsTest {
    private val creatureSlot = TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))

    private fun creature(name: String, power: Int, toughness: Int, vararg keywords: Keyword) = Cards.define(name, CardDefinition(Layout.Normal,
        listOf(FaceDefinition(name, ManaCost.parse("{2}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(power),
            toughness = PrintedNumber.Fixed(toughness), abilities = keywords.toList()))))

    /** "Target creature you control fights target creature you don't control." */
    private val brawl = Cards.define("Test Brawl", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Brawl", ManaCost.parse("{1}"),
        types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(creatureSlot.copy(name = "mine"), creatureSlot.copy(name = "theirs")),
            steps = listOf(Step.Do(Effect.Fight(Objects.Target("mine"), Objects.Target("theirs")))))))))))

    // CR 704.5a: a player with 0 or less life loses
    @Test
    fun zeroLifeLoses() = scenario {
        alice.life = 2
        alice.hand("Midnight Murmur")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Midnight Murmur")
        resolve()

        assertTrue(state.player(alice.id).lost)
    }

    // CR 704.5b, 121.4: drawing from an empty library loses at the next check, not at once
    @Test
    fun drawingFromAnEmptyLibraryLoses() = scenario {
        alice.hand("Forebear's Insight")
        bob.library("Forest")

        alice.castsWithoutCosts("Forebear's Insight", target = "bob")
        resolve()

        assertTrue(state.player(bob.id).lost)
        expect { bob.hand has "Forest" }
    }

    // CR 104.4a: both players losing at once is a draw: both lose
    @Test
    fun bothPlayersLoseAtOnce() = scenario {
        alice.life = 1
        bob.life = 1
        /** "Each player loses 1 life." */
        val pox = Cards.define("Test Pox", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Pox", ManaCost.parse("{B}"),
            types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.LoseLife(
                arcanum.api.Players.Each(arcanum.api.PlayerFilter.Any), Value.Fixed(1)))))))))))
        alice.hand(pox)

        alice.castsWithoutCosts(pox)
        resolve()

        assertTrue(state.player(alice.id).lost && state.player(bob.id).lost)
    }

    // CR 704.5d, 111.7: a token that died ceases to exist, but its dies trigger saw it die (CR 603.10a)
    @Test
    fun aDeadTokenCeasesToExist() = scenario {
        alice.battlefield("Remorseless Corsair")
        alice.hand("Lingering Shades", "Storm Spark")

        alice.castsWithoutCosts("Lingering Shades")
        resolve()
        alice.castsWithoutCosts("Storm Spark", target = "Spirit Token")
        resolve()

        expect {
            alice.graveyard.names shouldBe listOf("Lingering Shades", "Storm Spark")
            stack.names shouldBe listOf("ability of Remorseless Corsair")
            log mentions "CeasedToExist"
        }
    }

    // CR 704.5h, 702.2b: any damage from a source with deathtouch is lethal
    @Test
    fun deathtouch() = scenario {
        alice.battlefield("Venomous Owlet")
        bob.battlefield("Elvish Hierophant")
        alice.hand(brawl)

        alice.castsWithoutCosts(brawl, targets = listOf("Venomous Owlet", "Elvish Hierophant"))
        resolve()

        expect {
            bob.graveyard has "Elvish Hierophant"
            alice.graveyard has "Venomous Owlet"
        }
    }

    // CR 702.15b: lifelink: damage dealt makes its controller gain that much life
    @Test
    fun lifelink() = scenario {
        alice.battlefield("Crypt Flitter")
        bob.battlefield("Elvish Hierophant")
        alice.hand(brawl)

        alice.castsWithoutCosts(brawl, targets = listOf("Crypt Flitter", "Elvish Hierophant"))
        resolve()

        expect { alice.life shouldBe 21 }
    }

    // CR 702.90b, c: infect deals damage to creatures as -1/-1 counters and to players as poison counters
    @Test
    fun infect() = scenario {
        val blight = creature("Test Blightling", 2, 2, Keyword.Infect)
        alice.battlefield(blight)
        bob.battlefield("Elvish Hierophant")
        alice.hand(brawl)

        alice.castsWithoutCosts(brawl, targets = listOf(blight, "Elvish Hierophant"))
        resolve()

        expect {
            bob.graveyard has "Elvish Hierophant"
            log mentions "PowerToughness(power=-1, toughness=-1)"
        }
    }

    // CR 702.80a: wither puts -1/-1 counters instead of damage; they stay
    @Test
    fun wither() = scenario {
        val withering = creature("Test Witherling", 1, 3, Keyword.Wither)
        alice.battlefield(withering)
        bob.battlefield("Elvish Hierophant")
        alice.hand(brawl)

        alice.castsWithoutCosts(brawl, targets = listOf(withering, "Elvish Hierophant"))
        resolve()

        expect {
            "Elvish Hierophant".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
            "Elvish Hierophant".power shouldBe 1
            "Elvish Hierophant".view.obj!!.damage shouldBe 0
        }
    }

    // CR 704.5i, 306.8: damage to a planeswalker removes loyalty; at 0 it goes to the graveyard
    @Test
    fun planeswalkerWithNoLoyalty() = scenario {
        bob.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Renji Kagehara")
        resolve()

        expect { bob.graveyard has "Renji Kagehara" }
    }

    // CR 704.5j: the legend rule: its controller keeps one
    @Test
    fun legendRule() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way")
        alice.hand("Velis, Mender of the Lost Way")

        alice.castsWithoutCosts("Velis, Mender of the Lost Way")
        resolve()
        val choice = decision<Decision.LegendRule>()
        assertEquals(2, choice.among.size)
        answer(Answer.Keep(choice.among.last()))

        expect {
            alice.battlefield.size shouldBe 1
            alice.graveyard.size shouldBe 1
        }
    }

    // CR 704.5m: an Aura not attached to anything goes to the graveyard
    @Test
    fun unattachedAura() = scenario {
        alice.battlefield("Rapid Retrofit")

        settle()

        expect { alice.graveyard has "Rapid Retrofit" }
    }

    // CR 704.5m: when the enchanted creature dies, the Aura goes too (in the next check)
    @Test
    fun auraOfADeadCreature() = scenario {
        alice.battlefield("Rapid Retrofit", "Elvish Hierophant")
        attach("Rapid Retrofit", "Elvish Hierophant")
        alice.hand("Sudden Rot")

        alice.castsWithoutCosts("Sudden Rot", target = "Elvish Hierophant")
        resolve()

        expect { alice.graveyard.names.toSet() shouldBe setOf("Rapid Retrofit", "Elvish Hierophant", "Sudden Rot") }
    }

    // CR 704.5n: an Equipment attached to something that isn't a creature becomes unattached
    @Test
    fun equipmentOnANoncreatureFallsOff() = scenario {
        alice.battlefield("Bonevise", "Forest")
        attach("Bonevise", "Forest")

        settle()

        assertEquals(null, state.objOrThrow(card("Bonevise")).attachedTo)
        expect { alice.battlefield has "Bonevise" }
    }

    // CR 704.5q: +1/+1 and -1/-1 counters annihilate in pairs
    @Test
    fun countersAnnihilate() = scenario {
        /** "Put two +1/+1 counters on target creature." */
        val boon = Cards.define("Test Double Boon", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Double Boon",
            ManaCost.parse("{G}"), types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(listOf(creatureSlot),
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1), Value.Fixed(2)))))))))))
        alice.battlefield("Festering Gremlin", counters = listOf(CounterKind.PowerToughness(-1, -1) to 1))
        alice.hand(boon)

        alice.castsWithoutCosts(boon, target = "Festering Gremlin")
        resolve()

        expect {
            "Festering Gremlin".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            "Festering Gremlin".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 0
        }
    }

    // CR 714.4: a Saga whose lore counters reached its last chapter, with no chapter ability waiting, is sacrificed
    @Test
    fun finishedSagaIsSacrificed() = scenario {
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 3))

        settle()

        expect {
            card("Haruka's Age of Honor").zone shouldBe arcanum.api.Zone.Graveyard
            log mentions "Sacrificed"
        }
    }

    // CR 120.3e, 704.5g: damage adds up until it is lethal
    @Test
    fun damageAddsUp() = scenario {
        bob.battlefield("Elvish Hierophant")
        alice.hand("Magma Needle", "Flash Surge")

        alice.castsWithoutCosts("Magma Needle", target = "Elvish Hierophant")
        resolve()
        expect { bob.battlefield has "Elvish Hierophant" }
        alice.castsWithoutCosts("Flash Surge", target = "Elvish Hierophant")
        resolve()
        expect { bob.graveyard has "Elvish Hierophant" }
    }

    // CR 704.3: checked again after the first actions: the anthem goes, and the damaged Elf no longer survives
    @Test
    fun checkedAgainUntilNoneApply() = scenario {
        bob.battlefield("Elvish Hierophant", "Elvish Seer")
        alice.hand("Magma Needle", "Sudden Rot")

        alice.castsWithoutCosts("Magma Needle", target = "Elvish Seer")
        resolve()
        expect { bob.battlefield has "Elvish Seer" }
        alice.castsWithoutCosts("Sudden Rot", target = "Elvish Hierophant")
        resolve()

        expect { bob.graveyard.names.toSet() shouldBe setOf("Elvish Hierophant", "Elvish Seer") }
    }

    // CR 704.5d: only tokens cease to exist; cards stay in the graveyard
    @Test
    fun cardsStayInTheGraveyard() = scenario {
        bob.battlefield("Umbral Elves")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        resolve()

        expect { bob.graveyard has "Umbral Elves" }
        assertTrue(state.objects.values.none { it.token })
    }
}
