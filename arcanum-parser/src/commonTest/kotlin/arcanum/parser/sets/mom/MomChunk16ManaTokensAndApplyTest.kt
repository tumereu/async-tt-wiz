package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MOM chunk 16: restricted and any-colour mana abilities, a sacrificed additional cost and a sacrifice kicker (CR 601.2f, 702.33),
 * "gains K until end of turn" pumps and grants, base power and toughness, counters and token creation.
 */
class MomChunk16ManaTokensAndApplyTest {
    private val kicked: Condition = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
    private val phyrexians = Objects.Each(and(sub("Phyrexian"), you))
    private fun additionalCost(vararg parts: CostPart): Ability =
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost(*parts))), affects = arcanum.api.Affected.Objects(Objects.Self))
    private fun token(subtypes: String, colors: List<Color>, power: Int, toughness: Int, vararg keywords: Keyword) =
        creatureToken(subtypes, colors, power, toughness, keywords.toList())
    private val elemental = token("Elemental", listOf(Color.Blue, Color.Red), 1, 1)
    private val knight = token("Knight", listOf(Color.White, Color.Blue), 2, 2, Keyword.Vigilance)

    // CR 106.6: mana that can be spent only to activate abilities
    @Test
    fun omenPlover() = card(
        name = "Omen Plover", cost = "{U}", type = "Creature — Octopus Advisor", pt = "1/1",
        text = "{T}: Add {C}{U}. Spend this mana only to activate abilities.",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}{U}")!!),
            restriction = ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated)), Players.You))),
    )

    // CR 605.1a: an activated mana ability with a mana cost
    @Test
    fun urnOfDawnfire() = card(
        name = "Urn of Dawnfire", cost = "{1}", type = "Artifact",
        text = """
            {2}: Add one mana of any color.
            {6}, {T}, Sacrifice this artifact: Destroy target creature or enchantment.
        """,
    ) parsesTo abilities(
        activated(manaCost("{2}"), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
        activated(cost(mana("{6}"), tap, sacrificeSelf), destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, enchantment)))),
    )

    // CR 601.2f
    @Test
    fun corruptedCredence() = card(
        name = "Corrupted Credence", cost = "{B}", type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Draw two cards.
        """,
    ) parsesTo abilities(additionalCost(sacrificeA(creature)), spell(draw(2)))

    // CR 702.33g (kicker with a non-mana cost), 608.2h
    @Test
    fun finalFanfare() = card(
        name = "Final Fanfare", cost = "{1}{B}", type = "Instant",
        text = """
            Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
            Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -6/-6 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(sacrificeA(or(artifact, creature))))),
        spell(ifThen(kicked, pump(target("creature"), -6, -6), otherwise = listOf(pump(target("creature"), -2, -2))), targets = listOf(slot("creature", creature))),
    )

    // CR 205.3m (creature types), 702.110 (menace); the graveyard ability needs no stated zone: the engine finds it (CR 113.6m)
    @Test
    fun graftedSkinner() = card(
        name = "Grafted Skinner", cost = "{1}{B}", type = "Creature — Phyrexian Samurai", pt = "2/2",
        text = """
            When this creature enters, Phyrexians you control gain menace until end of turn.
            Other Phyrexians you control get +1/+1.
            {3}{B}, Sacrifice an artifact or creature: Return this card from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, gains(phyrexians, Keyword.Menace)),
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Phyrexian"), you))),
        activated(cost(mana("{3}{B}"), sacrificeA(or(artifact, creature))), move(Objects.Self, Zone.Battlefield, postBind = "returned"), rules = asSorcery),
    )

    @Test
    fun inspiredSurge() = card(
        name = "Inspired Surge", cost = "{2}{W}{W}", type = "Instant",
        text = "Creatures you control get +2/+1 until end of turn.",
    ) parsesTo abilities(spell(pump(Objects.Each(creatureYouControl), 2, 1)))

    // CR 702.9 (flying), 506.3
    @Test
    fun phyrexianGull() = card(
        name = "Phyrexian Gull", cost = "{2}{W}", type = "Creature — Phyrexian Bird", pt = "2/2",
        text = """
            Flying
            Whenever this creature attacks, another target attacking creature without flying gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, gains(target("creature"), Keyword.Flying),
            targets = listOf(slot("creature", and(another, attacking, creature, not(kw(Keyword.Flying.kind)))))),
    )

    // CR 700.2 (modes)
    @Test
    fun ravenousRaptorback() = card(
        name = "Ravenous Raptorback", cost = "{4}{G}", type = "Creature — Dinosaur", pt = "3/4",
        text = """
            When this creature enters, choose one —
            • This creature gains haste until end of turn.
            • Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(gains(Objects.Self, Keyword.Haste)),
            mode(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment))))))),
    )

    @Test
    fun trailblazingChronicler() = card(
        name = "Trailblazing Chronicler", cost = "{1}{R}", type = "Creature — Human Shaman", pt = "1/3",
        text = """
            Haste
            {T}: Another target creature gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(tap), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", and(another, creature)))),
    )

    @Test
    fun zhalfirinPikeman() = card(
        name = "Zhalfirin Pikeman", cost = "{2}{W}", type = "Creature — Human Knight", pt = "3/3",
        text = "Whenever another Knight you control enters, this creature gets +1/+1 and gains vigilance until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(another, sub("Knight"), you)), pump(Objects.Self, 1, 1, has(Keyword.Vigilance))),
    )

    // CR 613.4b (setting base power and toughness)
    @Test
    fun zhalfirinShapeshift() = card(
        name = "Zhalfirin Shapeshift", cost = "{1}{U}", type = "Instant",
        text = """
            Target creature has base power and toughness 4/3 until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(4), n(3))), draw(1), targets = listOf(slot("creature", creature))),
    )

    // CR 700.2, 701.6a
    @Test
    fun changeTheFormula() = card(
        name = "Change the Formula", cost = "{1}{U}", type = "Instant",
        text = """
            Choose one —
            • Counter target spell with mana value 2 or less.
            • Counter target red or green spell with mana value 6 or less.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(doing(Effect.Counter(target("spell"))),
                targets = listOf(slot("spell", and(spellFilter, manaValue(Comparison.AtMost, 2))))),
            mode(doing(Effect.Counter(target("spell"))),
                targets = listOf(slot("spell", and(or(ObjectFilter.OfColor(Color.Red), ObjectFilter.OfColor(Color.Green)), spellFilter,
                    manaValue(Comparison.AtMost, 6)))))))),
    )

    // CR 111.1, 111.4 (a token is created with the given characteristics)
    @Test
    fun knightOfTheOldCoalition() = card(
        name = "Knight of the Old Coalition", cost = "{3}{W}", type = "Creature — Human Knight", pt = "2/2",
        text = """
            Vigilance
            When this creature enters, create a 2/2 white and blue Knight creature token with vigilance.
        """,
    ) parsesTo abilities(Keyword.Vigilance, triggered(entersSelf, create(knight)))

    @Test
    fun preeningVictor() = card(
        name = "Preening Victor", cost = "{2}{U}", type = "Creature — Bird Knight", pt = "2/2",
        text = """
            Flying
            When this creature enters, create a 1/1 blue and red Elemental creature token.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, create(elemental)))

    @Test
    fun ralsGarrison() = card(
        name = "Ral's Garrison", cost = "{1}{R}", type = "Sorcery",
        text = "Create two 1/1 blue and red Elemental creature tokens.",
    ) parsesTo abilities(spell(create(elemental, n(2))))

    // CR 602.5b: an activation condition counting spells cast this turn
    @Test
    fun xerexStrobeSquire() = card(
        name = "Xerex Strobe-Squire", cost = "{2}{U}", type = "Creature — Human Knight", pt = "2/2",
        text = """
            Flying, vigilance
            {T}: Create a 2/2 white and blue Knight creature token with vigilance. Activate only if you've cast two or more spells this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Vigilance,
        activated(cost(tap), create(knight), rules = anyTime.copy(condition = Condition.HappenedThisTurn(
            EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), atLeast = 2, measure = Measure.Events))),
    )
}
