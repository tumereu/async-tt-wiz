package arcanum.parser.sets.mom

import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 04: incubate, part 2 (CR 701.53, 111.10i). */
class MomChunk04IncubateTest {
    private val phyrexian = sub("Phyrexian")
    private fun modal(vararg options: arcanum.api.Mode) = arcanum.api.Ability.Spell(arcanum.api.Instructions(modes = modes(*options)))

    // CR 701.53a; CR 602.5d: only as a sorcery; exiling it from the graveyard is part of the cost (CR 602.2)
    @Test
    fun ichorSipper() = card(
        name = "Ichor Sipper",
        cost = "{B}",
        type = "Creature — Phyrexian Vampire",
        text = """
            Lifelink
            {B}, Exile this card from your graveyard: Incubate 2. Activate only as a sorcery. ${incubateReminder("two")}
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Lifelink,
        activated(cost(mana("{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), incubate(2), rules = asSorcery),
    )

    // CR 603.6c, 701.53a
    @Test
    fun infestedTurncoat() = card(
        name = "Infested Turncoat",
        cost = "{4}{W}",
        type = "Creature — Phyrexian Knight",
        text = "When this creature dies, incubate 3. ${incubateReminder("three")}",
        pt = "4/3",
    ) parsesTo abilities(
        triggered(diesSelf, incubate(3)),
    )

    // CR 406.2, 701.53a; CR 608.2b: with its only target illegal the spell doesn't resolve, so no token
    @Test
    fun ruthlessRecycling() = card(
        name = "Ruthless Recycling",
        cost = "{4}{B}{B}",
        type = "Instant",
        text = "Exile target creature. Incubate 3. ${incubateReminder("three")}",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile), incubate(3), targets = listOf(slot("creature", creature))),
    )

    // CR 701.27a: a permanent transforms; "into a Phyrexian": it is a Phyrexian after transforming (CR 603.2, not a look-back trigger,
    // CR 603.10); "it" is that permanent
    @Test
    fun compleatInterrogator() = card(
        name = "Compleat Interrogator",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Knight",
        text = """
            When this creature enters, incubate 2. ${incubateReminder("two")}
            Whenever a permanent you control transforms into a Phyrexian, put a +1/+1 counter on it.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, incubate(2)),
        triggered(on(EventPattern.Transform(and(permanentFilter, you, phyrexian))), counters(Objects.EventObject(EventRole.Object))),
    )

    // CR 701.53a; CR 613.1f: Phyrexians you control have vigilance
    @Test
    fun phyrexianQuickening() = card(
        name = "Phyrexian Quickening",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, incubate 4. ${incubateReminder("four")}
            Phyrexians you control have vigilance.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, incubate(4)),
        static(has(Keyword.Vigilance), affects = Objects.Each(and(phyrexian, you))),
    )

    // CR 107.3m: X in its enters trigger is the X it was cast with; CR 701.53a that many times; CR 701.27a: transform a target Incubator
    @Test
    fun broodExarch() = card(
        name = "Brood Exarch",
        cost = "{X}{X}{W}",
        type = "Creature — Phyrexian Cat Cleric",
        text = """
            When this creature enters, incubate 3 X times. (To incubate 3, create an Incubator token with three +1/+1 counters on it and "{2}: Transform this token." It transforms into a 0/0 Phyrexian artifact creature.)
            {T}: Transform target Incubator token you control.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Repeat(Value.CastX(of = Objects.Self), listOf(incubate(3)))),
        activated(cost(tap), doing(Effect.Transform(target("token"))), targets = listOf(slot("token", and(sub("Incubator"), token, you)))),
    )

    // CR 701.53a; CR 613.4c: Phyrexians you control get +1/+1
    @Test
    fun carvedPerfection() = card(
        name = "Carved Perfection",
        cost = "{2}{W}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, incubate 2. ${incubateReminder("two")}
            Phyrexians you control get +1/+1.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, incubate(2)),
        static(pt(1, 1), affects = Objects.Each(and(phyrexian, you))),
    )

    // CR 608.2h: "If it's a creature": the target as the spell resolves (a player, planeswalker or battle can't block, CR 509.1a); CR 701.53a
    @Test
    fun scorchingBarb() = card(
        name = "Scorching Barb",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Scorching Barb deals 2 damage to any target. If it's a creature, it can't block this turn. Incubate 1. ${incubateReminder("a")}"
            .replace("with a +1/+1 counters", "with a +1/+1 counter"),
    ) parsesTo abilities(
        spell(
            damage(2, toAny),
            Step.If(Condition.ObjectMatches(target("any"), creature), then = listOf(untilEndOfTurn(target("any"), Mechanic.CantBlock(ObjectFilter.Any)))),
            incubate(1),
            targets = listOf(anyTarget),
        ),
    )

    // CR 700.2; CR 701.53a; CR 701.27a: "transform an Incubator token you control" is chosen as the mode resolves, not targeted (the one just
    // created may be chosen, CR 608.2c)
    @Test
    fun shatterThePortal() = card(
        name = "Shatter the Portal",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Destroy target nontoken artifact or enchantment an opponent controls. Incubate 2. ${incubateReminder("two")}
            • Incubate 2, then transform an Incubator token you control.
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("permanent")), incubate(2),
                targets = listOf(slot("permanent", and(not(token), or(artifact, enchantment), opponents)))),
            mode(incubate(2),
                Step.Choose(Choice.Objects(and(sub("Incubator"), token, you), n(1), n(1)), bind = "chosen", chooser = Players.You),
                doing(Effect.Transform(Objects.Bound("chosen")))),
        ),
    )

    // CR 406.2; CR 107.3c: X is the number of creatures this exile moved (tokens included, CR 111.7 happens after); CR 701.53a, with no
    // counters when none were exiled
    @Test
    fun dawnfall() = card(
        name = "Dawnfall",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = "Exile all creatures. Incubate X, where X is the number of creatures exiled this way. ${incubateReminder("X")}",
    ) parsesTo abilities(
        spell(
            move(Objects.Each(creature), Zone.Exile, postBind = "exiled"),
            doing(Effect.Action(arcanum.api.KeywordAction.Incubate(Value.Count(Objects.Bound("exiled"))))),
        ),
    )

    // CR 119.3, 701.53a; CR 613.1f: Phyrexians you control have reach
    @Test
    fun knottedSkyline() = card(
        name = "Knotted Skyline",
        cost = "{4}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, you gain 5 life and incubate 5. ${incubateReminder("five")}
            Phyrexians you control have reach.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(5), incubate(5)),
        static(has(Keyword.Reach), affects = Objects.Each(and(phyrexian, you))),
    )

    // CR 601.2c, 603.2: a spell that targets one or more permanents; CR 701.53a
    @Test
    fun plowerOfFlesh() = card(
        name = "Plower of Flesh",
        cost = "{3}{W}",
        type = "Creature — Phyrexian Knight",
        text = "Whenever you cast a spell that targets one or more permanents, incubate 2. ${incubateReminder("two")}",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Targets(objects = Objects.Each(permanentFilter))), incubate(2)),
    )

    // CR 701.20a: the hand is revealed; "You may choose … If you do" / "If you don't": whether a card was chosen (CR 608.2c, none can be when
    // the hand has no such card); CR 701.9a: that player discards it; CR 701.53a
    @Test
    fun harrowingRevelation() = card(
        name = "Harrowing Revelation",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You may choose a creature or battle card from it. If you do, that player discards that card. If you don't, incubate 3. ${incubateReminder("three")}",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Pay(steps = listOf(Step.Choose(Choice.Objects(or(creature, battle), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen",
                chooser = Players.You)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"),
                then = listOf(doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen")))),
                otherwise = listOf(incubate(3))),
            targets = listOf(targetOpponent),
        ),
    )
}
