package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 12r: control while an Aura stays attached, up to X modes for a modal spell's modes, damage equal to toughness and
 * "plus X" for this turn, an enters batch none of which had mana spent to cast it.
 */
class OtjChunk12rLegendsTest {
    private val attached = Objects.EventObject(EventRole.To)
    private val attachment = Objects.EventObject(EventRole.Object)

    // CR 701.3a: the Aura becoming attached; CR 202.3: the permanent's mana value against the Aura's, as it becomes attached;
    // CR 611.2b, 613.1b: control lasts only while that Aura is attached to that permanent
    @Test
    fun veyraTheEnthraller() = card(
        name = "Veyra, the Enthraller",
        cost = "{1}{W}{U}{B}",
        type = "Legendary Creature — Human Warlock",
        pt = "4/4",
        text = """
            Lifelink
            Whenever an Aura you control becomes attached to a nonland permanent an opponent controls with mana value less than or equal to that Aura's mana value, gain control of that permanent for as long as that Aura is attached to it.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(on(EventPattern.Attach(and(sub("Aura"), you), to = and(nonland, permanentFilter, opponents))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(attached),
                Duration.AsLongAs(Condition.ObjectMatches(attachment, ObjectFilter.Attached(to = attached))))),
            triggersOnlyIf = Condition.ObjectMatches(attached, ObjectFilter.ManaValue(Comparison.AtMost,
                Value.Of(NumericProperty.ManaValue, attachment, Combine.Single)))),
    )

    // CR 700.2: a modal spell; X counts each mode chosen for that spell; CR 603.3c: the trigger's modes are chosen as it is put on the Stack
    @Test
    fun torvinOfManyRoads() = card(
        name = "Torvin of Many Roads",
        cost = "{G}{U}{R}",
        type = "Legendary Creature — Human Wizard",
        pt = "3/3",
        text = """
            Whenever you cast a modal spell, choose up to X, where X is the number of times you chose a mode for that spell —
            • Exile the top card of your library. Until the end of your next turn, you may play it.
            • Put a +1/+1 counter on Torvin. It gains trample until end of turn.
            • Create a 1/1 blue Bird creature token with flying.
        """,
    ) parsesTo abilities(
        Ability.Triggered(youCast(ObjectFilter.Modal), Instructions(modes = Modes(listOf(
            mode(move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
            mode(counters(Objects.Self), gains(Objects.Self, Keyword.Trample)),
            mode(create(creatureToken("Bird", listOf(Color.Blue), 1, 1, abilities = listOf(Keyword.Flying)))),
        ), min = 0, max = 3, chooser = Players.You, maxCount = Value.ModesChosenFor(Objects.EventObject(EventRole.Object))))),
    )

    // CR 120.3: the damage dealt to the creature, compared with its toughness as it is dealt (CR 603.2); CR 614.1a, 611.2a: a replacement the
    // ability makes for the rest of the turn, its X fixed as it resolves (CR 608.2h)
    @Test
    fun marsaQuickdrawDeadeye() = card(
        name = "Marsa Quickdraw, Deadeye",
        cost = "{R}{W}",
        type = "Legendary Creature — Human Mercenary",
        pt = "2/3",
        text = """
            Whenever a source you control deals noncombat damage to a creature equal to that creature's toughness, draw a card.
            {X}, {T}: If a source you control would deal noncombat damage to a permanent or player this turn, it deals that much damage plus X instead.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(you, RecipientFilter(objects = creature), combat = false)), draw(1),
            triggersOnlyIf = Condition.Compare(Value.EventAmount, Comparison.Equal, toughness(Objects.EventObject(EventRole.Object)))),
        Ability.Activated(cost(mana("{X}"), tap), Instructions(steps = listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
            EventPattern.Damage(you, RecipientFilter(), combat = false), modify = listOf(Modification.AddAmount(Value.Announced("X"))))),
            null, Duration.EndOfTurn))), announced = listOf("X")), anyTime),
    )

    // CR 603.2c: one trigger for the batch (by wording); CR 603.4: no mana was spent to cast any of them (one not cast had none spent,
    // CR 601.2h, 400.7d), checked as it triggers and as it resolves
    @Test
    fun kaimonTheUnseen() = card(
        name = "Kaimon, the Unseen",
        cost = "{U}{B}",
        type = "Legendary Creature — Human Ninja Rogue",
        pt = "2/3",
        text = """
            Menace
            Whenever Kaimon and/or one or more other nontoken creatures you control enter, if none of them were cast or no mana was spent to cast them, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(Trigger.On(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(not(token), creature, you)), to = Zone.Battlefield),
            oneOrMore = true), draw(1),
            interveningIf = Condition.Compare(Value.ManaSpent(of = Objects.EventObject(EventRole.After)), Comparison.Equal, n(0))),
    )
}
