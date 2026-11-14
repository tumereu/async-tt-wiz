package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 09: "until end of turn" effects (pumps, base power and toughness, can't be blocked, becomes a creature). */
class DmrChunk09ApplyTest {
    private val creatureSlot = listOf(slot("creature", creature))

    // CR 700.4, 613.4c: dies trigger, -1/-1 until end of turn
    @Test
    fun festeringGoblin() = card(
        name = "Gutter Goblin", cost = "{B}", type = "Creature — Zombie Goblin", pt = "1/1",
        text = "When this creature dies, target creature gets -1/-1 until end of turn.",
    ) parsesTo abilities(triggered(diesSelf, pump(target("creature"), -1, -1), targets = creatureSlot))

    // CR 613.4c, 702.9a
    @Test
    fun glintwingInvoker() = card(
        name = "Glassfeather Invoker", cost = "{4}{U}", type = "Creature — Human Wizard Mutant", pt = "3/3",
        text = "{7}{U}: This creature gets +3/+3 and gains flying until end of turn.",
    ) parsesTo abilities(activated(cost(mana("{7}{U}")), pump(Objects.Self, 3, 3, has(Keyword.Flying))))

    // CR 107.3a, 601.2b: X is announced; +X/+0
    @Test
    fun howlFromBeyond() = card(
        name = "Howl of Embers", cost = "{X}{B}", type = "Instant",
        text = "Target creature gets +X/+0 until end of turn.",
    ) parsesTo abilities(spell(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Announced("X"), n(0))), targets = creatureSlot, announced = listOf("X")))

    // CR 613.4c: -1/-0 with flying; CR 602.1: a {0} cost
    @Test
    fun hyalopterousLemure() = card(
        name = "Gossamer Lemure", cost = "{4}{B}", type = "Creature — Spirit", pt = "4/3",
        text = "{0}: This creature gets -1/-0 and gains flying until end of turn.",
    ) parsesTo abilities(activated(cost(mana("{0}")), pump(Objects.Self, -1, 0, has(Keyword.Flying))))

    // CR 205.1b, 613.1d: the land stays a land; 613.4b: it becomes 1/1
    @Test
    fun kamahl() = card(
        name = "Kiran, Fist of Moss", cost = "{4}{G}{G}", type = "Legendary Creature — Human Druid", pt = "4/3",
        text = """
            {G}: Target land becomes a 1/1 creature until end of turn. It's still a land.
            {2}{G}{G}{G}: Creatures you control get +3/+3 and gain trample until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{G}")),
            untilEndOfTurn(target("land"), Mechanic.SetTypes(add = listOf(CardType.Creature), replaceTypes = false), Mechanic.SetPowerToughness(n(1), n(1))),
            targets = listOf(slot("land", land))),
        activated(cost(mana("{2}{G}{G}{G}")), untilEndOfTurn(Objects.Each(creatureYouControl), pt(3, 3), has(Keyword.Trample))),
    )

    // CR 709.3, 613.4c
    @Test
    fun nightDay() = cardFaces(
        Layout.Split,
        card(name = "Eventide Fall", cost = "{B}", type = "Instant", text = "Target creature gets -1/-1 until end of turn.") to abilities(
            spell(pump(target("creature"), -1, -1), targets = creatureSlot),
        ),
        card(name = "Morningrise", cost = "{2}{W}", type = "Instant", text = "Creatures target player controls get +1/+1 until end of turn.") to abilities(
            spell(pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), 1, 1), targets = listOf(targetPlayer)),
        ),
    )

    // CR 613.1f, 613.4b: loses all abilities, base 0/1
    @Test
    fun ovinize() = card(
        name = "Mooncalf Hex", cost = "{1}{U}", type = "Instant",
        text = "Until end of turn, target creature loses all abilities and has base power and toughness 0/1.",
    ) parsesTo abilities(spell(untilEndOfTurn(target("creature"), Mechanic.LoseAbilities(), Mechanic.SetPowerToughness(n(0), n(1))), targets = creatureSlot))

    // CR 602.5, 118.12: tap and sacrifice this creature as a cost
    @Test
    fun phyrexianDebaser() = card(
        name = "Grimsky Debaser", cost = "{3}{B}", type = "Creature — Phyrexian Carrier", pt = "2/2",
        text = """
            Flying
            {T}, Sacrifice this creature: Target creature gets -2/-2 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Flying, activated(cost(tap, sacrificeSelf), pump(target("creature"), -2, -2), targets = creatureSlot))

    // CR 701.21a: you can sacrifice only a permanent you control; the cost is a creature you control
    @Test
    fun phyrexianGhoul() = card(
        name = "Grimsky Ghoul", cost = "{2}{B}", type = "Creature — Phyrexian Zombie", pt = "2/2",
        text = "Sacrifice a creature: This creature gets +2/+2 until end of turn.",
    ) parsesTo abilities(activated(cost(sacrificeA(creature)), pump(Objects.Self, 2, 2)))

    // CR 509.1b: can't be blocked this turn
    @Test
    fun subterraneanScout() = card(
        name = "Tunnel Scout", cost = "{1}{R}", type = "Creature — Goblin Scout", pt = "2/1",
        text = "When this creature enters, target creature with power 2 or less can't be blocked this turn.",
    ) parsesTo abilities(triggered(entersSelf, untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked),
        targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 2))))))

    // CR 709.3: each half is cast on its own
    @Test
    fun waxWane() = cardFaces(
        Layout.Split,
        card(name = "Swell", cost = "{G}", type = "Instant", text = "Target creature gets +2/+2 until end of turn.") to abilities(
            spell(pump(target("creature"), 2, 2), targets = creatureSlot),
        ),
        card(name = "Fade", cost = "{W}", type = "Instant", text = "Destroy target enchantment.") to abilities(
            spell(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
        ),
    )
}
