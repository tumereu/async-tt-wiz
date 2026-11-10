package arcanum.engine.sets.fdn

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 10a played: the invented names carry the oracle text of Battle-Rattle Shaman, Death Baron, Heraldic Banner, Joraga Invocation, Valkyrie's Call, Crawling Barrens, Crystal Barricade and Sphinx of the Final Word. */
class FdnChunk10aGetHaveScenariosTest {
    private val shaman = Cards.invent("Drumcall Shaman", "{3}{R}", "Creature — Goblin Shaman",
        "At the beginning of combat on your turn, you may have target creature get +2/+0 until end of turn.", "2/2")
    private val baron = Cards.invent("Crypt Overseer", "{1}{B}{B}", "Creature — Zombie Wizard",
        "Skeletons you control and other Zombies you control get +1/+1 and have deathtouch. (Any amount of damage they deal to a creature is enough to destroy it.)", "2/2")
    private val banner = Cards.invent("Prismatic Pennant", "{3}", "Artifact", """
        As this artifact enters, choose a color.
        Creatures you control of the chosen color get +1/+0.
        {T}: Add one mana of the chosen color.
    """)
    private val rally = Cards.invent("Vanguard Rally", "{4}{G}{G}", "Sorcery", "Each creature you control gets +3/+3 until end of turn and must be blocked this turn if able.")
    private val call = Cards.invent("Dawnward Recall", "{3}{W}{W}", "Enchantment",
        "Whenever a nontoken, non-Angel creature you control dies, return that card to the battlefield under its owner's control with a +1/+1 counter on it. It has flying and is an Angel in addition to its other types.")
    private val wastes = Cards.invent("Stirring Wastes", null, "Land", """
        {T}: Add {C}.
        {4}: Put two +1/+1 counters on this land. Then you may have it become a 0/0 Elemental creature until end of turn. It's still a land.
    """)
    private val husk = Cards.invent("Bulwark Husk", "{1}{W}", "Artifact Creature — Wall", """
        Defender (This creature can't attack.)
        You have hexproof. (You can't be the target of spells or abilities your opponents control.)
        Prevent all noncombat damage that would be dealt to other creatures you control.
    """, "0/4")
    private val oracle = Cards.invent("Oracle of Last Words", "{5}{U}{U}", "Creature — Sphinx", """
        This spell can't be countered.
        Flying
        Hexproof (This creature can't be the target of spells or abilities your opponents control.)
        Instant and sorcery spells you control can't be countered.
    """, "5/5")
    private val shambler = Cards.invent("Gravewalk Shambler", "{1}{B}", "Creature — Zombie", "", "1/1")
    private val soldier = Cards.invent("Rattlebone Soldier", "{1}{B}", "Creature — Skeleton", "", "1/1")
    private val hound = Cards.invent("Ember Hound", "{1}{R}", "Creature — Dog", "", "2/2")
    private val mule = Cards.invent("Thornback Mule", "{1}{G}", "Creature — Beast", "", "2/2")
    private val reaping = Cards.invent("Quiet Reaping", "{B}", "Instant", "Destroy target creature.")
    private val jab = Cards.invent("Vexing Jab", "{R}", "Instant", "Vexing Jab deals 2 damage to any target.")
    private val veto = Cards.invent("Muffling Veto", "{U}", "Instant", "Counter target spell.")
    private val enemyZombie = Cards.invent("Dustgrave Zombie", "{1}{B}", "Creature — Zombie", "", "1/1")
    private val boggart = Cards.invent("Cinder Boggart", "{1}{R}", "Creature — Goblin", "", "2/2")
    private val ox = Cards.invent("Ashen Ox", "{G}", "Creature — Ox", "", "1/1")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 608.2c, 611.2a: the controller chooses on resolution; the target gets +2/+0 until end of turn
    @Test
    fun shamanPumpsTheTargetWhenTheControllerAgrees() = scenario {
        alice.battlefield(shaman, mule)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.answers(Answer.Targets(listOf(target(mule))))
        allPass()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            mule.power shouldBe 4
            mule.toughness shouldBe 2
        }
    }

    // CR 608.2c: declining the "may" changes nothing
    @Test
    fun shamanPumpsNothingWhenTheControllerDeclines() = scenario {
        alice.battlefield(shaman, mule)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.answers(Answer.Targets(listOf(target(mule))))
        allPass()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { mule.power shouldBe 2 }
    }

    // CR 613.4c, 702.2: Skeletons and the other Zombies you control, not the baron and not the opponent's
    @Test
    fun baronBoostsSkeletonsAndOtherZombiesOfItsControllerOnly() = scenario {
        alice.battlefield(baron, shambler, soldier, mule)
        bob.battlefield(enemyZombie)
        expect {
            baron.power shouldBe 2
            baron.toughness shouldBe 2
            baron lacksKeyword Keyword.Deathtouch.kind
            soldier.power shouldBe 2
            soldier hasKeyword Keyword.Deathtouch.kind
            mule.power shouldBe 2
            mule lacksKeyword Keyword.Deathtouch.kind
        }
        expect {
            shambler.power shouldBe 2
            shambler hasKeyword Keyword.Deathtouch.kind
            enemyZombie.power shouldBe 1
            enemyZombie lacksKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 105.1, 614.12, 613.4c: the color is chosen as it enters; only creatures of that color are boosted
    @Test
    fun bannerBoostsCreaturesOfTheChosenColor() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", hound, mule)
        bob.battlefield(boggart)
        alice.hand(banner)
        alice.casts(banner)
        resolveAll()
        alice.answers(Answer.ColorChoice(Color.Red))
        expect {
            alice.battlefield has banner
            hound.power shouldBe 3
            mule.power shouldBe 2
            boggart.power shouldBe 2
        }
    }

    // CR 106.1b: the banner taps for one mana of the chosen color
    @Test
    fun bannerMakesManaOfTheChosenColor() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(banner, hound)
        alice.casts(banner)
        resolveAll()
        alice.answers(Answer.ColorChoice(Color.Red))
        alice.activates(banner, color = Color.Red)
        expect { state.player(alice.id).manaPool.map { it.color } shouldBe listOf(Color.Red) }
    }

    // CR 509.1c, 611.2c, 613.4c: both creatures get +3/+3 and the one that attacks has to be blocked
    @Test
    fun rallyPumpsEveryCreatureAndForcesABlock() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", mule, ox)
        bob.battlefield(boggart)
        alice.hand(rally)
        alice.casts(rally)
        resolveAll()
        expect {
            mule.power shouldBe 5
            ox.power shouldBe 4
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mule)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks() }
        bob.blocks(boggart to mule)
        settle()
        expect { bob.life shouldBe 20 }
    }

    // CR 603.10a, 400.7, 122.1, 613.1d, 613.1f: the card comes back as a 3/3 flying Angel
    @Test
    fun callReturnsADeadCreatureAsAFlyingAngel() = scenario {
        alice.battlefield(call, "Swamp", mule)
        alice.hand(reaping)
        alice.casts(reaping, target = mule)
        resolveAll()
        expect {
            alice.battlefield has mule
            mule.power shouldBe 3
            mule.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            mule hasKeyword Keyword.Flying.kind
            mule.subtypes shouldBe listOf(Subtype("Beast"), Subtype("Angel"))
        }
    }

    // CR 603.10a: the returned Angel isn't a non-Angel creature, so it doesn't come back a second time
    @Test
    fun callDoesNotReturnAnAngelAgain() = scenario {
        alice.battlefield(call, "Swamp", "Swamp", mule)
        alice.hand(reaping, reaping)
        alice.casts(reaping, target = mule)
        resolveAll()
        alice.casts(reaping, target = mule)
        resolveAll()
        expect { alice.graveyard has mule }
    }

    // CR 603.10a: only creatures its controller controls
    @Test
    fun callIgnoresTheOpponentsCreatures() = scenario {
        alice.battlefield(call, "Swamp")
        bob.battlefield(boggart)
        alice.hand(reaping)
        alice.casts(reaping, target = boggart)
        resolveAll()
        expect { bob.graveyard has boggart }
    }

    // CR 602.2, 122.1, 613.1d, 613.4b, 205.1b: two counters, then it may become a 2/2 Elemental creature that is still a land
    @Test
    fun wastesGrowsAndMayBecomeACreature() = scenario {
        alice.battlefield(wastes, "Forest", "Forest", "Forest", "Forest")
        alice.activates(wastes, ability = 1, paying = listOf("Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            wastes.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            wastes.power shouldBe 2
            wastes.toughness shouldBe 2
            wastes.types shouldBe listOf(CardType.Land, CardType.Creature)
            wastes.subtypes shouldBe listOf(Subtype("Elemental"))
        }
    }

    // CR 608.2c: declining leaves a land with counters
    @Test
    fun wastesStaysALandWhenDeclined() = scenario {
        alice.battlefield(wastes, "Forest", "Forest", "Forest", "Forest")
        alice.activates(wastes, ability = 1, paying = listOf("Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            wastes.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            wastes.types shouldBe listOf(CardType.Land)
        }
    }

    // CR 702.11c: Bob can't target the player; CR 615.1: noncombat damage to the other creatures is prevented, not to the husk or Bob's creatures
    @Test
    fun barricadeProtectsItsControllerAndOtherCreatures() = scenario {
        alice.battlefield(husk, mule)
        bob.battlefield("Mountain", "Mountain", "Mountain", ox)
        bob.hand(jab, jab, jab)
        alice.passes()
        bob.casts(jab)
        expect { decision<Decision.Targets>().options.contains(target("alice")) shouldBe false }
        bob.answers(Answer.Targets(listOf(target(mule))))
        resolveAll()
        expect {
            mule.zone shouldBe Zone.Battlefield
            card(mule).view.obj!!.damage shouldBe 0
        }
    }

    // CR 615.1: Alice's own burn against Bob's creature is not prevented
    @Test
    fun barricadeDoesNotProtectTheOpponentsCreatures() = scenario {
        alice.battlefield(husk, "Mountain")
        bob.battlefield(ox)
        alice.hand(jab)
        alice.casts(jab, target = ox)
        resolveAll()
        expect { bob.graveyard has ox }
    }

    // CR 101.2, 701.6: Bob's counterspell does nothing against Alice's instant
    @Test
    fun oracleKeepsInstantsFromBeingCountered() = scenario {
        alice.battlefield(oracle, "Mountain")
        alice.hand(jab)
        bob.battlefield("Island")
        bob.hand(veto)
        alice.casts(jab, target = "bob")
        alice.passes()
        bob.casts(veto, target = jab)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 101.2, 701.6: it only protects instants and sorceries, so a creature spell is still countered
    @Test
    fun oracleDoesNotKeepCreatureSpellsFromBeingCountered() = scenario {
        alice.battlefield(oracle, "Forest", "Forest")
        alice.hand(mule)
        bob.battlefield("Island")
        bob.hand(veto)
        alice.casts(mule)
        alice.passes()
        bob.casts(veto, target = mule)
        resolveAll()
        expect { alice.graveyard has mule }
    }
}
