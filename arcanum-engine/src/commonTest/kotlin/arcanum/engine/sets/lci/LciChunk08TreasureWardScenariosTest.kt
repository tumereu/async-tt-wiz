package arcanum.engine.sets.lci

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals

/** LCI chunk 08: Treasure and ward cards played: an upkeep exile whose two "if"s look at what was exiled, a trigger on being dealt
 * combat damage that hands the artifact to the attacking player, "that many" Treasures for a batch of attacking Dinosaurs, a target for
 * each player turned into a Treasure artifact while the creature stays, counters for power above base power, a chosen creature type's
 * triggers doubled, and a counter only for a creature that entered this turn before a fight. Invented names carry the oracle text. */
class LciChunk08TreasureWardScenariosTest {
    private val drake = Cards.invent("Hoardmaw Drake", "{3}{R}{R}", "Creature — Dinosaur Dragon", """
        Flying, first strike
        At the beginning of your upkeep, exile the top two cards of your library. You may play them this turn. If you exiled a land card this way, create a 3/1 red Dinosaur creature token. If you exiled a nonland card this way, create a Treasure token.
    """, "5/5")
    private val orb = Cards.invent("Disputed Trophy Orb", "{2}", "Artifact", """
        Whenever you're dealt combat damage, the attacking player gains control of this artifact and untaps it.
        {2}, {T}: Draw a card and put a point counter on this artifact. Then if it has five or more point counters on it, sacrifice it and create a Treasure token.
    """)
    private val windfall = Cards.invent("Saurian Windfall", "{2}{R}", "Enchantment", """
        Whenever one or more Dinosaurs you control attack, create that many Treasure tokens.
        Whenever you cast an artifact spell, create a 3/1 red Dinosaur creature token. This ability triggers only once each turn.
    """)
    private val pilferer = Cards.invent("Skysail Pilferer", "{2}{U}", "Creature — Human Pirate", """
        Flying, ward {1}
        When this creature enters, for each player, choose up to one other target artifact or creature that player controls. For as long as this creature remains on the battlefield, the chosen permanents become Treasure artifacts with "{T}, Sacrifice this artifact: Add one mana of any color" and lose all other abilities.
    """, "2/3")
    private val regent = Cards.invent("Regent Itzal Moch", "{2}{G}{W}", "Legendary Creature — Cat Noble", """
        Ward {2}
        Whenever Regent Itzal Moch attacks, for each creature you control with power greater than that creature's base power, put a number of +1/+1 counters on that creature equal to the difference.
    """, "3/4")
    private val dais = Cards.invent("Wandering Dais", "{4}", "Artifact Creature — Golem", """
        Ward {2}
        As this creature enters, choose a creature type.
        This creature is the chosen type in addition to its other types.
        If a triggered ability of another creature you control of the chosen type triggers, it triggers an additional time.
    """, "4/4")
    private val sigil = Cards.invent("Jungle War Sigil", "{G}", "Sorcery",
        "Choose target creature you control and target creature you don't control. If the creature you control entered this turn, put a +1/+1 counter on it. Then those creatures fight each other.")

    private val spit = Cards.invent("Searing Jab", "{R}", "Instant", "Searing Jab deals 3 damage to target creature.")
    private val ridgeback = Cards.invent("Ridgeback Saur", "{1}{R}", "Creature — Dinosaur", "", "2/2")
    private val crestback = Cards.invent("Crestback Saur", "{1}{R}", "Creature — Dinosaur", "", "2/2")
    private val ox = Cards.invent("Quarry Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val strider = Cards.invent("Mire Strider", "{1}{G}", "Creature — Beast", "", "2/2")
    private val stalker = Cards.invent("Thicket Stalker", "{G}", "Creature — Cat", "", "2/2")
    private val gull = Cards.invent("Squall Gull", "{1}{U}", "Creature — Bird", "Flying", "2/2")
    private val totem = Cards.invent("Brass Lantern Totem", "{2}", "Artifact", "{T}: You gain 1 life.")
    private val lookout = Cards.invent("Canopy Lookout", "{G}", "Creature — Elf Scout", "When this creature enters, draw a card.", "1/1")
    private val scout = Cards.invent("Gladeborn Scout", "{G}", "Creature — Human Scout", "When this creature enters, draw a card.", "1/1")

    // CR 406.1, 601.2a: a land and a nonland card exiled make both tokens, and the land can be played this turn (CR 305.1)
    @Test
    fun drakeExilesTwoAndMakesBothTokens() = scenario {
        alice.battlefield(drake)
        aliceUpkeep("Mountain", spit)
        resolveAll()
        expect {
            alice.exile has "Mountain"
            alice.exile has spit
            tokens("Dinosaur Token").size shouldBe 1
            tokens("Treasure Token").size shouldBe 1
        }
        advanceTo(TurnStep.PrecombatMain)
        alice.plays("Mountain")
        expect { alice.battlefield has "Mountain" }
    }

    // CR 608.2c: two lands exiled make the Dinosaur only
    @Test
    fun drakeMakesNoTreasureForTwoLands() = scenario {
        alice.battlefield(drake)
        aliceUpkeep("Mountain", "Forest")
        resolveAll()
        expect {
            tokens("Dinosaur Token").size shouldBe 1
            tokens("Treasure Token").size shouldBe 0
        }
    }

    // CR 510.2, 603.2c: two attackers' combat damage triggers it once; CR 506.2: the active player attacked, so Bob gains control (not
    // Carol), and it untaps (CR 701.26b)
    @Test
    fun orbGoesToTheAttackingPlayer() = scenario(players = 3) {
        alice.battlefield(orb, tapped = true)
        bob.battlefield(ridgeback, crestback)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ridgeback, crestback, defender = "alice")
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == alice.id) alice.blocks()
        advanceTo(TurnStep.CombatDamage)
        expect {
            alice.life shouldBe 16
            stack.size shouldBe 1
        }
        resolveAll()
        expect {
            orb.controller shouldBe bob.id
            orb.tapped shouldBe false
        }
    }

    // CR 603.2c, 508.1m: one trigger for the attacking Dinosaurs; "that many" counts the two of them, not the Ox
    @Test
    fun windfallMakesATreasurePerAttackingDinosaur() = scenario {
        alice.battlefield(windfall, ridgeback, crestback, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ridgeback, crestback, ox)
        resolveAll()
        expect { tokens("Treasure Token").size shouldBe 2 }
    }

    // CR 601.2c: one target for each player (Bob's creature, Carol's artifact); CR 205.1a, 613.1d, 613.1f: they become Treasure artifacts
    // only, with the mana ability and nothing else, until the Pilferer leaves (CR 611.2b)
    @Test
    fun pilfererTurnsOneOfEachPlayersIntoATreasure() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Mountain")
        alice.hand(pilferer, spit)
        bob.battlefield(gull)
        carol.battlefield(totem)
        alice.casts(pilferer)
        resolveAll()
        decision<Decision.Targets>()
        alice.answers(Answer.Targets(listOf(target(gull))))
        decision<Decision.Targets>()
        alice.answers(Answer.Targets(listOf(target(totem))))
        resolveAll()
        expect {
            gull.types shouldBe listOf(CardType.Artifact)
            gull.subtypes shouldBe listOf(Subtype("Treasure"))
            gull lacksKeyword Keyword.Flying.kind
            gull.power shouldBe null
            assertEquals(1, gull.view.characteristics!!.abilities.count { it.ability is Ability.Activated })
            totem.subtypes shouldBe listOf(Subtype("Treasure"))
            assertEquals(1, totem.view.characteristics!!.abilities.count { it.ability is Ability.Activated })
            gull.controller shouldBe bob.id
        }
        alice.casts(spit, target = pilferer)
        resolveAll()
        expect {
            alice.graveyard has pilferer
            gull.types shouldBe listOf(CardType.Creature)
            gull hasKeyword Keyword.Flying.kind
        }
    }

    // CR 208.4b: a +1/+1 counter and a +2/+0 effect raise power above base power (counters for the difference); an effect setting base
    // power to 5 doesn't, nor does the Regent itself
    @Test
    fun regentMakesUpTheDifferenceToBasePower() = scenario {
        alice.battlefield(regent, strider, stalker)
        alice.battlefield(ox, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), on = listOf(strider))
        effect(alice, Mechanic.SetPowerToughness(Value.Fixed(5), Value.Fixed(5)), on = listOf(stalker))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(regent)
        resolveAll()
        expect {
            ox.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            strider.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            stalker.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            regent.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 614.12: Elf chosen as it enters; CR 603.2d: the Elf's enters trigger triggers twice, the Human's once
    @Test
    fun daisDoublesTheChosenTypesTriggers() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Forest", "Forest")
        alice.hand(dais, lookout, scout)
        alice.library("Plains", "Plains", "Plains", "Plains")
        alice.casts(dais, paying = listOf("Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        decision<Decision.ChooseSubtype>()
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        resolveAll()
        expect { dais.subtypes.contains(Subtype("Elf")) shouldBe true }
        alice.casts(lookout)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        alice.casts(scout)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    // CR 608.2c, 701.14a: the creature cast this turn gets the counter, then deals 3 to Bob's 2/2 and survives its 2
    @Test
    fun sigilCountersACreatureThatEnteredThisTurn() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(stalker, sigil)
        bob.battlefield(ox)
        alice.casts(stalker)
        resolveAll()
        alice.casts(sigil, targets = listOf(stalker, ox))
        resolveAll()
        expect {
            stalker.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bob.graveyard has ox
            alice.battlefield has stalker
        }
    }

    // CR 608.2c: a creature that was already there gets no counter; the two 2/2s destroy each other (CR 704.5g)
    @Test
    fun sigilGivesNoCounterToAnOldCreature() = scenario {
        alice.battlefield("Forest", strider)
        alice.hand(sigil)
        bob.battlefield(ox)
        alice.casts(sigil, targets = listOf(strider, ox))
        resolveAll()
        expect {
            alice.graveyard has strider
            bob.graveyard has ox
        }
    }
}
