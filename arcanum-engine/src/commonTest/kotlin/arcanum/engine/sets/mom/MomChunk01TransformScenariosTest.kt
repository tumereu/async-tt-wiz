package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM chunk 01: transforming double-faced cards and Sieges, part 1: combat damage to a battle, a sacrificed permanent's mana value, a
 * graveyard return with haste until it is exiled, a transform-or-dies trigger, every player exiling until a nonland card, poison equal to
 * the damage, a cost reduction for the opponents' draws, "since your last turn" with three players, a permanent put into a graveyard this
 * turn, playing a card exiled from an opponent's hand, damage assigned as though unblocked, two creature tokens attacking together, a
 * reflexive shuffle sized by the creatures tapped and "exactly two colors". */
class MomChunk01TransformScenariosTest {
    private val stinger = Cards.inventFaces("Transform",
        face("Veilblade Courier", "{1}{B}", "Creature — Human Rogue", """
            Deathtouch
            {4}{U/P}: Transform this creature. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
        """.trimIndent(), "1/1", null, null),
        face("Glassmind Stinger", null, "Creature — Phyrexian Rogue", """
            Deathtouch
            Whenever this creature deals combat damage to a player or battle, draw a card.
        """.trimIndent(), "3/3", null, "U"))
    private val fen = Cards.inventFaces("Transform",
        OracleCard("Siege of the Glass Fen", "{2}{R}", "Battle — Siege", "", defense = "5"),
        OracleCard("Glass Fen Warden", null, "Creature — Elemental", "", "2/2", colorIndicator = "R"))
    private val vessa = Cards.inventFaces("Transform",
        face("Vessa, Widow of Ash", "{1}{B}{B}", "Legendary Creature — Elf Noble", """
            {T}, Sacrifice another creature or artifact: Vessa deals X damage to target opponent or battle and you gain X life, where X is the sacrificed permanent's mana value.
            {5}{R/P}: Transform Vessa. Activate only as a sorcery. ({R/P} can be paid with either {R} or 2 life.)
        """.trimIndent(), "3/3", null, null),
        face("Vessa, Kiln Queen", null, "Legendary Creature — Phyrexian Elf Noble",
            "At the beginning of combat on your turn, return up to one target artifact or creature card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step.",
            "4/4", null, "R"))
    private val sporeling = Cards.inventFaces("Transform",
        face("Rotreaper Sporeling", "{1}{B}", "Creature — Fungus", "{3}{G/P}: Transform this creature. Activate only as a sorcery. ({G/P} can be paid with either {G} or 2 life.)", "2/2", null, null),
        face("Rotsower Sporeling", null, "Creature — Phyrexian Fungus", "When this creature transforms into Rotsower Sporeling or dies, create a 1/1 green Phyrexian Saproling creature token.", "3/3", null, "G"))
    private val okkar = Cards.inventFaces("Transform",
        face("Okkar, Primal Tyrant", "{5}{R}{R}", "Legendary Creature — Elder Dinosaur", """
            Trample
            When Okkar enters, each player exiles cards from the top of their library until they exile a nonland card. You may cast any number of spells from among the nonland cards exiled this way without paying their mana costs.
            {9}{G/P}: Transform Okkar. Activate only as a sorcery.
        """.trimIndent(), "7/7", null, null),
        face("Okkar, Primal Blight", null, "Legendary Creature — Phyrexian Elder Dinosaur", """
            Trample, indestructible
            Whenever Okkar deals combat damage to a player, they get that many poison counters. (A player with ten or more poison counters loses the game.)
        """.trimIndent(), "11/11", null, "G"))
    private val solmara = Cards.inventFaces("Transform",
        face("Solmara, the Bright Dawn", "{2}{W}{W}", "Legendary Enchantment Creature — God", """
            When Solmara enters, return target enchantment card that isn't a God from your graveyard to your hand.
            {3}{U/P}: Transform Solmara. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
        """.trimIndent(), "4/4", null, null),
        face("Solmara, the Bent Eclipse", null, "Legendary Enchantment Creature — Phyrexian God", """
            You may cast spells as though they had flash.
            Spells you cast cost {1} less to cast for each card your opponents have drawn this turn.
        """.trimIndent(), "4/6", null, "U"))
    // the back faces of Sieges, alone (a Siege's back face is reached by defeating it, CR 310.11b)
    private val cinder = Cards.invent("Momentide Reaper", "{B}{R}", "Creature — Zombie Elemental", """
        Menace
        At the beginning of your end step, put a +1/+1 counter on this creature if a permanent was put into a graveyard from the battlefield this turn.
    """.trimIndent(), "2/1")
    private val ilsabet = Cards.invent("Momentide Monarch", "{4}{B}{B}", "Legendary Creature — Human Noble", """
        Menace, deathtouch
        At the beginning of your upkeep, if you haven't been dealt combat damage since your last turn, you draw a card and you lose 1 life.
    """.trimIndent(), "3/6")
    private val sandmere = Cards.inventFaces("Transform",
        OracleCard("Invasion of Sandmere", "{1}{W}", "Battle — Siege",
            "When this Siege enters, look at target opponent's hand. You may exile a nonland card from it. For as long as that card remains exiled, its owner may play it. A spell cast this way costs {2} more to cast.",
            defense = "3"),
        OracleCard("Glintshield Array", null, "Enchantment", "Sacrifice this enchantment: Creatures you control gain hexproof and indestructible until end of turn.", colorIndicator = "W"))
    private val grothmaw = Cards.invent("Momentide Apex", "{X}{G}{G}", "Legendary Creature — Dinosaur", """
        Reach
        For each non-Human creature you control, you may have that creature assign its combat damage as though it weren't blocked.
    """.trimIndent(), "8/8")
    // the back face's text on a front face, to cast it
    private val tagCall = Cards.invent("Momentide Tag Call", "{2}{R}{W}", "Sorcery",
        "Create two 3/2 red and white Warrior creature tokens with \"Whenever this token and at least one other creature token attack, put a +1/+1 counter on this token.\"")
    private val aldric = Cards.inventFaces("Transform",
        OracleCard("Invasion of Glimmerhold", "{X}{W}{U}", "Battle — Siege", "", defense = "6"),
        OracleCard("Aldric Vanth of Glimmerhold", null, "Legendary Planeswalker — Teferi", """
            [−3]: Tap any number of untapped creatures you control. When you do, shuffle target nonland permanent an opponent controls with mana value X or less into its owner's library, where X is the number of creatures tapped this way.
        """.trimIndent(), loyalty = "4", colorIndicator = "WU"))
    private val concordia = Cards.inventFaces("Transform",
        OracleCard("Invasion of Concordia", "{5}", "Battle — Siege", "When this Siege enters, exile target nonland permanent an opponent controls that isn't exactly two colors.", defense = "4"),
        OracleCard("Accord Paragon", null, "Artifact Creature — Construct", "", "5/5"))

    private val bear = Cards.invent("Momentide Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val human = Cards.invent("Momentide Squire", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val wall = Cards.invent("Momentide Wall", "{1}{W}", "Creature — Wall", "Defender", "0/6")
    private val trinket = Cards.invent("Momentide Trinket", "{3}", "Artifact")
    private val zap = Cards.invent("Momentide Zap", "{R}", "Instant", "Momentide Zap deals 3 damage to any target.")
    private val ogre = Cards.invent("Momentide Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val insight = Cards.invent("Momentide Insight", "{U}", "Instant", "Target player draws two cards.")
    private val icon = Cards.invent("Momentide Icon", "{W}{U}", "Artifact", "")
    private val idol = Cards.invent("Momentide Idol", "{W}", "Artifact", "")
    private val ox = Cards.invent("Momentide Ox", "{4}{G}", "Creature — Ox", "", "4/4")

    private fun Scenario.libraries() = players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") }

    // CR 510.2, 310.6: combat damage to a battle triggers "deals combat damage to a player or battle"; the battle loses that many defense counters
    @Test
    fun theStingerDrawsAfterHittingABattle() = scenario {
        alice.battlefield(fen, counters = listOf(CounterKind.Defense to 5))
        alice.battlefield(stinger)
        backFaceUp(stinger)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stinger, defender = fen)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            fen.counters(CounterKind.Defense) shouldBe 2
        }
    }

    // CR 608.2h: X is the sacrificed artifact's mana value as it last existed; CR 115.4: the target is an opponent or a battle
    @Test
    fun vessaDealsAndGainsTheSacrificedManaValue() = scenario {
        alice.battlefield(vessa, trinket)
        libraries()
        alice.activates(vessa, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
            alice.graveyard has trinket
        }
    }

    // CR 400.7, 611.2a: the returned creature is a new object with haste for as long as it stays; CR 603.7: exiled at the next end step
    @Test
    fun theKilnQueenReturnsACreatureWithHasteUntilTheEndStep() = scenario {
        alice.battlefield(vessa)
        backFaceUp(vessa)
        alice.graveyard(ogre)
        libraries()
        advanceTo(TurnStep.BeginningOfCombat)
        answer(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.exile has ogre
            vessa.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.27a, 603.2: transforming into the back face triggers it; CR 603.10a: so does dying (it looks back)
    @Test
    fun theSporelingMakesASaprolingWhenItTransformsAndWhenItDies() = scenario {
        alice.battlefield(sporeling, "Forest", "Forest", "Forest", "Forest")
        bob.battlefield("Mountain")
        bob.hand(zap)
        libraries()
        alice.activates(sporeling, ability = 0)
        resolveAll()
        expect { alice.battlefield has "Phyrexian Saproling Token" }
        alice.passes()
        bob.casts(zap, target = sporeling)
        resolveAll()
        expect {
            alice.graveyard has sporeling
            alice.battlefield.names.count { it == "Phyrexian Saproling Token" } shouldBe 2
        }
    }

    // CR 101.4: each library in turn order, three players; CR 608.2g: the found spells are cast while the ability resolves; lands stay exiled
    @Test
    fun okkarExilesFromEveryLibraryAndCastsTheSpells() = scenario(players = 3) {
        alice.hand(okkar)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library("Island", bear)
        bob.library("Forest", "Forest", ogre)
        carol.library(trinket)
        alice.casts(okkar)
        resolveAll()
        expect {
            alice.exile has "Island"
            bob.exile has "Forest"
        }
        alice.chooses(bear, ogre, trinket)
        resolveAll()
        expect {
            bear.controller shouldBe alice.id
            ogre.controller shouldBe alice.id
            trinket.controller shouldBe alice.id
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 120.3, 122.1f: as many poison counters as the combat damage dealt
    @Test
    fun theBlightGivesPoisonEqualToItsDamage() = scenario {
        alice.battlefield(okkar)
        backFaceUp(okkar)
        libraries()
        bob.battlefield(wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(okkar)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to okkar)
        advanceTo(TurnStep.CombatDamage)
        divideAsSuggested()
        resolveAll()
        expect { bob.counters(CounterKind.Poison) shouldBe 5 }
    }

    // CR 601.2f, 121.2: two cards an opponent drew this turn make a spell cost {2} less
    @Test
    fun theBentEclipseCountsTheOpponentsDraws() = scenario {
        alice.battlefield(solmara, "Island", "Forest", "Forest", "Forest")
        backFaceUp(solmara)
        alice.hand(insight, ox)
        libraries()
        alice.casts(insight, target = "bob")
        resolveAll()
        expect { bob.hand.size shouldBe 2 }
        alice.casts(ox)
        resolveAll()
        expect { ox.zone shouldBe Zone.Battlefield }
    }

    // CR 700.4, 608.2c: no permanent went to a graveyard this turn, no counter; after one did, a counter
    @Test
    fun theReaperGrowsOnlyAfterAPermanentWentToAGraveyard() = scenario {
        alice.battlefield(cinder)
        libraries()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { cinder.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 700.4: an opponent's creature dying counts too
    @Test
    fun theReaperGrowsAfterAnOpponentsCreatureDied() = scenario {
        alice.battlefield(cinder, "Mountain")
        alice.hand(zap)
        bob.battlefield(bear)
        libraries()
        alice.casts(zap, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { cinder.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 603.4, 500.1: with three players, combat damage on the first opponent's turn still counts at your next upkeep two turns later
    @Test
    fun ilsabetDrawsNothingAfterCombatDamageSinceHerLastTurn() = scenario(players = 3) {
        alice.battlefield(ilsabet)
        bob.battlefield(bear)
        libraries()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(bear, defender = "alice")
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.4: no combat damage since her last turn: she draws and loses 1 life
    @Test
    fun ilsabetDrawsWhenNotDealtCombatDamage() = scenario(players = 3) {
        alice.battlefield(ilsabet)
        libraries()
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
        }
    }

    // CR 400.7, 601.2f: the card's owner may play it from exile, paying {2} more
    @Test
    fun theSandmereOwnerPlaysTheExiledCardForTwoMore() = scenario {
        alice.hand(sandmere)
        alice.battlefield("Plains", "Plains")
        bob.hand(ogre, "Forest")
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        libraries()
        alice.casts(sandmere, target = "bob")
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { bob.exile has ogre }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bob.battlefield.names.count { it == "Mountain" } shouldBe 5
            "Mountain".tapped shouldBe true
        }
    }

    // CR 510.1c: a blocked non-Human creature may assign all its damage to the player it attacks
    @Test
    fun grothmawLetsABlockedCreatureHitThePlayer() = scenario {
        alice.battlefield(grothmaw, ox)
        bob.battlefield(wall)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to ox)
        advanceTo(TurnStep.CombatDamage)
        answer(Answer.Division(listOf(Share(TargetRef.Player(bob.id), 4))))
        resolveAll()
        expect {
            bob.life shouldBe 16
            card(wall).view.obj?.damage shouldBe 0
        }
    }

    // CR 510.1c: a Human has no such choice: its damage goes to its blocker
    @Test
    fun grothmawLeavesHumansBlocked() = scenario {
        alice.battlefield(grothmaw, human)
        bob.battlefield(wall)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(human)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to human)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.life shouldBe 20 }
    }

    // CR 508.1a: the two tokens attack together and each gets a counter; with a nontoken creature beside it one token alone gets none
    @Test
    fun theTagTeamTokensGrowWhenAttackingTogether() = scenario {
        val tokens = warriors()
        perform(Input.DeclareAttackers(alice.id, tokens.map { Attack(it, TargetRef.Player(bob.id)) }))
        resolveAll()
        assertEquals(listOf(1, 1), tokens.map { state.objOrThrow(it).counters(CounterKind.PowerToughness(1, 1)) })
    }

    // CR 508.1a: beside a nontoken creature the token attacks without another creature token: no counter
    @Test
    fun aTagTeamTokenBesideANontokenCreatureDoesNotGrow() = scenario {
        val tokens = warriors(bear)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(tokens.first(), TargetRef.Player(bob.id)), Attack(card(bear), TargetRef.Player(bob.id)))))
        resolveAll()
        assertEquals(0, state.objOrThrow(tokens.first()).counters(CounterKind.PowerToughness(1, 1)))
    }

    private fun Scenario.warriors(vararg others: String): List<arcanum.engine.state.ObjectId> {
        alice.hand(tagCall)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains", *others)
        libraries()
        alice.casts(tagCall)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        return state.battlefield.filter { state.obj(it)?.let { o -> nameOf(o) } == "Warrior Token" }.also { assertEquals(2, it.size) }
    }

    // CR 603.12, 107.3c: two creatures tapped make X 2; CR 701.24a: the target is shuffled into its owner's library
    @Test
    fun aldricShufflesAPermanentWithManaValueUpToTheCreaturesTapped() = scenario {
        alice.battlefield(aldric, counters = listOf(CounterKind.Loyalty to 4))
        backFaceUp(aldric)
        alice.battlefield(bear, human)
        bob.battlefield(idol, ox)
        libraries()
        alice.activates(aldric)
        resolveAll()
        alice.chooses(bear, human)
        resolveAll()
        expect {
            bob.library has idol
            ox.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
        }
    }

    // CR 105.2: a two-colour permanent can't be the target; a one-colour one is exiled
    @Test
    fun concordiaExilesOnlyWhatIsNotExactlyTwoColors() = scenario {
        alice.hand(concordia)
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(idol, icon)
        libraries()
        alice.casts(concordia)
        resolveAll()
        expect {
            bob.exile has idol
            icon.zone shouldBe Zone.Battlefield
        }
    }
}
