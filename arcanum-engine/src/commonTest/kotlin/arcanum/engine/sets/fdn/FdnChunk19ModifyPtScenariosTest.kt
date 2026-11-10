package arcanum.engine.sets.fdn

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Chunk 19 played: the invented names carry the oracle text of Anthem of Champions, Arahbo, Dwynen, Elvish Archdruid, Empyrean Eagle, Ghitu Lavarunner, Goblin Oriflamme, Imperious Perfect, Lyra Dawnbringer, Regal Caracal, Tempest Djinn and Wilt-Leaf Liege. */
class FdnChunk19ModifyPtScenariosTest {
    private val anthem = Cards.invent("Anthem of Heroes", "{G}{W}", "Enchantment", "Creatures you control get +1/+1.")
    private val arahbo = Cards.invent("Arahbo, the Second Fang", "{2}{W}", "Legendary Creature — Cat Avatar", """
        Other Cats you control get +1/+1.
        Whenever Arahbo or another nontoken Cat you control enters, create a 1/1 white Cat creature token.
    """, "2/2")
    private val dwynen = Cards.invent("Dwynen, Oak Daen", "{2}{G}{G}", "Legendary Creature — Elf Warrior", """
        Reach (This creature can block creatures with flying.)
        Other Elf creatures you control get +1/+1.
        Whenever Dwynen attacks, you gain 1 life for each attacking Elf you control.
    """, "3/4")
    private val archdruid = Cards.invent("Elvish Archdruid of the Glade", "{1}{G}{G}", "Creature — Elf Druid", """
        Other Elf creatures you control get +1/+1.
        {T}: Add {G} for each Elf you control.
    """, "2/2")
    private val eagle = Cards.invent("Sky Eagle of the Reach", "{1}{W}{U}", "Creature — Bird Spirit", """
        Flying
        Other creatures you control with flying get +1/+1.
    """, "2/3")
    private val runner = Cards.invent("Ghitu Fire Runner", "{R}", "Creature — Human Wizard",
        "As long as there are two or more instant and/or sorcery cards in your graveyard, this creature gets +1/+0 and has haste. (It can attack and {T} as soon as it comes under your control.)", "1/2")
    private val banner = Cards.invent("Goblin Banner of War", "{1}{R}", "Enchantment", "Attacking creatures you control get +1/+0.")
    private val perfect = Cards.invent("Perfect of the Wood", "{2}{G}", "Creature — Elf Warrior", """
        Other Elves you control get +1/+1.
        {G}, {T}: Create a 1/1 green Elf Warrior creature token.
    """, "2/2")
    private val lyra = Cards.invent("Lyra Skybringer", "{3}{W}{W}", "Legendary Creature — Angel", """
        Flying
        First strike (This creature deals combat damage before creatures without first strike.)
        Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        Other Angels you control get +1/+1 and have lifelink.
    """, "5/5")
    private val caracal = Cards.invent("Caracal of the Dunes", "{3}{W}{W}", "Creature — Cat", """
        Other Cats you control get +1/+1 and have lifelink. (Damage dealt by those creatures also causes you to gain that much life.)
        When this creature enters, create two 1/1 white Cat creature tokens with lifelink.
    """, "3/3")
    private val djinn = Cards.invent("Storm Djinn of the Isles", "{U}{U}{U}", "Creature — Djinn", """
        Flying
        This creature gets +1/+0 for each basic Island you control.
    """, "0/4")
    private val liege = Cards.invent("Liege of the Glade", "{1}{G/W}{G/W}{G/W}", "Creature — Elf Knight", """
        ({G/W} can be paid with either {G} or {W}.)
        Other green creatures you control get +1/+1.
        Other white creatures you control get +1/+1.
        If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
    """, "4/4")

    private val ox = Cards.invent("Plodding Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val bobBear = Cards.invent("Marsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val tabby = Cards.invent("Dune Lynx", "{1}{W}", "Creature — Cat", "", "2/2")
    private val thicketElf = Cards.invent("Thicket Elf", "{1}{G}", "Creature — Elf", "", "1/1")
    private val squire = Cards.invent("Plains Squire", "{1}{W}", "Creature — Human", "", "2/2")
    private val angel = Cards.invent("Temple Angel", "{2}{W}", "Creature — Angel", "", "2/2")
    private val wisp = Cards.invent("Gale Wisp", "{1}{U}", "Creature — Spirit", "Flying", "1/1")
    private val grunt = Cards.invent("Ember Grunt", "{1}{R}", "Creature — Goblin", "", "2/1")
    private val study = Cards.invent("Hushed Study", "{U}", "Instant", "Draw a card.")
    private val rite = Cards.invent("Ash Rite", "{B}", "Sorcery", "Each opponent loses 1 life.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 613.4c: all your creatures get +1/+1, the opponent's do not
    @Test
    fun anthemPumpsOnlyYourCreatures() = scenario {
        alice.battlefield(anthem, ox)
        bob.battlefield(bobBear)
        expect {
            ox.power shouldBe 3
            ox.toughness shouldBe 3
            bobBear.power shouldBe 2
            bobBear.toughness shouldBe 2
        }
    }

    // CR 613.4c, 603.6a, 603.2: the lord pumps the other Cats, not itself or non-Cats; its trigger makes a Cat token that is pumped too
    @Test
    fun arahboPumpsOtherCatsAndMakesATokenWhenItEnters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", tabby, ox)
        alice.hand(arahbo)
        alice.casts(arahbo)
        resolveAll()
        expect {
            arahbo.power shouldBe 2
            tabby.power shouldBe 3
            ox.power shouldBe 2
            state.battlefield.count { nameOf(state.obj(it)!!) == "Cat Token" } shouldBe 1
        }
    }

    // CR 613.4c, 508.1, 603.2, 119.1: other Elves get +1/+1, Dwynen does not; attacking with it and an Elf gains 2 life
    @Test
    fun dwynenPumpsElvesAndGainsLifeForAttackingElves() = scenario {
        alice.battlefield(dwynen, thicketElf, ox)
        expect {
            dwynen.power shouldBe 3
            thicketElf.power shouldBe 2
            ox.power shouldBe 2
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dwynen, thicketElf)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 613.4c, 605.1a, 106.1: the Archdruid pumps another Elf; tapping it adds {G} for each Elf you control (two)
    @Test
    fun archdruidPumpsElvesAndTapsForOneGreenPerElf() = scenario {
        alice.battlefield(archdruid, thicketElf)
        expect {
            archdruid.power shouldBe 2
            thicketElf.power shouldBe 2
        }
        alice.activates(archdruid)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    // CR 613.4c: only the other creatures with flying get the bonus
    @Test
    fun eaglePumpsOtherFliersOnly() = scenario {
        alice.battlefield(eagle, wisp, ox)
        expect {
            eagle.power shouldBe 2
            wisp.power shouldBe 2
            wisp.toughness shouldBe 2
            ox.power shouldBe 2
        }
    }

    // CR 613.4c, 611.3a, 613.4f: with two instants/sorceries in the graveyard it is 2/2 with haste; with one it is not; opponent's graveyard does not count
    @Test
    fun runnerNeedsTwoInstantsOrSorceriesInYourGraveyard() = scenario {
        alice.battlefield(runner)
        alice.graveyard(study, rite)
        bob.graveyard(study)
        expect {
            runner.power shouldBe 2
            runner hasKeyword Keyword.Haste.kind
        }
    }

    @Test
    fun runnerIsPlainWithOneInGraveyard() = scenario {
        alice.battlefield(runner)
        alice.graveyard(study)
        bob.graveyard(rite, rite)
        expect {
            runner.power shouldBe 1
            runner lacksKeyword Keyword.Haste.kind
        }
    }

    // CR 613.4c, 506.4: only attacking creatures get +1/+0, and only until they stop attacking (declared first)
    @Test
    fun bannerPumpsAttackersOnly() = scenario {
        alice.battlefield(banner, ox, grunt)
        expect { ox.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        expect {
            ox.power shouldBe 3
            ox.toughness shouldBe 2
            grunt.power shouldBe 2
        }
    }

    // CR 613.4c, 602.2, 111.1: other Elves get +1/+1; its token is an Elf and is pumped too
    @Test
    fun perfectPumpsElvesIncludingItsTokens() = scenario {
        alice.battlefield("Forest", perfect, thicketElf)
        alice.activates(perfect, paying = listOf("Forest"))
        resolveAll()
        expect {
            perfect.power shouldBe 2
            thicketElf.power shouldBe 2
            state.battlefield.count { nameOf(state.obj(it)!!) == "Elf Warrior Token" } shouldBe 1
        }
    }

    // CR 613.4c, 613.4f, 702.15a: other Angels get +1/+1 and lifelink; Lyra herself keeps 5/5
    @Test
    fun lyraPumpsOtherAngelsAndGivesLifelink() = scenario {
        alice.battlefield(lyra, angel, ox)
        expect {
            lyra.power shouldBe 5
            angel.power shouldBe 3
            angel hasKeyword Keyword.Lifelink.kind
            ox.power shouldBe 2
            ox lacksKeyword Keyword.Lifelink.kind
        }
    }

    // CR 613.4c, 702.15a, 111.1: the Caracal pumps its two lifelink Cat tokens and another Cat, which gains lifelink
    @Test
    fun caracalMakesTwoPumpedLifelinkCats() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", tabby)
        alice.hand(caracal)
        alice.casts(caracal)
        resolveAll()
        val tokens = state.battlefield.filter { nameOf(state.obj(it)!!) == "Cat Token" }
        expect {
            tokens.size shouldBe 2
            tabby.power shouldBe 3
            tabby hasKeyword Keyword.Lifelink.kind
            caracal.power shouldBe 3
            caracal lacksKeyword Keyword.Lifelink.kind
        }
    }

    // CR 613.4c, 205.3i: +1/+0 for each basic Island you control, not other lands
    @Test
    fun djinnCountsBasicIslands() = scenario {
        alice.battlefield(djinn, "Island", "Island", "Forest")
        bob.battlefield("Island")
        expect {
            djinn.power shouldBe 2
            djinn.toughness shouldBe 4
        }
    }

    // CR 613.4c, 105.1: green and white creatures get +1/+1, others and the Liege itself do not
    @Test
    fun liegePumpsGreenAndWhiteCreaturesOnly() = scenario {
        alice.battlefield(liege, ox, squire, grunt)
        bob.battlefield(bobBear)
        expect {
            liege.power shouldBe 4
            ox.power shouldBe 3
            squire.power shouldBe 3
            grunt.power shouldBe 2
            bobBear.power shouldBe 2
        }
    }
}
