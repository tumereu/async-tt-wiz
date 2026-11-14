package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 03: incubate, part 1 (CR 701.53, 111.10i). */
class MomChunk03IncubateTest {
    private val phyrexian = sub("Phyrexian")
    private val sagaReminder = "(As this Saga enters and after your draw step, add a lore counter.)"
    private fun chapter(number: Int, vararg steps: Step) =
        Ability.Triggered(Trigger.Chapter(listOf(number)), Instructions(steps = steps.toList()))

    // CR 701.53a; CR 118.12a: "unless its controller pays", then "If they do" reads the same payment
    @Test
    fun absorbQuintessence() = card(
        name = "Absorb Quintessence",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target creature or battle spell unless its controller pays {4}. If they do, you incubate 2. ${incubateReminder("two")}",
    ) parsesTo abilities(
        spell(
            Step.Pay(manaCost("{4}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            Step.If(Condition.Did("paid"), then = listOf(incubate(2))),
            targets = listOf(slot("spell", and(or(creature, battle), ObjectFilter.Category(ObjectCategory.Spell)))),
        ),
    )

    // CR 603.10a: X is its power as it last existed on the battlefield
    @Test
    fun swollenRefiner() = card(
        name = "Swollen Refiner",
        cost = "{2}{B}",
        type = "Creature — Phyrexian",
        text = """
            Sacrifice another Phyrexian: Put a +1/+1 counter on this creature.
            When this creature dies, incubate X, where X is its power. ${incubateReminder("X")}
        """,
        pt = "3/2",
    ) parsesTo abilities(
        activated(cost(sacrificeA(and(another, phyrexian))), counters(Objects.Self)),
        triggered(diesSelf, incubate(power(Objects.EventObject(EventRole.Before)))),
    )

    // CR 603.4, 608.2h: the spell's mana value, as it last existed if it has left the Stack
    @Test
    fun steelBroodReefshark() = card(
        name = "Steel Brood Reefshark",
        cost = "{2}{U}",
        type = "Creature — Phyrexian Shark",
        text = """
            Flying
            Whenever you cast a noncreature spell, incubate X, where X is that spell's mana value. ${incubateReminder("X")}
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreatureSpell), incubate(Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single))),
    )

    // CR 701.53a; CR 602.2: the sacrifice is a cost
    @Test
    fun perfectedStalkmaster() = card(
        name = "Perfected Stalkmaster",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Elf Warrior",
        text = "{1}, {T}, Sacrifice another creature or artifact: Incubate 3. ${incubateReminder("three")}",
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeA(and(another, or(creature, artifact)))), incubate(3)),
    )

    // CR 701.53a
    @Test
    fun transmuterBrute() = card(
        name = "Transmuter Brute",
        cost = "{3}{G}",
        type = "Creature — Phyrexian Beast",
        text = "When this creature enters, incubate 5. ${incubateReminder("five")}",
        pt = "0/1",
    ) parsesTo abilities(
        triggered(entersSelf, incubate(5)),
    )

    // CR 701.27a: a permanent transforms; CR 712.14a: one put onto the battlefield transformed enters back face up; CR 603.2h: "Do this
    // only once each turn" limits the draw, not the trigger
    @Test
    fun blightOfSpirehold() = card(
        name = "Blight of Spirehold",
        cost = "{4}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, incubate 4. ${incubateReminder("four")}
            Whenever a permanent you control transforms or a permanent you control enters transformed, you may draw a card. Do this only once each turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, incubate(4)),
        Ability.Triggered(Trigger.On(listOf(EventPattern.Transform(and(permanentFilter, you)),
            EventPattern.ZoneChange(and(permanentFilter, you, ObjectFilter.HasStatus(Status.Transformed)), to = Zone.Battlefield))),
            Instructions(steps = listOf(Step.May(listOf(draw(1)), Players.You))), actionOncePerTurn = true),
    )

    // CR 120.3, 603.2c: each damage event from an opponent's source to you or a permanent you control; CR 118.12a: that source's
    // controller may pay {1}; CR 608.2h: its last controller if it has left. Chapter I: CR 701.53a five times, then CR 701.27a.
    // Chapter III: CR 701.8a; CR 712.14: the Saga returns front face up
    @Test
    fun vessaGrandMatron() = cardFaces(Layout.Transform,
        card(
            name = "Vessa, Grand Matron",
            cost = "{2}{W}{W}",
            type = "Legendary Creature — Phyrexian Praetor",
            text = """
                Vigilance
                Whenever a source an opponent controls deals damage to you or a permanent you control, that source's controller loses 2 life unless they pay {1}.
                {2}{W}, Sacrifice three other creatures: Exile Vessa, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            """,
            pt = "3/5",
        ) to abilities(
            Keyword.Vigilance,
            triggered(on(EventPattern.Damage(opponents, RecipientFilter(objects = and(permanentFilter, you), players = PlayerFilter.You))),
                Step.Pay(manaCost("{1}"), payer = Players.ControllerOf(Objects.EventObject(EventRole.Source)), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(loseLife(2, Players.ControllerOf(Objects.EventObject(EventRole.Source)))))),
            activated(cost(mana("{2}{W}"), CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(3)))),
                move(Objects.Self, Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")), transformed = true))),
                rules = asSorcery),
        ),
        card(
            name = "The Gilded Engravings",
            cost = null,
            type = "Enchantment — Saga",
            text = """
                $sagaReminder
                I — Incubate 2 five times, then transform all Incubator tokens you control.
                II — Creatures you control get +1/+1 and gain double strike until end of turn.
                III — Destroy all other permanents except for artifacts, lands, and Phyrexians. Exile this Saga, then return it to the battlefield (front face up).
            """,
            colorIndicator = "W",
        ) to abilities(
            chapter(1, incubateTimes(5, n(2)), transformAllIncubators),
            chapter(2, pump(Objects.Each(creatureYouControl), 1, 1, has(Keyword.DoubleStrike))),
            chapter(3, destroy(Objects.Each(and(another, permanentFilter, not(or(artifact, land, phyrexian))))),
                move(Objects.Self, Zone.Exile, postBind = "exiled"), Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield)))),
        ),
    )

    // CR 603.2: one trigger for this creature or another Phyrexian entering; CR 701.53a
    @Test
    fun heartOfDogma() = card(
        name = "Heart of Dogma",
        cost = "{3}{W}{W}",
        type = "Creature — Phyrexian",
        text = """
            Flying
            Whenever this creature or another Phyrexian you control enters, incubate 2. ${incubateReminder("two")}
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, phyrexian, you))), incubate(2)),
    )

    // CR 701.53a, then CR 121.1
    @Test
    fun gazeOfTheCogmind() = card(
        name = "Gaze of the Cogmind",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Incubate 3. ${incubateReminder("three")}
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(incubate(3), draw(1)),
    )

    // CR 603.10a: X is its power as it last existed on the battlefield (a pump included)
    @Test
    fun smelterImp() = card(
        name = "Smelter Imp",
        cost = "{1}{R}",
        type = "Creature — Phyrexian Gremlin",
        text = """
            {1}{R}: This creature gets +1/+0 until end of turn.
            When this creature dies, incubate X, where X is its power. ${incubateReminder("X")}
        """,
        pt = "1/2",
    ) parsesTo abilities(
        activated(manaCost("{1}{R}"), pump(Objects.Self, 1, 0)),
        triggered(diesSelf, incubate(power(Objects.EventObject(EventRole.Before)))),
    )

    // CR 701.53a; CR 701.25a
    @Test
    fun boonOfPerfection() = card(
        name = "Boon of Perfection",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            When this enchantment enters, incubate 3. ${incubateReminder("three")}
            Whenever a Phyrexian you control dies, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, incubate(3)),
        triggered(dies(and(phyrexian, you)), Step.Do(Effect.Surveil(Players.You, n(1)))),
    )

    // CR 700.2: a modal triggered ability; CR 701.53a twice; CR 701.27a; CR 611.2c: the gains apply to the Phyrexians there as it resolves
    @Test
    fun vyrlaHarbingerOfHunger() = card(
        name = "Vyrla, Harbinger of Hunger",
        cost = "{3}{B}{G}",
        type = "Legendary Creature — Phyrexian Zombie Elf",
        text = """
            At the beginning of combat on your turn, choose one —
            • Incubate 2 twice. (To incubate 2, create an Incubator token with two +1/+1 counters on it and "{2}: Transform this token." It transforms into a 0/0 Phyrexian artifact creature.)
            • Transform all Incubator tokens you control.
            • Phyrexians you control gain first strike and deathtouch until end of turn.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Ability.Triggered(beginningOfCombatOnYourTurn, Instructions(modes = modes(
            mode(incubateTimes(2, n(2))),
            mode(transformAllIncubators),
            mode(gains(Objects.Each(and(phyrexian, you)), Keyword.FirstStrike, Keyword.Deathtouch))))),
    )

    // CR 107.3c: X is defined by "where X is", counted as it resolves (CR 608.2h); CR 701.53a twice
    @Test
    fun gleamingDaybreak() = card(
        name = "Gleaming Daybreak",
        cost = "{2}{G}{G}",
        type = "Sorcery",
        text = "Incubate X twice, where X is the number of lands you control. (To incubate X, create an Incubator token with X +1/+1 counters on it and \"{2}: Transform this token.\" It transforms into a 0/0 Phyrexian artifact creature.)",
    ) parsesTo abilities(
        spell(incubateTimes(2, count(and(land, you)))),
    )
}
