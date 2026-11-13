package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 05: suspect (CR 701.60): suspecting an object the text names, conditions on being suspected, and "no longer suspected". */
class MkmChunk05SuspectTest {
    // CR 701.60a: a creature stops being suspected when an effect says so (each suspected creature, whoever controls it); CR 603.6c, 603.10a:
    // the dies trigger; CR 601.2c: "up to one target creature an opponent controls" may be left empty
    @Test
    fun pardoningSphinx() = card(
        name = "Pardoning Sphinx",
        cost = "{4}{W}",
        type = "Creature — Sphinx",
        pt = "4/3",
        text = """
            Flying
            When this creature enters, all suspected creatures are no longer suspected.
            When this creature dies, you gain 3 life and suspect up to one target creature an opponent controls. (A suspected creature has menace and can't block.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, noLongerSuspected(Objects.Each(and(suspected, creature)))),
        triggered(diesSelf, gainLife(3), suspect(target("creature")), targets = listOf(slot("creature", creatureOpponentControls, min = 0))),
    )

    // CR 608.2b: whether the target is suspected is checked as the ability resolves; CR 701.60a: otherwise it becomes suspected
    @Test
    fun grevikDorn() = card(
        name = "Grevik Dorn, Spirit of Verdict",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Spirit Detective",
        pt = "2/4",
        text = """
            Double strike, vigilance
            Whenever Grevik Dorn enters or attacks, choose up to one target creature. If it's suspected, exile it. Otherwise, suspect it. (A suspected creature has menace and can't block.)
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Keyword.Vigilance,
        triggered(entersOrAttacksSelf,
            ifThen(isSuspected(target("creature")), move(target("creature"), Zone.Exile, postBind = "exiled"), otherwise = listOf(suspect(target("creature")))),
            targets = listOf(slot("creature", creature, min = 0))),
    )

    // CR 701.60a: "suspect it" in this creature's enters trigger is this creature; CR 120.3, 603.2: "is dealt damage" with that much life lost
    @Test
    fun spikedWarden() = card(
        name = "Spiked Warden",
        cost = "{3}{B}",
        type = "Artifact Creature — Construct",
        pt = "1/1",
        text = """
            Indestructible
            When this creature enters, suspect it. (It has menace and can't block.)
            Whenever this creature deals combat damage to a player, you draw a card and you lose 1 life.
            Whenever this creature is dealt damage, target opponent loses that much life.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        triggered(entersSelf, suspect(Objects.Self)),
        triggered(combatDamageToAPlayer, draw(1), loseLife(1)),
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), per = arcanum.api.DamageOccurrence.Recipient)),
            Instructions(targets = listOf(targetOpponent), steps = listOf(Step.Do(Effect.LoseLife(Players.Target("opponent"), Value.EventAmount)))),
        ),
    )

    // CR 101.2: can't be countered; CR 701.60a: the creature it gained control of becomes suspected
    @Test
    fun handInTheTill() = card(
        name = "Hand in the Till",
        cost = "{4}{R}",
        type = "Instant",
        text = """
            This spell can't be countered. (This includes by the ward ability.)
            Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. Suspect it. (It has menace and can't block.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            suspect(target("creature")),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 603.2c: one trigger for creatures entering together; CR 603.4: "if this creature is suspected" is checked as it triggers and resolves;
    // CR 701.60a: one of the creatures that entered becomes suspected, and only then this one stops being suspected
    @Test
    fun panickedPatsy() = card(
        name = "Panicked Patsy",
        cost = "{R}",
        type = "Creature — Goat",
        pt = "1/1",
        text = """
            Haste
            When this creature enters, suspect it. (It has menace and can't block.)
            Whenever one or more other creatures you control enter, if this creature is suspected, you may suspect one of the other creatures. If you do, this creature is no longer suspected.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, suspect(Objects.Self)),
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, creature, you), to = Zone.Battlefield), oneOrMore = true),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.EventObject(EventRole.After)), bind = "chosen", chooser = Players.You),
            suspect(Objects.Bound("chosen")),
            ifThen(Condition.NonEmpty("chosen"), noLongerSuspected(Objects.Self)),
            interveningIf = isSuspected()),
    )

    // CR 400.7, 608.2c: "it" is the permanent the card became on the battlefield; CR 701.60a
    @Test
    fun doesntTally() = card(
        name = "Doesn't Tally",
        cost = "{3}{B}{B}",
        type = "Instant",
        text = "Return target creature card from your graveyard to the battlefield. Suspect it. (It has menace and can't block.)",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield, postBind = "returned"), suspect(Objects.Bound("returned")), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 701.60a: this creature becomes suspected; CR 111.1 the Detective token
    @Test
    fun partyOfConcern() = card(
        name = "Party of Concern",
        cost = "{3}{R}",
        type = "Creature — Human Rogue",
        pt = "2/2",
        text = "When this creature enters, suspect it. Create a 2/2 white and blue Detective creature token. (A suspected creature has menace and can't block.)",
    ) parsesTo abilities(
        triggered(entersSelf, suspect(Objects.Self), create(creatureToken("Detective", listOf(Color.White, Color.Blue), 2, 2))),
    )

    // CR 611.2a, 613.4c: until end of turn; CR 400.7, 603.10a: the granted dies trigger returns the card from the graveyard, a new object, and
    // suspects that permanent (CR 701.60a)
    @Test
    fun presumedDeparted() = card(
        name = "Presumed Departed",
        cost = "{1}{B}",
        type = "Instant",
        text = "Until end of turn, target creature gets +2/+0 and gains \"When this creature dies, return it to the battlefield under its owner's control and suspect it.\" (A suspected creature has menace and can't block.)",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(2, 0), has(triggered(diesSelf,
                doing(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield,
                    controller = Players.OwnerOf(Objects.EventObject(EventRole.After))), postBind = "returned")),
                suspect(Objects.Bound("returned"))))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 608.2b: whether it is suspected is checked as the ability resolves; CR 122.1 the counter, CR 701.60a otherwise it becomes suspected
    @Test
    fun habitualFelon() = card(
        name = "Habitual Felon",
        cost = "{1}{B}",
        type = "Creature — Human Assassin",
        pt = "2/1",
        text = "{2}{B}: If this creature is suspected, put a +1/+1 counter on it. Otherwise, suspect it. (A suspected creature has menace and can't block.)",
    ) parsesTo abilities(
        activated(manaCost("{2}{B}"), ifThen(isSuspected(), counters(Objects.Self), otherwise = listOf(suspect(Objects.Self)))),
    )

    // CR 603.4: "if it's not suspected" is checked as it triggers and resolves; CR 608.2d: you may; CR 701.60a
    @Test
    fun boastfulBrute() = card(
        name = "Boastful Brute",
        cost = "{4}{R}",
        type = "Creature — Lizard Warrior",
        pt = "5/5",
        text = "Whenever this creature attacks, if it's not suspected, you may suspect it. (A suspected creature has menace and can't block.)",
    ) parsesTo abilities(
        triggered(attacksSelf, Step.May(listOf(suspect(Objects.Self)), player = Players.You), interveningIf = Condition.Not(isSuspected())),
    )

    // CR 701.60a: "up to one target creature you control"; CR 602.2b, 118.3: sacrificing a suspected creature is part of the cost
    @Test
    fun brandRuneTrickster() = card(
        name = "Brand-Rune Trickster",
        cost = "{B}{R}",
        type = "Creature — Human Shaman",
        pt = "2/2",
        text = """
            When this creature enters, suspect up to one target creature you control. (A suspected creature has menace and can't block.)
            {3}{B}{R}, Sacrifice a suspected creature: Target creature gets -5/-5 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, suspect(target("creature")), targets = listOf(slot("creature", creatureYouControl, min = 0))),
        activated(cost(mana("{3}{B}{R}"), sacrificeA(and(suspected, creature))), pump(target("creature"), -5, -5),
            targets = listOf(slot("creature", creature))),
    )

    // CR 118.12: counter unless its controller pays; CR 701.60a: the suspected mark, whose menace and "can't block" come with the mark itself
    // (MarkRules, CR 701.60c), so the model is the mark alone (audit: false alarm)
    @Test
    fun lingeringDoubt() = card(
        name = "Lingering Doubt",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Counter target spell unless its controller pays {2}.
            Suspect up to one target creature. (A suspected creature has menace and can't block.)
        """,
    ) parsesTo abilities(
        spell(*counterUnless("{2}").toTypedArray(), suspect(target("creature")),
            targets = listOf(slot("spell", spellFilter), slot("creature", creature, min = 0))),
    )
}
