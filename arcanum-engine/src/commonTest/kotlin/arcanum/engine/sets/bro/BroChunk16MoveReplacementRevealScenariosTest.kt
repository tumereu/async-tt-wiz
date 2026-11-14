package arcanum.engine.sets.bro

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The Brothers' War chunk 16 in play: a conditional counter that looks at the returned creature as it was on the
 * battlefield, a two-mode spell that counts what it exiled, a land destroying by its own last counters, a hand and graveyard
 * choice, an opponent's creatures exiled instead of dying, enter tapped, and mana-value limited returns and exiles.
 */
class BroChunk16MoveReplacementRevealScenariosTest {
    private fun Scenario.targets(vararg names: String, none: Boolean = false) {
        if (pending is Decision.Targets) answer(Answer.Targets(if (none) emptyList() else names.map { target(it) }))
    }

    private val scout = Cards.invent("Zephyr Scout", "{1}{U}", "Creature — Human Soldier", """
        Flash
        Flying
        When this creature enters, return up to one other target creature you control to its owner's hand. If it was a Soldier, put a +1/+1 counter on this creature.
    """, "2/1")
    private val pikeman = Cards.invent("Hill Pikeman", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val ranger = Cards.invent("Hill Ranger", "{1}{G}", "Creature — Human Scout", "", "2/2")
    private val mask = Cards.invent("Mask of Many", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+1 and is all creature types.
        Equip {1}
    """)
    private val titansCommand = Cards.invent("Titan's Command", "{4}{G}{G}", "Sorcery", """
        Choose two —
        • Exile target player's graveyard. You gain 1 life for each card exiled this way.
        • Search your library for up to two land cards, put them onto the battlefield tapped, then shuffle.
        • Create two 2/2 green Bear creature tokens.
        • Put two +1/+1 counters on each creature you control.
    """)
    private val blast = Cards.invent("Blast Plain", null, "Land", """
        This land enters with a charge counter on it.
        {T}: Add {C}.
        {X}{X}, {T}: Put X charge counters on this land.
        {3}, {T}, Sacrifice this land: Destroy each nonland permanent with mana value equal to the number of charge counters on this land.
    """)
    private val dreams = Cards.invent("Dreams of Iron and Oil", "{B}", "Sorcery",
        "Target opponent reveals their hand. You choose an artifact or creature card from it, then choose an artifact or creature card from their graveyard. Exile the chosen cards.")
    private val pall = Cards.invent("Misery's Pall", "{1}{B}", "Creature — Shade", """
        If a creature an opponent controls would die, exile it instead.
        {1}: This creature gets +1/+1 until end of turn.
    """, "2/2")
    private val doom = Cards.invent("Doom Bolt", "{B}", "Instant", "Destroy target creature.")
    private val heir = Cards.invent("Clay Heir", "{1}", "Artifact Creature — Golem", """
        This creature enters tapped.
        {2}{B}: Return this card from your graveyard to your hand.
    """, "1/2")
    private val hero = Cards.invent("Hero of the Sands", "{3}{W}{B}", "Creature — Human Soldier", """
        When this creature enters, return target artifact or creature card with mana value 3 or less from your graveyard to the battlefield.
        Creatures you control with mana value 3 or less get +1/+0.
    """, "3/2")
    private val spears = Cards.invent("Lay Down Spears", "{W}", "Sorcery",
        "Exile target creature with mana value less than or equal to the number of Plains you control. Its controller gains 3 life.")
    private val cheap = Cards.invent("Cheap Brute", "{1}{R}", "Creature — Ogre", "", "2/2")
    private val dear = Cards.invent("Dear Brute", "{2}{R}{R}", "Creature — Ogre", "", "4/4")
    private val gizmo = Cards.invent("Brass Gizmo", "{2}", "Artifact", "")
    private val statue = Cards.invent("Iron Statue", "{4}", "Artifact", "")

    // CR 603.4, 608.2h: the returned creature was not a Soldier
    @Test
    fun noCounterWhenTheReturnedCreatureWasNotASoldier() = scenario {
        alice.battlefield("Island", "Island", ranger)
        alice.hand(scout)
        alice.casts(scout)
        resolveAll()
        targets(ranger)
        resolveAll()
        expect {
            alice.hand has ranger
            scout.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 603.4: the returned creature was a Soldier
    @Test
    fun counterWhenTheReturnedCreatureWasASoldier() = scenario {
        alice.battlefield("Island", "Island", pikeman)
        alice.hand(scout)
        alice.casts(scout)
        resolveAll()
        targets(pikeman)
        resolveAll()
        expect {
            alice.hand has pikeman
            scout.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 608.2h, 400.7: a creature that was every type on the battlefield was a Soldier, though the card in hand is not
    @Test
    fun counterWhenTheCreatureWasASoldierOnlyOnTheBattlefield() = scenario {
        alice.battlefield("Island", "Island", "Island", ranger, mask)
        alice.hand(scout)
        alice.activates(mask, target = ranger)
        resolveAll()
        alice.casts(scout)
        resolveAll()
        targets(ranger)
        resolveAll()
        expect {
            alice.hand has ranger
            scout.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 603.4: no creature returned, so it was not a Soldier
    @Test
    fun noCounterWhenNothingIsReturned() = scenario {
        alice.battlefield("Island", "Island", pikeman)
        alice.hand(scout)
        alice.casts(scout)
        resolveAll()
        targets(none = true)
        resolveAll()
        expect {
            alice.battlefield has pikeman
            scout.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 700.2d, 701.13a: the life gained counts the cards exiled by the first mode; the second mode is the counters
    @Test
    fun titansCommandExilesAGraveyardAndPutsCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", cheap)
        alice.hand(titansCommand)
        bob.graveyard(gizmo, statue, "Island")
        alice.casts(titansCommand, modes = listOf(0, 3), target = "bob")
        resolveAll()
        expect {
            alice.life shouldBe 23
            bob.exile has gizmo
            bob.exile has statue
            cheap.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 700.2d: the bears and a search for two lands
    @Test
    fun titansCommandMakesBearsAndSearches() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(titansCommand)
        alice.library("Mountain", "Swamp", "Island")
        alice.casts(titansCommand, modes = listOf(1, 2))
        resolveAll()
        alice.chooses("Mountain", "Swamp")
        playOut()
        expect {
            alice.battlefield.names.count { it == "Bear Token" } shouldBe 2
            "Mountain".tapped shouldBe true
            "Swamp".tapped shouldBe true
        }
    }

    // CR 608.2h: the sacrifice is a cost, so the destroy count uses the land's last known counters
    @Test
    fun blastPlainDestroysByItsLastCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", cheap, gizmo)
        alice.battlefield(blast, counters = listOf(CounterKind.Named("charge") to 2))
        bob.battlefield(dear, "Forest")
        alice.activates(blast, ability = 2)
        resolveAll()
        expect {
            alice.graveyard has cheap
            alice.graveyard has gizmo
            bob.battlefield has dear
            bob.battlefield has "Forest"
        }
    }

    // CR 701.20a, 400.7: a hand card and a graveyard card chosen in turn, both exiled
    @Test
    fun dreamsExilesTheChosenCardFromHandAndGraveyard() = scenario {
        alice.battlefield("Swamp")
        alice.hand(dreams)
        bob.hand(gizmo, cheap, "Island")
        bob.graveyard(statue, dear)
        alice.casts(dreams, target = "bob")
        resolveAll()
        alice.chooses(gizmo)
        alice.chooses(dear)
        playOut()
        expect {
            bob.exile has gizmo
            bob.exile has dear
            bob.hand has cheap
            bob.graveyard has statue
        }
    }

    // CR 614.6: only opponents' creatures are exiled instead of dying
    @Test
    fun opponentsCreaturesAreExiledInsteadOfDying() = scenario {
        alice.battlefield("Swamp", "Swamp", pall, pikeman)
        alice.hand(doom, doom)
        bob.battlefield(cheap)
        alice.casts(doom, target = cheap)
        resolveAll()
        expect { bob.exile has cheap }
        alice.casts(doom, target = pikeman)
        resolveAll()
        expect { alice.graveyard has pikeman }
    }

    // CR 614.1c, 400.7: it enters tapped, and the activated ability works from the graveyard
    @Test
    fun heirEntersTappedAndReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(heir)
        alice.casts(heir)
        resolveAll()
        expect { heir.tapped shouldBe true }
        move(heir, Zone.Graveyard)
        alice.activates(heir)
        resolveAll()
        expect { alice.hand has heir }
    }

    // CR 202.3: only a card of mana value 3 or less returns; the static boost covers only such creatures
    @Test
    fun heroReturnsAndBoostsCheapCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.graveyard(cheap, dear)
        alice.hand(hero)
        alice.casts(hero)
        resolveAll()
        targets(cheap)
        resolveAll()
        expect {
            alice.battlefield has cheap
            alice.graveyard has dear
            cheap.power shouldBe 3
            hero.power shouldBe 3
        }
    }

    // CR 202.3, 119.1: the limit is the number of Plains; its controller, not the caster, gains the life
    @Test
    fun spearsExileAndGiveItsControllerLife() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(spears)
        bob.battlefield(cheap, dear)
        alice.casts(spears, target = cheap)
        resolveAll()
        expect {
            bob.exile has cheap
            bob.life shouldBe 23
            alice.life shouldBe 20
        }
    }

    // CR 202.3, 115.1: a creature with mana value above the Plains count is no legal target
    @Test
    fun spearsCannotTargetACreatureAboveThePlainsCount() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(spears)
        bob.battlefield(dear)
        assertFailsWith<IllegalArgumentException> { alice.casts(spears, target = dear) }
    }
}
