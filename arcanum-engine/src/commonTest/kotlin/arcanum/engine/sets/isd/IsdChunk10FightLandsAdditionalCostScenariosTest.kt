package arcanum.engine.sets.isd

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** ISD chunk 10: fight, utility lands with a second ability, and additional costs paid from the graveyard or by sacrificing. Invented
 * names carry the oracle text of the real cards. */
class IsdChunk10FightLandsAdditionalCostScenariosTest {
    private val warden = Cards.inventFaces("Transform",
        face("Daylight Warden", "{2}{G}", "Creature — Human Archer Ranger Werewolf", """
            {T}: This creature deals 2 damage to target creature with flying.
            At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
        """.trimIndent(), "2/2", null, null),
        face("Nightfall Stalker", null, "Creature — Werewolf", """
            {R}, {T}: This creature fights target creature. (Each deals damage equal to its power to the other.)
            At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
        """.trimIndent(), "4/4", null, "G"))
    private val prey = Cards.invent("Preyfall", "{G}", "Sorcery",
        "Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)")
    private val borough = Cards.invent("Gavony Borough", null, "Land", """
        {T}: Add {C}.
        {2}{G}{W}, {T}: Put a +1/+1 counter on each creature you control.
    """.trimIndent())
    private val plaza = Cards.invent("Ghost Plaza", null, "Land", """
        {T}: Add {C}.
        {T}, Sacrifice this land: Destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield, then shuffle.
    """.trimIndent())
    private val howlRun = Cards.invent("Kessig Howl Run", null, "Land", """
        {T}: Add {C}.
        {X}{R}{G}, {T}: Target creature gets +X/+0 and gains trample until end of turn.
    """.trimIndent())
    private val hollow = Cards.invent("Moorland Hollow", null, "Land", """
        {T}: Add {C}.
        {W}{U}, {T}, Exile a creature card from your graveyard: Create a 1/1 white Spirit creature token with flying.
    """.trimIndent())
    private val cavern = Cards.invent("Shimmering Cavern", null, "Land", """
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
    """.trimIndent())
    private val bloodpit = Cards.invent("Stensia Bloodpit", null, "Land", """
        {T}: Add {C}.
        {3}{B}{R}, {T}: This land deals 2 damage to target player or planeswalker.
    """.trimIndent())
    private val reaping = Cards.invent("Altar's Reaping", "{1}{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature.
        Draw two cards.
    """.trimIndent())
    private val blaze = Cards.invent("Harvest Blaze", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, exile X cards from your graveyard.
        Harvest Blaze deals X damage to target creature.
    """.trimIndent())
    private val leap = Cards.invent("Infernal Leap", "{R}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature.
        Add {R}{R}{R}.
    """.trimIndent())
    private val brute = Cards.invent("Makeshift Brute", "{3}{U}", "Creature — Zombie Horror",
        "As an additional cost to cast this spell, exile a creature card from your graveyard.", "4/5")
    private val colossus = Cards.invent("Skaab Colossus", "{5}{U}", "Creature — Zombie Giant", """
        As an additional cost to cast this spell, exile two creature cards from your graveyard.
        Trample
    """.trimIndent(), "6/9")
    private val destroyer = Cards.invent("Skaab Destroyer", "{1}{U}{U}", "Creature — Zombie Horror", """
        As an additional cost to cast this spell, exile three creature cards from your graveyard.
        Flying
        You may cast this card from your graveyard.
    """.trimIndent(), "5/6")
    private val wyvern = Cards.invent("Stitched Wyvern", "{1}{U}{U}", "Creature — Zombie Drake", """
        As an additional cost to cast this spell, exile a creature card from your graveyard.
        Flying
    """.trimIndent(), "3/4")
    private val bat = Cards.invent("Moorland Bat", "{1}{B}", "Creature — Bat", "Flying", "2/2")
    private val bear = Cards.invent("Moorland Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Fenland Ox", "{3}", "Creature — Ox", "", "4/4")
    private val hare = Cards.invent("Moorland Hare", "{G}", "Creature — Rabbit", "", "1/1")
    private val glance = Cards.invent("Quick Peek", "{U}", "Instant", "Draw a card.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face

    // CR 602.2, 120.3: the front face pings a flier
    @Test
    fun wardenDamagesAFlyingCreature() = scenario {
        alice.battlefield(warden)
        bob.battlefield(bat, bear)
        alice.activates(warden, target = bat)
        resolveAll()
        expect { bob.graveyard has bat }
        expect { bob.battlefield has bear }
    }

    // CR 603.4, 701.27a: a turn with no spell cast transforms it at the next upkeep; CR 701.14a: the back face fights
    @Test
    fun wardenTransformsAndFights() = scenario {
        alice.battlefield(warden, "Mountain")
        bob.battlefield(bear)
        alice.library(bear, bear, bear)
        bob.library(bear, bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        assertEquals(1, face(warden))
        alice.activates(warden, target = bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            warden.tapped shouldBe true
            alice.battlefield has warden
        }
    }

    private fun Scenario.backFaceAfterCasting(spells: Int) {
        alice.battlefield(warden)
        bob.battlefield("Island", "Island")
        alice.library(bear, bear, bear, bear)
        bob.library(bear, bear, bear, bear)
        players[2].library(bear, bear, bear, bear)
        bob.hand(*List(spells) { glance }.toTypedArray())
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(1, face(warden))
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        repeat(spells) { bob.casts(glance); resolveAll() }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
    }

    // CR 603.4, 102.1: "a player cast two or more spells last turn": bob's two spells flip it back at the next upkeep (3 players)
    @Test
    fun wardenBackTransformsAfterAPlayerCastsTwo() = scenario(players = 3) {
        backFaceAfterCasting(2)
        assertEquals(0, face(warden))
    }

    @Test
    fun wardenBackStaysAfterOneSpell() = scenario(players = 3) {
        backFaceAfterCasting(1)
        assertEquals(1, face(warden))
    }

    // CR 701.14a: each deals damage equal to its power to the other; CR 701.14b: both are destroyed here
    @Test
    fun preyfallMakesCreaturesFight() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(prey)
        bob.battlefield(ox, hare)
        alice.casts(prey, targets = listOf(bear, hare))
        resolveAll()
        expect {
            alice.battlefield has bear
            bob.graveyard has hare
            bob.battlefield has ox
        }
    }

    // CR 701.14a: damage is dealt both ways, so the smaller creature dies too
    @Test
    fun preyfallCanLoseAFight() = scenario(players = 3) {
        alice.battlefield("Forest", bear)
        alice.hand(prey)
        players[2].battlefield(ox)
        alice.casts(prey, targets = listOf(bear, ox))
        resolveAll()
        expect {
            alice.graveyard has bear
            players[2].battlefield has ox
        }
    }

    // CR 602.5b: the land's {T} cost for either ability; tapped for its own ability, it can't also make mana
    @Test
    fun boroughPutsACounterOnEachCreature() = scenario {
        alice.battlefield(borough, "Forest", "Plains", "Island", "Island", bear, ox)
        bob.battlefield(hare)
        alice.activates(borough, ability = 1)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            ox.counters(p1p1) shouldBe 1
            hare.counters(p1p1) shouldBe 0
            borough.tapped shouldBe true
        }
    }

    // CR 701.8a, 608.2h: the destroyed land's controller (an opponent) may fetch a basic land
    @Test
    fun plazaLetsTheOpponentFetchABasic() = scenario(players = 3) {
        alice.battlefield(plaza)
        bob.battlefield("Mountain")
        bob.library("Forest", "Island")
        alice.activates(plaza, ability = 1, target = "Mountain")
        resolveAll()
        bob.answers(arcanum.engine.decision.Answer.YesNo(true))
        resolveAll()
        bob.chooses("Forest")
        resolveAll()
        expect {
            alice.graveyard has plaza
            bob.graveyard has "Mountain"
            bob.battlefield has "Forest"
            bob.battlefield.size shouldBe 1
        }
    }

    // CR 107.3a, 107.3k: X = 2 pays {2}{R}{G}; +2/+0 and trample until end of turn
    @Test
    fun howlRunPumpsByX() = scenario {
        alice.battlefield(howlRun, "Mountain", "Forest", "Island", "Island", bear)
        alice.activates(howlRun, ability = 1, target = bear, x = 2)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 2
            bear hasKeyword Keyword.Trample.kind
        }
    }

    // CR 602.2, 118.8: exiling a creature card from the graveyard pays for a Spirit
    @Test
    fun hollowExilesACreatureCardForASpirit() = scenario {
        alice.battlefield(hollow, "Plains", "Island")
        alice.graveyard(bear, glance)
        alice.activates(hollow, ability = 1, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.exile has bear
            alice.graveyard has glance
            "Spirit Token".power shouldBe 1
            "Spirit Token" hasKeyword Keyword.Flying.kind
        }
    }

    // CR 106.1b, 605.1a: {1}, {T}: one mana of any color pays for a colored spell
    @Test
    fun cavernMakesAnyColorForOneMana() = scenario {
        alice.battlefield(cavern, "Island")
        alice.activates(cavern, ability = 1, paying = listOf("Island"), color = arcanum.api.Color.Green)
        assertEquals(listOf(arcanum.api.Color.Green), alice.pool.map { it.color })
        expect {
            cavern.tapped shouldBe true
            "Island".tapped shouldBe true
        }
    }

    // CR 120.3, 115.1: target player (here the third player) takes 2
    @Test
    fun bloodpitHitsAPlayer() = scenario(players = 3) {
        alice.battlefield(bloodpit, "Swamp", "Mountain", "Swamp", "Swamp", "Swamp")
        alice.activates(bloodpit, ability = 1, target = "carol")
        resolveAll()
        expect { players[2].life shouldBe 18 }
    }

    // CR 601.2b, 601.2h: the sacrifice is paid as the spell is cast, before it resolves
    @Test
    fun reapingSacrificesToDrawTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(reaping)
        alice.library("Island", "Plains", "Forest")
        alice.casts(reaping, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.hand has "Island"
            alice.hand has "Plains"
        }
    }

    // CR 601.2h, 106.4: the mana is added to the pool as it resolves
    @Test
    fun leapSacrificesForMana() = scenario {
        alice.battlefield("Mountain", bear, bat)
        alice.hand(leap)
        alice.casts(leap, choosing = listOf(bear))
        resolveAll()
        expect { alice.graveyard has bear }
    }

    // CR 107.3a, 601.2b: X is the number of cards exiled; that much damage
    @Test
    fun blazeExilesXCardsForXDamage() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(blaze)
        alice.graveyard("Island", "Island", "Island", "Island")
        bob.battlefield(ox)
        alice.casts(blaze, target = ox, x = 4)
        resolveAll()
        expect {
            alice.exile.size shouldBe 4
            bob.graveyard has ox
        }
    }

    // CR 601.2b: a creature card is exiled from the graveyard; a noncreature card isn't a legal choice
    @Test
    fun bruteExilesACreatureCard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(brute)
        alice.graveyard(glance, bear)
        alice.casts(brute)
        resolveAll()
        expect {
            alice.exile has bear
            alice.graveyard has glance
            alice.battlefield has brute
        }
    }

    @Test
    fun colossusExilesTwoCreatureCards() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(colossus)
        alice.graveyard(bear, ox, hare)
        alice.casts(colossus, choosing = listOf(bear, ox))
        resolveAll()
        expect {
            alice.exile.size shouldBe 2
            alice.graveyard has hare
            alice.battlefield has colossus
        }
    }

    // CR 601.2b, 601.3: cast from the graveyard, and the three exiled cards don't include the spell itself
    @Test
    fun destroyerIsCastFromTheGraveyardExilingThree() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.graveyard(destroyer, bear, ox, hare)
        alice.casts(destroyer, choosing = listOf(bear, ox, hare))
        resolveAll()
        expect {
            alice.battlefield has destroyer
            alice.exile.size shouldBe 3
        }
    }

    @Test
    fun wyvernExilesACreatureCard() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(wyvern)
        alice.graveyard(bear)
        alice.casts(wyvern)
        resolveAll()
        expect {
            alice.exile has bear
            alice.battlefield has wyvern
        }
    }
}
