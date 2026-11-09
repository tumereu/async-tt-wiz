package arcanum.parser.sets.fdn

import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Ability
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 13 of FDN: more "until end of turn" pumps and grants, and what is left of the apply group. Invented names, the real oracle text. */
class FdnChunk13ApplyTest {
    private val yourCreatures = Objects.Each(and(creature, you))

    // CR 611.2a, 613.4c, 702.12a (indestructible)
    @Test
    fun holdTheBridge() = card(
        name = "Hold the Bridge",
        cost = "{2}{W}",
        type = "Instant",
        text = "Creatures you control get +1/+0 and gain indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy them.)",
    ) parsesTo abilities(
        spell(untilEndOfTurn(yourCreatures, pt(1, 0), has(Keyword.Indestructible))),
    )

    // CR 603.6a (enters), 613.4c, 700.4 (dies), 120.3 (life loss)
    @Test
    fun plagueWurmOfAsh() = card(
        name = "Plague Wurm of Ash",
        cost = "{3}{B}{B}{B}",
        type = "Creature — Phyrexian Wurm",
        pt = "6/5",
        text = """
            When this creature enters, creatures your opponents control get -2/-2 until end of turn.
            Whenever a creature an opponent controls dies, that player loses 2 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(Objects.Each(creatureOpponentControls), -2, -2)),
        triggered(dies(creatureOpponentControls), loseLife(2, Players.EventPlayer)),
    )

    // CR 613.4c, 119.1 (gain life)
    @Test
    fun hourOfHunger() = card(
        name = "Hour of Hunger",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -2/-2 until end of turn. You gain 2 life.",
    ) parsesTo abilities(
        spell(pump(target("creature"), -2, -2), gainLife(2), targets = listOf(slot("creature", creature))),
    )

    // CR 613.4c, 119.1
    @Test
    fun hourOfTriumph() = card(
        name = "Hour of Triumph",
        cost = "{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. You gain 2 life.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2), gainLife(2), targets = listOf(slot("creature", creature))),
    )

    // CR 702.2a (deathtouch), 702.12a (indestructible)
    @Test
    fun grantEndurance() = card(
        name = "Grant Endurance",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gains deathtouch and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(gains(target("creature"), Keyword.Deathtouch, Keyword.Indestructible), targets = listOf(slot("creature", creature))),
    )

    // CR 613.4c, 702.19b (trample)
    @Test
    fun stampedeSurge() = card(
        name = "Stampede Surge",
        cost = "{2}{G}{G}{G}",
        type = "Sorcery",
        text = "Creatures you control get +3/+3 and gain trample until end of turn. (Each of those creatures can deal excess combat damage to the player or planeswalker it's attacking.)",
    ) parsesTo abilities(
        spell(untilEndOfTurn(yourCreatures, pt(3, 3), has(Keyword.Trample))),
    )

    // CR 613.4c, 702.20b (vigilance)
    @Test
    fun absurdDimensions() = card(
        name = "Absurd Dimensions",
        cost = "{5}{G}{G}",
        type = "Sorcery",
        text = "Creatures you control get +10/+10 and gain vigilance until end of turn.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(yourCreatures, pt(10, 10), has(Keyword.Vigilance))),
    )

    // CR 702.10a (haste), 508.1m, 603.4, 605.1a (mana ability), 106.1b
    @Test
    fun tildaQuickScout() = card(
        name = "Tilda, Quick Scout",
        cost = "{R}{G}",
        type = "Legendary Creature — Human Scout",
        pt = "1/2",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            Whenever Tilda attacks while you control a creature with power 4 or greater, Tilda gets +2/+2 until end of turn.
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf, pump(Objects.Self, 2, 2),
            triggersOnlyIf = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1)),
        addEither("R", "G"),
    )

    // CR 602.1, 509.1b (can't block)
    @Test
    fun sifterOfHavoc() = card(
        name = "Sifter of Havoc",
        cost = "{3}{R}",
        type = "Creature — Devil",
        pt = "4/3",
        text = "{2}{R}: Target creature can't block this turn.",
    ) parsesTo abilities(
        activated(manaCost("{2}{R}"), untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 613.4c, 702.7a (first strike)
    @Test
    fun certainBlow() = card(
        name = "Certain Blow",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature gets +3/+0 and gains first strike until end of turn. (It deals combat damage before creatures without first strike.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 0, has(Keyword.FirstStrike)), targets = listOf(slot("creature", creature))),
    )

    // CR 207.2c (ability word), 603.4 (intervening if), 506.1, 702.10a, 700.5 (total power)
    @Test
    fun vorakTheHuntWarden() = card(
        name = "Vorak, the Hunt Warden",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Human Warrior",
        pt = "5/4",
        text = "Formidable — At the beginning of combat on your turn, if creatures you control have total power 8 or greater, target creature you control gains haste until end of turn. (It can attack and {T} no matter when it came under your control.)",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", creatureYouControl)), abilityWord = "Formidable",
            interveningIf = Condition.Compare(Value.Of(NumericProperty.Power, yourCreatures, Combine.Total), Comparison.AtLeast, n(8))),
    )

    // CR 702.7a, 508.1m, 603.2, 613.4c
    @Test
    fun sirBelenTheTigersFang() = card(
        name = "Syr Belen, the Tiger's Fang",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Human Knight",
        pt = "4/4",
        text = """
            First strike (This creature deals combat damage before creatures without first strike.)
            Whenever Syr Belen attacks, other creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(attacksSelf, pump(Objects.Each(and(another, creature, you)), 1, 1)),
    )

    // CR 702.4a (double strike), 603.6a, 613.4c
    @Test
    fun terrorOfMountOrrin() = card(
        name = "Terror of Mount Orrin",
        cost = "{5}{R}{R}",
        type = "Creature — Dragon",
        pt = "5/5",
        text = """
            Flying
            Double strike (This creature deals both first-strike and regular combat damage.)
            When this creature enters, creatures you control gain double strike until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.DoubleStrike,
        triggered(entersSelf, gains(yourCreatures, Keyword.DoubleStrike)),
    )

    // CR 611.2a, 613.1f, 700.4 (dies), 400.7 (new object), 122.1 (counter)
    @Test
    fun lingeringSpite() = card(
        name = "Lingering Spite",
        cost = "{B}",
        type = "Instant",
        text = "Until end of turn, target creature gains \"When this creature dies, return it to the battlefield tapped under its owner's control with a +1/+1 counter on it.\"",
    ) parsesTo abilities(
        spell(gains(target("creature"), triggered(diesSelf,
            Step.Do(Effect.Move(Objects.EventObject(arcanum.api.EventRole.After),
                arcanum.api.Destination(Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.EventObject(arcanum.api.EventRole.After)),
                    counters = listOf(arcanum.api.CountersToPut(p1p1, n(1))))))
        )), targets = listOf(slot("creature", creature))),
    )

    // CR 700.2 (modes), 702.12a, 701.8 (destroy), 208.1
    @Test
    fun resoluteStand() = card(
        name = "Resolute Stand",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
            • Destroy target creature with toughness 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creature))),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtLeast, n(4)))))),
        ))),
    )

    // CR 602.1, 613.4c, 702.19a
    @Test
    fun wildmaneCaller() = card(
        name = "Wildmane Caller",
        cost = "{2}{G}{G}",
        type = "Creature — Elf Shaman",
        pt = "4/3",
        text = "{8}: Target creature gets +5/+5 and gains trample until end of turn. (It can deal excess combat damage to the player or planeswalker it's attacking.)",
    ) parsesTo abilities(
        activated(manaCost("{8}"), pump(target("creature"), 5, 5, has(Keyword.Trample)), targets = listOf(slot("creature", creature))),
    )
}
