package arcanum.engine.sets.fdn

import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chunk 17 played: the invented names carry the oracle text of Dragonmaster Outcast, Dwynen's Elite, Extravagant Replication, Faebloom Trick, Guarded Heir, Heroic Reinforcements, Homunculus Horde, Infestation Sage, Krenko, Mob Boss, Lathliss, Dragon Queen, Maalfeld Twins, Mischievous Mystic, Prideful Parent, Redcap Gutter-Dweller, Release the Dogs, Rite of the Dragoncaller and Suspicious Shambler. */
class FdnChunk17CreateTokenScenariosTest {
    private val outcast = Cards.invent("Dragonbinder Outcast", "{R}", "Creature — Human Shaman",
        "At the beginning of your upkeep, if you control six or more lands, create a 5/5 red Dragon creature token with flying.", "1/1")
    private val elite = Cards.invent("Dwenna's Elite", "{1}{G}", "Creature — Elf Warrior",
        "When this creature enters, if you control another Elf, create a 1/1 green Elf Warrior creature token.", "2/2")
    private val replication = Cards.invent("Lavish Duplication", "{4}{U}{U}", "Enchantment",
        "At the beginning of your upkeep, create a token that's a copy of another target nonland permanent you control.")
    private val gambit = Cards.invent("Faerie Gambit", "{2}{U}", "Instant",
        "Create two 1/1 blue Faerie creature tokens with flying. When you do, tap target creature an opponent controls.")
    private val heir = Cards.invent("Warded Heir", "{5}{W}", "Creature — Human Noble", """
        Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        When this creature enters, create two 3/3 white Knight creature tokens.
    """, "1/1")
    private val reinforcements = Cards.invent("Valiant Reinforcements", "{2}{R}{W}", "Sorcery",
        "Create two 1/1 white Soldier creature tokens. Until end of turn, creatures you control get +1/+1 and gain haste. (They can attack and {T} this turn.)")
    private val swarm = Cards.invent("Homunculus Swarm", "{3}{U}", "Creature — Homunculus",
        "Whenever you draw your second card each turn, create a token that's a copy of this creature.", "2/2")
    private val sage = Cards.invent("Blight Sage", "{B}", "Creature — Elf Warlock",
        "When this creature dies, create a 1/1 black and green Insect creature token with flying.", "1/1")
    private val grenko = Cards.invent("Grenko, Horde Boss", "{2}{R}{R}", "Legendary Creature — Goblin Warrior",
        "{T}: Create X 1/1 red Goblin creature tokens, where X is the number of Goblins you control.", "3/3")
    private val lethlass = Cards.invent("Lethlass, Drake Queen", "{4}{R}{R}", "Legendary Creature — Dragon", """
        Flying
        Whenever another nontoken Dragon you control enters, create a 5/5 red Dragon creature token with flying.
        {1}{R}: Dragons you control get +1/+0 until end of turn.
    """, "6/6")
    private val twins = Cards.invent("Maalmark Twins", "{5}{B}", "Creature — Zombie",
        "When this creature dies, create two 2/2 black Zombie creature tokens.", "4/4")
    private val mystic = Cards.invent("Playful Mystic", "{1}{U}", "Creature — Human Wizard", """
        Flying
        Whenever you draw your second card each turn, create a 1/1 blue Faerie creature token with flying.
    """, "2/1")
    private val guardian = Cards.invent("Proud Guardian", "{2}{W}", "Creature — Cat", """
        Vigilance (Attacking doesn't cause this creature to tap.)
        When this creature enters, create a 1/1 white Cat creature token.
    """, "2/2")
    private val dweller = Cards.invent("Redhat Gutter-Dweller", "{2}{R}{R}", "Creature — Goblin Warrior", """
        Menace
        When this creature enters, create two 1/1 black Rat creature tokens with "This token can't block."
        At the beginning of your upkeep, you may sacrifice another creature. If you do, put a +1/+1 counter on this creature and exile the top card of your library. You may play that card this turn.
    """, "3/3")
    private val hounds = Cards.invent("Release the Hounds", "{3}{W}", "Sorcery", "Create four 1/1 white Dog creature tokens.")
    private val rite = Cards.invent("Rite of the Drakecaller", "{4}{R}{R}", "Enchantment",
        "Whenever you cast an instant or sorcery spell, create a 5/5 red Dragon creature token with flying.")
    private val shambler = Cards.invent("Dubious Shambler", "{3}{B}", "Creature — Zombie", """
        {4}{B}{B}, Exile this card from your graveyard: Create two 2/2 black Zombie creature tokens. Activate only as a sorcery.
    """, "4/2")

    private val elfOther = Cards.invent("Glade Elf", "{G}", "Creature — Elf", "", "1/1")
    private val bear = Cards.invent("Grove Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bobOx = Cards.invent("Marsh Ox", "{2}{G}", "Creature — Ox", "", "2/2")
    private val goblinPal = Cards.invent("Hollow Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val drake = Cards.invent("Wee Drake", "{R}", "Creature — Dragon", "", "1/1")
    private val studyOne = Cards.invent("Tide Reading", "{U}", "Instant", "Draw a card.")
    private val studyTwo = Cards.invent("Tide Review", "{U}", "Instant", "Draw a card.")
    private val zap = Cards.invent("Needle Zap", "{R}", "Instant", "Needle Zap deals 2 damage to any target.")
    private val ruin = Cards.invent("Searing Ruin", "{1}{R}", "Instant", "Searing Ruin deals 4 damage to target creature.")
    private val relic = Cards.invent("Chipped Relic", "{1}", "Artifact", "")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.pay() {
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 603.4: the intervening "if" is checked when the upkeep begins; six lands make the Dragon, 5/5 red with flying (CR 111.4)
    @Test
    fun dragonmasterMakesADragonWithSixLands() = scenario {
        alice.battlefield(outcast, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library(bear, bear, bear)
        bob.library(bear, bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            count("Dragon Token") shouldBe 1
            "Dragon Token".power shouldBe 5
            "Dragon Token".toughness shouldBe 5
            "Dragon Token".colors shouldBe listOf(Color.Red)
            "Dragon Token" hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.4: five lands fail the condition, so the trigger never goes on the stack
    @Test
    fun dragonmasterNeedsSixLands() = scenario {
        alice.battlefield(outcast, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library(bear, bear, bear)
        bob.library(bear, bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        assertEquals(0, count("Dragon Token"))
    }

    // CR 603.4: with another Elf the Elite makes an Elf Warrior token
    @Test
    fun eliteMakesAnElfWithAnotherElf() = scenario {
        alice.battlefield("Forest", "Forest", elfOther)
        alice.hand(elite)
        alice.casts(elite)
        pay()
        resolveAll()
        expect {
            count("Elf Warrior Token") shouldBe 1
            "Elf Warrior Token".power shouldBe 1
            "Elf Warrior Token".colors shouldBe listOf(Color.Green)
        }
    }

    // CR 603.4, 111.2: the Elite is an Elf itself but "another" excludes it, so alone it makes nothing
    @Test
    fun eliteAloneMakesNothing() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(elite)
        alice.casts(elite)
        pay()
        resolveAll()
        expect { alice.battlefield lacks "Elf Warrior Token" }
    }

    // CR 707.2, 115.1: the target is another nonland permanent I control; the token copies it
    @Test
    fun replicationCopiesTheTarget() = scenario {
        alice.battlefield(replication, bear, relic, "Island")
        alice.library(bear, bear, bear)
        bob.library(bear, bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        val options = decision<Decision.Targets>().options.toSet()
        assertEquals(setOf(target(bear), target(relic)), options)
        alice.answers(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Grove Bear" } shouldBe 2
            "Grove Bear".power shouldBe 2
        }
    }

    // CR 603.12: "When you do" makes a reflexive trigger once the tokens were created; it taps their creature
    @Test
    fun gambitMakesTwoFaeriesAndTapsACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        bob.battlefield(bobOx, bear)
        alice.hand(gambit)
        alice.casts(gambit)
        pay()
        resolveAll()
        if (pending is Decision.Targets) {
            alice.answers(Answer.Targets(listOf(target(bobOx))))
            resolveAll()
        }
        expect {
            count("Faerie Token") shouldBe 2
            "Faerie Token" hasKeyword Keyword.Flying.kind
            "Faerie Token".colors shouldBe listOf(Color.Blue)
            bobOx.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    // CR 603.12: with no creature an opponent controls the reflexive trigger has no target, but the tokens are still made
    @Test
    fun gambitStillMakesFaeriesWithoutATarget() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(gambit)
        alice.casts(gambit)
        pay()
        resolveAll()
        expect { count("Faerie Token") shouldBe 2 }
    }

    // CR 111.2, 603.6a: two Knights, 3/3 white; the Heir itself has lifelink
    @Test
    fun heirMakesTwoKnights() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(heir)
        alice.casts(heir)
        pay()
        resolveAll()
        expect {
            count("Knight Token") shouldBe 2
            "Knight Token".power shouldBe 3
            "Knight Token".toughness shouldBe 3
            "Knight Token".colors shouldBe listOf(Color.White)
            heir hasKeyword Keyword.Lifelink.kind
        }
    }

    // CR 611.2a, 608.2: the tokens exist when the pump applies, so they get +1/+1 and haste too; the opponent's creature does not
    @Test
    fun reinforcementsPumpsTheNewTokens() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains", bear)
        bob.battlefield(bobOx)
        alice.hand(reinforcements)
        alice.casts(reinforcements)
        pay()
        resolveAll()
        expect {
            count("Soldier Token") shouldBe 2
            "Soldier Token".power shouldBe 2
            "Soldier Token" hasKeyword Keyword.Haste.kind
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste.kind
            bobOx.power shouldBe 2
        }
    }

    // CR 121.2, 707.2: the second draw of a turn makes a copy of the creature; the first draw doesn't
    @Test
    fun homunculusCopiesItselfOnTheSecondDraw() = scenario {
        alice.battlefield(swarm, "Island", "Island")
        alice.library(bear, bear, bear)
        alice.hand(studyOne, studyTwo)
        alice.casts(studyOne)
        pay()
        resolveAll()
        assertEquals(1, count("Homunculus Swarm"))
        alice.casts(studyTwo)
        pay()
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Homunculus Swarm" } shouldBe 2
            "Homunculus Swarm".power shouldBe 2
        }
    }

    // CR 700.4, 603.10a: the dying creature's trigger makes a black and green flying Insect
    @Test
    fun sageLeavesAnInsect() = scenario {
        alice.battlefield(sage)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.passes()
        bob.casts(zap, target = sage)
        pay()
        resolveAll()
        expect {
            sage.zone shouldBe Zone.Graveyard
            count("Insect Token") shouldBe 1
            "Insect Token".colors.toSet() shouldBe setOf(Color.Black, Color.Green)
            "Insect Token" hasKeyword Keyword.Flying.kind
        }
    }

    // CR 107.3a, 111.2: X is the number of Goblins I control when the ability resolves (Grenko and a Goblin: two tokens)
    @Test
    fun grenkoMakesAsManyGoblinsAsItControls() = scenario {
        alice.battlefield(grenko, goblinPal)
        alice.activates(grenko)
        resolveAll()
        assertEquals(2, count("Goblin Token"))
    }

    // CR 603.2, 111.2: a nontoken Dragon entering makes a Dragon token; the token entering doesn't (it is a token)
    @Test
    fun lethlassMakesADragonForANontokenDragon() = scenario {
        alice.battlefield(lethlass, "Mountain")
        alice.hand(drake, bear)
        alice.casts(drake)
        pay()
        resolveAll()
        expect {
            count("Dragon Token") shouldBe 1
            "Dragon Token".power shouldBe 5
            "Dragon Token" hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.2: a non-Dragon entering doesn't trigger it
    @Test
    fun lethlassIgnoresOtherCreatures() = scenario {
        alice.battlefield(lethlass, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        pay()
        resolveAll()
        assertEquals(0, count("Dragon Token"))
    }

    // CR 611.2a: Dragons I control get +1/+0 until end of turn (including the Queen); other creatures don't
    @Test
    fun lethlassPumpsDragons() = scenario {
        alice.battlefield(lethlass, drake, bear, "Mountain", "Mountain")
        alice.activates(lethlass)
        pay()
        resolveAll()
        expect {
            lethlass.power shouldBe 7
            drake.power shouldBe 2
            bear.power shouldBe 2
        }
    }

    // CR 700.4, 111.2: two 2/2 black Zombies
    @Test
    fun twinsLeaveTwoZombies() = scenario {
        alice.battlefield(twins)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(ruin)
        alice.passes()
        bob.casts(ruin, target = twins)
        pay()
        resolveAll()
        expect {
            count("Zombie Token") shouldBe 2
            "Zombie Token".power shouldBe 2
            "Zombie Token".colors shouldBe listOf(Color.Black)
        }
    }

    // CR 121.2: the second draw makes a Faerie; the Mystic has flying itself
    @Test
    fun mysticMakesAFaerieOnTheSecondDraw() = scenario {
        alice.battlefield(mystic, "Island", "Island")
        alice.library(bear, bear, bear)
        alice.hand(studyOne, studyTwo)
        alice.casts(studyOne)
        pay()
        resolveAll()
        assertEquals(0, count("Faerie Token"))
        alice.casts(studyTwo)
        pay()
        resolveAll()
        expect {
            count("Faerie Token") shouldBe 1
            "Faerie Token" hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.6a, 111.2: a 1/1 white Cat
    @Test
    fun guardianMakesACat() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(guardian)
        alice.casts(guardian)
        pay()
        resolveAll()
        expect {
            count("Cat Token") shouldBe 1
            "Cat Token".power shouldBe 1
            "Cat Token".colors shouldBe listOf(Color.White)
            guardian hasKeyword Keyword.Vigilance.kind
        }
    }

    // CR 111.4, 509.1b: the two Rats carry "This token can't block."
    @Test
    fun dwellerMakesTwoRatsThatCannotBlock() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dweller)
        alice.casts(dweller)
        pay()
        resolveAll()
        expect {
            count("Rat Token") shouldBe 2
            "Rat Token".colors shouldBe listOf(Color.Black)
            "Rat Token" hasMechanic { it is Mechanic.CantBlock }
            dweller lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 608.2c, 701.21: sacrificing another creature at upkeep gives the +1/+1 counter and exiles the top card, which may be played this turn
    @Test
    fun dwellerSacrificeGivesCounterAndExilesTop() = scenario {
        alice.battlefield(dweller, bear)
        alice.library(goblinPal, relic, relic)
        bob.library(relic, relic, relic)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            dweller.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.exile has goblinPal
        }
    }

    // CR 608.2c: declining the sacrifice does nothing
    @Test
    fun dwellerDecliningDoesNothing() = scenario {
        alice.battlefield(dweller, bear)
        alice.library(goblinPal, relic, relic)
        bob.library(relic, relic, relic)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            dweller.counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 111.2: four 1/1 white Dogs
    @Test
    fun houndsMakeFourDogs() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(hounds)
        alice.casts(hounds)
        pay()
        resolveAll()
        expect {
            count("Dog Token") shouldBe 4
            "Dog Token".power shouldBe 1
            "Dog Token".colors shouldBe listOf(Color.White)
        }
    }

    // CR 601.2i, 603.2: an instant spell I cast makes a Dragon; a creature spell doesn't
    @Test
    fun riteTriggersOnInstantsNotCreatures() = scenario {
        alice.battlefield(rite, "Island", "Island", "Forest")
        alice.library(bear, bear)
        alice.hand(studyOne, bear)
        alice.casts(bear)
        pay()
        resolveAll()
        assertEquals(0, count("Dragon Token"))
        alice.casts(studyOne)
        pay()
        resolveAll()
        expect {
            count("Dragon Token") shouldBe 1
            "Dragon Token".power shouldBe 5
        }
    }

    // CR 602.2, 118.3: exiling the Shambler from the graveyard as a cost makes two Zombies at sorcery speed
    @Test
    fun shamblerMakesZombiesFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(shambler)
        alice.activates(shambler)
        pay()
        resolveAll()
        expect {
            shambler.zone shouldBe Zone.Exile
            count("Zombie Token") shouldBe 2
            "Zombie Token".power shouldBe 2
        }
    }
}
