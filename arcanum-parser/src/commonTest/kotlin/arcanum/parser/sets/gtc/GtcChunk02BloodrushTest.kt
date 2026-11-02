package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Gatecrash chunk 02: bloodrush. Bloodrush is an ability word (CR 207.2c) on an activated ability whose cost includes
 * "Discard this card", so it works only while the card is in its owner's hand (CR 113.6b, m). It targets an attacking
 * creature; the bonus is locked in as the ability resolves (CR 611.2c), "where X is the number of lands you control"
 * too (CR 608.2h).
 */
class GtcChunk02BloodrushTest {
    private val attackingCreature = slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature)))

    /** "Bloodrush — [mana], Discard this card: Target attacking creature gets +[p]/+[t] (and gains [keyword]) until end of turn." */
    private fun bloodrush(cost: String, p: Int, t: Int, vararg keywords: Ability) = Ability.Activated(
        cost = costOf(manaPart(cost), CostPart.Discard(CostObjects.Self)),
        instructions = Instructions(
            targets = listOf(attackingCreature),
            steps = listOf(pump(Objects.Target("creature"), p, t, *listOfNotNull(keywords.takeIf { it.isNotEmpty() }?.let { Mechanic.GainAbilities(it.toList()) }).toTypedArray())),
        ),
        rules = anyTime,
        abilityWord = "Bloodrush",
    )

    // CR 113.6m: a cost that discards this card makes it work in the hand
    @Test
    fun cinderstrideElemental() = card(
        name = "Cinderstride Elemental",
        cost = "{3}{R}",
        type = "Creature — Elemental",
        pt = "5/1",
        text = "Bloodrush — {1}{R}{R}, Discard this card: Target attacking creature gets +5/+1 until end of turn.",
    ) parsesTo abilities(bloodrush("{1}{R}{R}", 5, 1))

    @Test
    fun brandskinGoblin() = card(
        name = "Brandskin Goblin",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        pt = "2/1",
        text = "Bloodrush — {R}, Discard this card: Target attacking creature gets +2/+1 until end of turn.",
    ) parsesTo abilities(bloodrush("{R}", 2, 1))

    @Test
    fun shankfangViashino() = card(
        name = "Shankfang Viashino",
        cost = "{3}{R}",
        type = "Creature — Lizard Warrior",
        pt = "3/1",
        text = """
            First strike
            Bloodrush — {2}{R}, Discard this card: Target attacking creature gets +3/+1 and gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        bloodrush("{2}{R}", 3, 1, Keyword.FirstStrike),
    )

    @Test
    fun ruinousOgre() = card(
        name = "Ruinous Ogre",
        cost = "{4}{R}",
        type = "Creature — Ogre Warrior",
        pt = "3/3",
        text = """
            Double strike
            Bloodrush — {3}{R}{R}, Discard this card: Target attacking creature gets +3/+3 and gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        bloodrush("{3}{R}{R}", 3, 3, Keyword.DoubleStrike),
    )

    @Test
    fun clanbruteCharger() = card(
        name = "Clanbrute Charger",
        cost = "{3}{G}",
        type = "Creature — Centaur Warrior",
        pt = "2/4",
        text = "Bloodrush — {1}{G}, Discard this card: Target attacking creature gets +2/+4 until end of turn.",
    ) parsesTo abilities(bloodrush("{1}{G}", 2, 4))

    @Test
    fun rubblemawGoliath() = card(
        name = "Rubblemaw Goliath",
        cost = "{6}{G}{G}",
        type = "Creature — Beast",
        pt = "9/9",
        text = """
            Trample
            Bloodrush — {5}{G}{G}, Discard this card: Target attacking creature gets +9/+9 and gains trample until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        bloodrush("{5}{G}{G}", 9, 9, Keyword.Trample),
    )

    @Test
    fun gorehornBeast() = card(
        name = "Gorehorn Beast",
        cost = "{2}{G}",
        type = "Creature — Beast",
        pt = "3/2",
        text = "Bloodrush — {G}, Discard this card: Target attacking creature gets +3/+2 until end of turn.",
    ) parsesTo abilities(bloodrush("{G}", 3, 2))

    @Test
    fun blightfangViper() = card(
        name = "Blightfang Viper",
        cost = "{G}",
        type = "Creature — Snake",
        pt = "1/2",
        text = """
            Deathtouch
            Bloodrush — {G}, Discard this card: Target attacking creature gets +1/+2 and gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        bloodrush("{G}", 1, 2, Keyword.Deathtouch),
    )

    @Test
    fun ghorClanBruiser() = card(
        name = "Ghor-Clan Bruiser",
        cost = "{2}{R}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            Trample
            Bloodrush — {R}{G}, Discard this card: Target attacking creature gets +4/+4 and gains trample until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        bloodrush("{R}{G}", 4, 4, Keyword.Trample),
    )

    // CR 604.3: a characteristic-defining P/T; CR 608.2h: X is counted as the bloodrush ability resolves
    @Test
    fun quarryhulk() {
        val lands = Value.Count(Objects.Each(ObjectFilter.And(listOf(land, you))))
        card(
            name = "Quarryhulk",
            cost = "{4}{R}{G}",
            type = "Creature — Elemental",
            pt = "*/*",
            text = """
                Quarryhulk's power and toughness are each equal to the number of lands you control.
                Bloodrush — {1}{R}{G}, Discard this card: Target attacking creature gets +X/+X until end of turn, where X is the number of lands you control.
            """,
        ) parsesTo abilities(
            Ability.Static(listOf(Mechanic.SetPowerToughness(lands, lands)), affects = arcanum.api.Affected.Objects(Objects.Self), characteristicDefining = true),
            Ability.Activated(
                cost = costOf(manaPart("{1}{R}{G}"), CostPart.Discard(CostObjects.Self)),
                instructions = Instructions(
                    targets = listOf(attackingCreature),
                    steps = listOf(untilEndOfTurn(Objects.Target("creature"), Mechanic.ModifyPowerToughness(lands, lands))),
                ),
                rules = anyTime,
                abilityWord = "Bloodrush",
            ),
        )
    }

    @Test
    fun zhurTaaHog() = card(
        name = "Zhur-Taa Hog",
        cost = "{3}{R}{G}",
        type = "Creature — Boar",
        pt = "5/4",
        text = "Bloodrush — {1}{R}{G}, Discard this card: Target attacking creature gets +5/+4 until end of turn.",
    ) parsesTo abilities(bloodrush("{1}{R}{G}", 5, 4))
}
