package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class FdnChunk05HexproofKickerMillMorbidTest {
    /** CR 702.33d: "kicked" asks whether the kicker was paid. */
    private val kicked = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)

    /** CR 700.4: a creature died means it went from the battlefield to a graveyard. */
    private val morbid = Condition.HappenedThisTurn(
        EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
        atLeast = 1,
        measure = Measure.Events,
    )

    // CR 702.11d (hexproof from a card type), CR 103.4 (starting life), CR 613.4c (both pumps add)
    @Test
    fun verenna() = card(
        name = "Verenna, Saint of Vespers",
        cost = "{2}{W}{B}",
        type = "Legendary Creature — Vampire Knight",
        pt = "4/4",
        text = """
            Lifelink, hexproof from instants
            As long as your life total is greater than your starting life total, Verenna gets +1/+1 and has menace. Verenna gets an additional +5/+5 as long as your life total is at least 10 greater than your starting life total.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Keyword.HexproofFrom(instant),
        static(
            pt(1, 1),
            has(Keyword.Menace),
            condition = Condition.Compare(Value.LifeOf(Players.You), Comparison.Greater, Value.StartingLife(Players.You)),
        ),
        static(
            pt(5, 5),
            condition = Condition.Compare(
                Value.LifeOf(Players.You),
                Comparison.AtLeast,
                Value.Sum(listOf(Value.StartingLife(Players.You), n(10))),
            ),
        ),
    )

    // CR 702.11d (hexproof from a colour)
    @Test
    fun paladinOfMercy() = card(
        name = "Paladin of Mercy",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            First strike (This creature deals combat damage before creatures without first strike.)
            Hexproof from black (This creature can't be the target of black spells or abilities your opponents control.)
            This creature gets +1/+0 as long as any player controls a black permanent.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.HexproofFrom(ObjectFilter.OfColor(Color.Black)),
        static(pt(1, 0), condition = Condition.Exists(and(ObjectFilter.OfColor(Color.Black), permanentFilter), atLeast = 1)),
    )

    // CR 702.11d
    @Test
    fun paladinOfSpite() = card(
        name = "Paladin of Spite",
        cost = "{1}{B}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            First strike (This creature deals combat damage before creatures without first strike.)
            Hexproof from white (This creature can't be the target of white spells or abilities your opponents control.)
            This creature gets +1/+0 as long as any player controls a white permanent.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.HexproofFrom(ObjectFilter.OfColor(Color.White)),
        static(pt(1, 0), condition = Condition.Exists(and(ObjectFilter.OfColor(Color.White), permanentFilter), atLeast = 1)),
    )

    // CR 702.33d, CR 601.2c: each version has its own targets, chosen only when it is the one that happens
    @Test
    fun stalwartPrayer() = card(
        name = "Stalwart Prayer",
        cost = "{W}",
        type = "Instant",
        text = """
            Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
            Target creature you control gains indestructible until end of turn. If this spell was kicked, instead any number of target creatures you control gain indestructible until end of turn. (Damage and effects that say "destroy" don't destroy them.)
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}{W}"))),
        spell(
            Step.If(
                condition = kicked,
                then = listOf(gains(target("creatures"), Keyword.Indestructible)),
                otherwise = listOf(gains(target("creature"), Keyword.Indestructible)),
            ),
            targets = listOf(
                slot("creature", creatureYouControl).copy(onlyIf = Condition.Not(kicked)),
                slot("creatures", creatureYouControl, min = 0, max = null).copy(onlyIf = kicked),
            ),
        ),
    )

    // CR 702.33d, CR 614.1c
    @Test
    fun mossbackHerd() = card(
        name = "Mossback Herd",
        cost = "{1}{G}",
        type = "Creature — Beast",
        pt = "2/2",
        text = """
            Kicker {2}{G} (You may pay an additional {2}{G} as you cast this spell.)
            If this creature was kicked, it enters with two +1/+1 counters on it.
            Each creature you control with a +1/+1 counter on it has trample. (It can deal excess combat damage to the player or planeswalker it's attacking.)
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}{G}"))),
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(2))),
            )),
            affects = Affected.Objects(Objects.Self),
            condition = kicked,
        ),
        static(
            has(Keyword.Trample),
            affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))),
        ),
    )

    // CR 702.33d, CR 701.23
    @Test
    fun seedfallRite() = card(
        name = "Seedfall Rite",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Kicker {2} (You may pay an additional {2} as you cast this spell.)
            Search your library for a basic land card, put it onto the battlefield, then shuffle. If this spell was kicked, instead search your library for two basic land cards, put them onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}"))),
        spell(
            Step.If(
                condition = kicked,
                then = listOf(search(basicLand(), max = 2), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
                otherwise = listOf(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
            ),
        ),
    )

    // CR 702.33d
    @Test
    fun flashSpark() = card(
        name = "Flash Spark",
        cost = "{R}",
        type = "Instant",
        text = """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Flash Spark deals 2 damage to any target. If this spell was kicked, it deals 4 damage instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}"))),
        spell(
            Step.If(condition = kicked, then = listOf(damage(4, toAny)), otherwise = listOf(damage(2, toAny))),
            targets = listOf(anyTarget),
        ),
    )

    // CR 702.33d, CR 603.4 (an intervening "if"), CR 701.21a
    @Test
    fun wardenOfAshfen() = card(
        name = "Warden of Ashfen",
        cost = "{B}{B}",
        type = "Creature — Vampire Warrior",
        pt = "2/2",
        text = """
            Kicker {B} (You may pay an additional {B} as you cast this spell.)
            When this creature enters, if it was kicked, target player sacrifices a creature of their choice.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{B}"))),
        triggered(
            entersSelf,
            Step.Choose(
                Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "sacrificed",
                chooser = Players.Target("player"),
            ),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetPlayer),
            interveningIf = kicked,
        ),
    )

    // CR 702.33d
    @Test
    fun tidalRecoil() = card(
        name = "Tidal Recoil",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            Return target nonland permanent to its owner's hand. If this spell was kicked, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{1}{U}"))),
        spell(
            move(target("permanent"), Zone.Hand),
            Step.If(condition = kicked, then = listOf(draw(1))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter))),
        ),
    )

    // CR 702.33d, CR 603.4
    @Test
    fun graveAcolyte() = card(
        name = "Graveborn Acolyte",
        cost = "{1}{B}",
        type = "Creature — Vampire Cleric",
        pt = "2/1",
        text = """
            Kicker {3}{B} (You may pay an additional {3}{B} as you cast this spell.)
            Lifelink
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, if it was kicked, return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{3}{B}"))),
        Keyword.Lifelink,
        Keyword.Menace,
        triggered(
            entersSelf,
            move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature))),
            interveningIf = kicked,
        ),
    )

    // CR 702.33d, CR 707.2
    @Test
    fun mirrorRite() = card(
        name = "Mirror Rite",
        cost = "{2}{U}{U}",
        type = "Sorcery",
        text = """
            Kicker {5} (You may pay an additional {5} as you cast this spell.)
            Create a token that's a copy of target creature. If this spell was kicked, create five of those tokens instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{5}"))),
        spell(
            Step.If(
                condition = kicked,
                then = listOf(create(TokenSpec.CopyOf(target("creature")), n(5))),
                otherwise = listOf(create(TokenSpec.CopyOf(target("creature")), n(1))),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.33d, CR 603.4
    @Test
    fun dawnblessedMedic() = card(
        name = "Dawnblessed Medic",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "3/1",
        text = """
            Kicker {1}{W} (You may pay an additional {1}{W} as you cast this spell.)
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
            When this creature enters, if it was kicked, return target nonland permanent card with mana value 2 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{1}{W}"))),
        Keyword.Lifelink,
        triggered(
            entersSelf,
            move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(nonland, permanentCard, manaValue(Comparison.AtMost, 2)))),
            interveningIf = kicked,
        ),
    )

    // CR 701.17, CR 107.3: only the creature cards that reached a graveyard count (CR 400.7)
    @Test
    fun graveMuster() = card(
        name = "Grave Muster",
        cost = "{X}{B}{B}",
        type = "Sorcery",
        text = """
            Each player mills X cards. For each creature card put into a graveyard this way, you create a tapped 2/2 black Zombie creature token. (To mill a card, a player puts the top card of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Mill(Players.Each(PlayerFilter.Any), Value.Announced(name = "X"), postBind = "milled")),
            Step.Do(Effect.CreateToken(
                creatureToken("Zombie", listOf(Color.Black), 2, 2),
                Value.Count(Objects.Where(Objects.Bound("milled"), and(creature, inZone(Zone.Graveyard)))),
                Players.You,
                tapped = true,
            )),
            announced = listOf("X"),
        ),
    )

    // CR 701.17, CR 207.2c (an ability word has no rules meaning)
    @Test
    fun howlingWailmass() = card(
        name = "Howling Wailmass",
        cost = "{3}{B}",
        type = "Creature — Spirit",
        pt = "2/3",
        text = """
            Flying
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            Threshold — This creature gets +2/+1 as long as there are seven or more cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, mill(3)),
        static(pt(2, 1), condition = Condition.Exists(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 7)).copy(abilityWord = "Threshold"),
    )

    // CR 603.4, CR 700.2: the modes are chosen as the ability goes on the Stack, the "if" is checked also on resolution
    @Test
    fun keepersOfTheHollow() = card(
        name = "Keepers of the Hollow",
        cost = "{1}{B}{G}{G}",
        type = "Creature — Elf Warlock",
        pt = "3/4",
        text = """
            Morbid — At the beginning of your end step, if a creature died this turn, choose one —
            • You gain 2 life.
            • You draw a card and you lose 1 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            stepBegins(TurnStep.End, PlayerFilter.You),
            Instructions(modes = Modes(
                listOf(
                    mode(gainLife(2)),
                    mode(draw(1), loseLife(1)),
                ),
                min = 1,
                max = 1,
                chooser = Players.You,
            )),
            interveningIf = morbid,
            abilityWord = "Morbid",
        ),
    )

    // CR 702.21a, CR 603.4
    @Test
    fun snickeringStalker() = card(
        name = "Snickering Stalker",
        cost = "{3}{G}",
        type = "Creature — Hyena Rogue",
        pt = "4/3",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            Morbid — At the beginning of your end step, if a creature died this turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        triggered(
            stepBegins(TurnStep.End, PlayerFilter.You),
            counters(Objects.Self),
            interveningIf = morbid,
            abilityWord = "Morbid",
        ),
    )

    // CR 603.4
    @Test
    fun razorjawHerd() = card(
        name = "Razorjaw Herd",
        cost = "{3}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "4/5",
        text = """
            Morbid — At the beginning of your end step, if a creature died this turn, put two +1/+1 counters on target creature you control.
        """,
    ) parsesTo abilities(
        triggered(
            stepBegins(TurnStep.End, PlayerFilter.You),
            counters(target("creature"), 2),
            targets = listOf(slot("creature", creatureYouControl)),
            interveningIf = morbid,
            abilityWord = "Morbid",
        ),
    )

    // CR 502.3, CR 603.4: "each end step", not only yours
    @Test
    fun slumberingHellhound() = card(
        name = "Slumbering Hellhound",
        cost = "{1}{R}",
        type = "Creature — Dog",
        pt = "4/2",
        text = """
            This creature doesn't untap during your untap step.
            Morbid — At the beginning of each end step, if a creature died this turn, untap this creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.DoesntUntap),
        triggered(
            stepBegins(TurnStep.End, PlayerFilter.Any),
            untapIt(Objects.Self),
            interveningIf = morbid,
            abilityWord = "Morbid",
        ),
    )

    // CR 608.2c: "instead" chooses by the condition as the ability resolves; no intervening "if" here
    @Test
    fun sorrowfulWraith() = card(
        name = "Sorrowful Wraith",
        cost = "{4}{B}",
        type = "Creature — Spirit",
        pt = "5/3",
        text = """
            Morbid — When this creature enters, target creature an opponent controls gets -1/-1 until end of turn. If a creature died this turn, that creature gets -13/-13 until end of turn instead.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.If(
                condition = morbid,
                then = listOf(pump(target("creature"), -13, -13)),
                otherwise = listOf(pump(target("creature"), -1, -1)),
            ),
            targets = listOf(slot("creature", creatureOpponentControls)),
            abilityWord = "Morbid",
        ),
    )
}
