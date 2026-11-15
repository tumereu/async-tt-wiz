package arcanum.engine.sets.trk

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.view.Subtypes
import kotlin.test.Test

/** Star Trek chunk 07 in play: Federation (the number of creature types among non-Borg creatures you control, CR 205.3m; a changeling brings
 * every creature type, CR 702.73a), a card for every five types and "if you draw three or more cards this way", mana equal to it, copies of a
 * card cast free, a tapped copy of a token, kicker that changes targets, kicker by sacrifice, and a dies trigger only during your turn. */
class TrkChunk07FederationInvestigateKickerScenariosTest {
    private val voss = Cards.invent("Captain Delmar Voss", "{2}{G}", "Legendary Creature — Human Officer", """
        When Captain Voss enters, create Rufus, a legendary 1/1 green Dog creature token.
        Federation — {G}, {T}: Target creature you control gets +X/+X until end of turn, where X is the number of creature types among non-Borg creatures you control. Activate only as a sorcery.
    """, "3/3")
    private val elf = Cards.invent("Trk7 Elf Ranger", "{G}", "Creature — Elf Warrior", "", "1/1")
    private val elfTwin = Cards.invent("Trk7 Elf Scout", "{G}", "Creature — Elf Scout", "", "1/1")
    private val drone = Cards.invent("Trk7 Borg Drone", "{2}", "Artifact Creature — Borg Vulcan", "", "2/2")
    private val goblin = Cards.invent("Trk7 Goblin Raider", "{R}", "Creature — Goblin Pirate", "", "2/1")
    private val mimic = Cards.invent("Trk7 Shifting Mimic", "{1}", "Creature — Shapeshifter", "Changeling (This card is every creature type.)", "1/1")
    private val census = Cards.invent("Trk7 Census Taker", "{1}{W}", "Creature — Human Advisor",
        "{T}: You gain X life, where X is the number of creature types among creatures you control.", "1/1")
    private val solvar = Cards.invent("Solvar, Federation Envoy", "{1}{G}{W}{U}", "Legendary Creature — Vulcan Advisor",
        "Federation — At the beginning of combat on your turn, draw a card for every five creature types among non-Borg creatures you control. If you draw three or more cards this way, put two +1/+1 counters on each creature you control.",
        "0/5")
    private val crowd = Cards.invent("Trk7 Motley Crowd", "{2}", "Creature — Human Elf Dwarf Giant Goblin", "", "1/1")
    private val horde = Cards.invent("Trk7 Motley Horde", "{2}", "Creature — Orc Troll Ogre Kor Merfolk", "", "1/1")
    private val haunt = Cards.invent("Trk7 Motley Haunt", "{2}", "Creature — Vampire Zombie Spirit", "", "1/1")
    private val pair = Cards.invent("Trk7 Motley Pair", "{2}", "Creature — Vampire Zombie", "", "1/1")
    private val college = Cards.invent("Fleet Cadet College", null, "Legendary Land", """
        {T}: Add {C}.
        Federation — {5}, {T}: Choose a color. Add an amount of mana of that color equal to the number of creature types among non-Borg creatures you control.
    """)
    private val variety = Cards.invent("Boundless Variety", "{7}{R}{R}", "Sorcery",
        "Federation — Exile target instant or sorcery card from your graveyard. Copy that card X times, where X is the number of creature types among non-Borg creatures you control. You may cast the copies without paying their mana costs. Exile Boundless Variety.")
    private val tonic = Cards.invent("Trk7 Tonic", "{3}{W}", "Instant", "You gain 2 life.")
    private val duplicator = Cards.invent("Duplicator Unit", "{2}", "Artifact", """
        When this artifact enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        {2}, {T}: Create a tapped token that's a copy of target token you control.
    """)
    private val incursion = Cards.invent("Shadow Realm Incursion", "{1}{U}{B}", "Sorcery", """
        Kicker {4}{U}{B} (You may pay an additional {4}{U}{B} as you cast this spell.)
        Create a token that's a copy of target creature an opponent controls, except it has menace. If this spell was kicked, instead for each creature target opponent controls, create a token that's a copy of that creature, except it has menace.
    """)
    private val vent = Cards.invent("Vent the Reactor Core", "{1}{B}", "Instant", """
        Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
        Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -6/-6 until end of turn instead.
    """)
    private val brute = Cards.invent("Trk7 Hulking Brute", "{4}{G}", "Creature — Beast", "", "5/5")
    private val irrational = Cards.invent("Most Irrational", "{1}{U}", "Instant", """
        Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
        Counter target spell with mana value 2 or less. If this spell was kicked, instead counter target spell.
    """)
    private val bolt = Cards.invent("Trk7 Plasma Volley", "{2}{R}", "Sorcery", "Trk7 Plasma Volley deals 3 damage to each opponent.")
    private val jolt = Cards.invent("Trk7 Static Jolt", "{R}", "Instant", "Trk7 Static Jolt deals 1 damage to each opponent.")
    private val recruit = Cards.invent("Fleet Guard Recruit", "{R}", "Creature — Human Officer", """
        Haste
        When this creature dies during your turn, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
    """, "1/1")
    private val zap = Cards.invent("Trk7 Phaser Zap", "{R}", "Instant", "Trk7 Phaser Zap deals 1 damage to target creature.")

    // ---- Federation

    // CR 205.3m: Human, Officer, Elf, Warrior and Scout; a shared type counts once, a Borg's types and an opponent's creatures not at all
    @Test
    fun federationCountsCreatureTypesAmongYourNonBorgCreatures() = scenario(players = 3) {
        alice.battlefield(voss, elf, elfTwin, drone, "Forest")
        bob.battlefield(goblin)
        alice.activates(voss, target = elf)
        resolveAll()
        expect { elf.power shouldBe 6 }
    }

    // CR 702.73a: a changeling is every creature type, Borg too, so it isn't a non-Borg creature: only Human and Officer count
    @Test
    fun aChangelingIsBorgSoFederationLeavesItOut() = scenario {
        alice.battlefield(voss, mimic, "Forest")
        alice.activates(voss, target = mimic)
        resolveAll()
        expect { mimic.power shouldBe 3 }
    }

    // CR 702.73a, 205.3m: without the Borg exception, a changeling brings every creature type to the count
    @Test
    fun aChangelingBringsEveryCreatureTypeToACount() = scenario {
        alice.battlefield(census, mimic)
        alice.activates(census)
        resolveAll()
        expect { alice.life shouldBe 20 + Subtypes.creatureTypeCount }
    }

    private fun Scenario.solvarAtCombat(vararg creatures: String, library: Int) {
        alice.battlefield(solvar, *creatures)
        alice.library(*Array(library) { "Island" })
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
    }

    // CR 107.1a, 121.2: fifteen types draw three cards, so each creature you control gets two +1/+1 counters
    @Test
    fun fifteenTypesDrawThreeAndPutCounters() = scenario {
        solvarAtCombat(crowd, horde, haunt, library = 4)
        expect {
            alice.hand.size shouldBe 3
            solvar.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            haunt.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 107.1a: fourteen types draw two (rounded down), no counters
    @Test
    fun fourteenTypesDrawTwo() = scenario {
        solvarAtCombat(crowd, horde, pair, library = 4)
        expect {
            alice.hand.size shouldBe 2
            solvar.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 121.2, 121.4: with two cards in the library only two are drawn this way, so no counters
    @Test
    fun drawingFewerThanThreeGivesNoCounters() = scenario {
        solvarAtCombat(crowd, horde, haunt, library = 2)
        expect {
            alice.hand.size shouldBe 2
            solvar.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 106.1b, 205.3m: Human, Officer, Elf and Warrior: four mana of the chosen color
    @Test
    fun collegeAddsManaEqualToTheTypes() = scenario {
        alice.battlefield(college, voss, elf, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(college, ability = 1, color = Color.Red)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        expect { alice.pool.size shouldBe 4 }
    }

    // CR 707.12, 608.2g: three copies of the exiled instant, each cast without paying its mana cost; the sorcery exiles itself (CR 608.2c)
    @Test
    fun copiesOfTheExiledCardAreCastFree() = scenario {
        alice.battlefield(voss, elf)
        alice.graveyard(tonic)
        alice.hand(variety)
        alice.castsWithoutCosts(variety, target = tonic)
        allPass()
        // Human, Officer, Elf, Warrior: four copies
        var cast = 0
        while (pending != null && cast < 10) {
            when (val d = pending) {
                is Decision.ChooseObjects -> answer(Answer.Objects(listOf(d.options.first())))
                is Decision.May -> { cast++; answer(Answer.YesNo(true)) }
                else -> error("unexpected $d")
            }
        }
        expect { stack.names.size shouldBe 4 }
        resolveAll()
        expect {
            alice.life shouldBe 28
            alice.exile has variety
            alice.exile has tonic
            alice.graveyard.size shouldBe 0
        }
    }

    // ---- Food and Clue tokens

    // CR 707.2, 111.10b: a tapped copy of a Food token
    @Test
    fun duplicatorMakesATappedCopyOfAToken() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(duplicator)
        alice.casts(duplicator)
        resolveAll()
        alice.activates(duplicator, target = "Food Token")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Food Token" } shouldBe 2
            card("Food Token", 1).tapped shouldBe true
            card("Food Token", 0).tapped shouldBe false
        }
    }

    // CR 603.2, 603.6c: dies during your turn: a Clue; during an opponent's turn: nothing
    @Test
    fun recruitInvestigatesOnlyWhenItDiesDuringYourTurn() = scenario(players = 3) {
        alice.battlefield(recruit, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = recruit)
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
    }

    @Test
    fun recruitDyingOnAnOpponentsTurnMakesNoClue() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(recruit)
        bob.battlefield("Mountain")
        bob.hand(zap)
        bob.casts(zap, target = recruit)
        resolveAll()
        expect {
            alice.graveyard has recruit
            alice.battlefield lacks "Clue Token"
        }
    }

    // ---- Kicker

    // CR 702.33d, 601.2c, 707.9b: unkicked, one copy of the target creature, with menace
    @Test
    fun incursionUnkickedCopiesOneCreature() = scenario(players = 3) {
        alice.battlefield("Island", "Swamp", "Swamp")
        bob.battlefield(goblin, brute)
        alice.hand(incursion)
        alice.casts(incursion, target = brute, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield.size shouldBe 4
            stack.names shouldBe emptyList()
        }
        expect { card(brute, 1) hasKeyword Keyword.Menace.kind }
    }

    // CR 702.33d, 601.2c: kicked, the target is an opponent and each creature they control is copied with menace; another opponent's aren't
    @Test
    fun incursionKickedCopiesEachCreatureOfTheTargetOpponent() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Swamp", "Swamp", "Island", "Swamp", "Swamp")
        bob.battlefield(goblin, brute)
        carol.battlefield(elf)
        alice.hand(incursion)
        alice.casts(incursion, target = "bob", kicked = 1)
        resolveAll()
        expect {
            alice.battlefield.size shouldBe 11
            card(goblin, 1) hasKeyword Keyword.Menace.kind
            card(brute, 1) hasKeyword Keyword.Menace.kind
            carol.battlefield.size shouldBe 1
        }
    }

    // CR 702.33a: kicked by sacrificing a creature, -6/-6 kills a 5/5
    @Test
    fun ventKickedBySacrificeGivesMinusSix() = scenario {
        alice.battlefield("Swamp", "Swamp", elf, elfTwin)
        bob.battlefield(brute)
        alice.hand(vent)
        alice.casts(vent, target = brute, kicked = 1, choosing = listOf(elf))
        resolveAll()
        expect {
            alice.graveyard has elf
            bob.graveyard has brute
        }
    }

    // CR 702.33d: unkicked, -2/-2
    @Test
    fun ventUnkickedGivesMinusTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", elf)
        bob.battlefield(brute)
        alice.hand(vent)
        alice.casts(vent, target = brute, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has elf
            brute.power shouldBe 3
        }
    }

    // CR 601.2c, 702.33d: unkicked it can target only a spell with mana value 2 or less
    @Test
    fun irrationalUnkickedTargetsOnlyASmallSpell() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(bolt, jolt)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(irrational)
        bob.casts(bolt, paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        bob.casts(jolt, paying = listOf("Mountain#3"))
        bob.passes()
        carol.passes()
        alice.casts(irrational, kicked = 0)
        val choice = decision<Decision.Targets>()
        expect {
            choice.options.size shouldBe 1
            choice.options.single() shouldBe target(jolt)
        }
    }

    // CR 702.33d: kicked, it counters a spell with mana value 3
    @Test
    fun irrationalKickedCountersABiggerSpell() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(bolt)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(irrational)
        bob.casts(bolt)
        bob.passes()
        carol.passes()
        alice.casts(irrational, target = bolt, kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has bolt
            alice.life shouldBe 20
            carol.life shouldBe 20
        }
    }
}
