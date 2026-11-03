package arcanum.parser.sets.ecl

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 04: creatures that enter with -1/-1 counters (CR 614.1c) and remove counters: "Remove a counter from this
 * creature" as a cost (any kind, CR 122.8), "remove a -1/-1 counter from it" when it becomes tapped, when another creature enters or
 * when cards go to your graveyard "while it has a -1/-1 counter on it" (checked as it would trigger, CR 603.2), an intervening "if"
 * (CR 603.4), a reflexive trigger after removing a counter (CR 603.12), a delayed trigger for the rest of the turn (CR 603.7c), stun
 * counters (CR 122.1d), and removing all counters from a creature.
 */
class EclChunk04CountersTest {
    private val m1m1Counter = ObjectFilter.HasCounter(m1m1, atLeast = 1)
    private val removeOne: Step = Step.Do(Effect.RemoveCounters(Objects.Self, m1m1, n(1)))

    @Test
    fun ladenShalehide() = card(
        name = "Laden Shalehide",
        cost = "{1}{W}",
        type = "Creature — Giant Warrior",
        text = """
            This creature enters with two -1/-1 counters on it.
            {1}{W}, Remove a counter from this creature: Target creature gains indestructible until end of turn. Activate only as a sorcery. (Damage and effects that say "destroy" don't destroy it. If its toughness is 0 or less, it still dies.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        entersWithM1M1(2),
        activated(cost(mana("{1}{W}"), removeCounters(1)), gains(target("creature"), Keyword.Indestructible),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 603.2: "while it has a -1/-1 counter on it" is checked as it becomes tapped
    @Test
    fun weighedDownShoalkeeper() = card(
        name = "Weighed-Down Shoalkeeper",
        cost = "{1}{W}",
        type = "Creature — Merfolk Soldier",
        text = """
            This creature enters with three -1/-1 counters on it.
            Whenever this creature becomes tapped while it has a -1/-1 counter on it, remove a -1/-1 counter from it.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        entersWithM1M1(3),
        triggered(becomesTapped, removeOne, triggersOnlyIf = Condition.ObjectMatches(Objects.Self, m1m1Counter)),
    )

    @Test
    fun moonglowMourner() = card(
        name = "Moonglow Mourner",
        cost = "{2}{W}",
        type = "Creature — Treefolk Cleric",
        text = """
            This creature enters with a -1/-1 counter on it.
            {1}{W}, Remove a counter from this creature: Draw a card. Activate only as a sorcery.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        entersWithM1M1(1),
        activated(cost(mana("{1}{W}"), removeCounters(1)), draw(1), rules = asSorcery),
    )

    @Test
    fun hesitantHillwarden() = card(
        name = "Hesitant Hillwarden",
        cost = "{2}{W}",
        type = "Creature — Kithkin Soldier",
        text = """
            This creature enters with two -1/-1 counters on it.
            Whenever another creature you control enters while this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        entersWithM1M1(2),
        triggered(anotherCreatureYouControlEnters, removeOne, triggersOnlyIf = Condition.ObjectMatches(Objects.Self, m1m1Counter)),
    )

    // CR 603.12: the target is chosen once the counter is removed
    @Test
    fun drowsingStrider() = card(
        name = "Drowsing Strider",
        cost = "{3}{W}{W}",
        type = "Creature — Giant Warrior",
        text = """
            This creature enters with two -1/-1 counters on it.
            At the beginning of your end step, you may remove a counter from this creature. When you do, return target creature card with power 2 or less from your graveyard to the battlefield.
        """,
        pt = "4/7",
    ) parsesTo abilities(
        entersWithM1M1(2),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Pay(cost(removeCounters(1)), payer = Players.You, bind = "removed"),
            Step.If(Condition.Did("removed"), then = listOf(reflexive(move(target("card"), Zone.Battlefield),
                targets = listOf(slot("card", inYourGraveyard(creature, power(Comparison.AtMost, 2)))))))),
    )

    // CR 603.7c: a delayed trigger that triggers each time for the rest of the turn
    @Test
    fun fluttermothPest() = card(
        name = "Fluttermoth Pest",
        cost = "{U}",
        type = "Creature — Faerie Rogue",
        text = """
            Flying
            This creature enters with a -1/-1 counter on it.
            {2}{U}, Remove a counter from this creature: Whenever a creature you control deals combat damage to a player or planeswalker this turn, draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        entersWithM1M1(1),
        activated(cost(mana("{2}{U}"), removeCounters(1)), doing(Effect.CreateDelayedTrigger(
            on(EventPattern.Damage(creatureYouControl, RecipientFilter(objects = ObjectFilter.Type(arcanum.api.CardType.Planeswalker), players = PlayerFilter.Any), combat = true)),
            arcanum.api.Instructions(steps = listOf(draw(1))), duration = Duration.EndOfTurn, repeats = true))),
    )

    // CR 701.6a; the countered spell's controller draws
    @Test
    fun valeSylvaneWarden() = card(
        name = "Vale Sylvane Warden",
        cost = "{2}{U}",
        type = "Creature — Faerie Wizard",
        text = """
            Flash
            Flying
            This creature enters with a -1/-1 counter on it.
            {1}{U}, Remove a counter from this creature: Counter target noncreature spell. Its controller draws a card.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        entersWithM1M1(1),
        activated(cost(mana("{1}{U}"), removeCounters(1)), doing(Effect.Counter(target("spell"))), draw(1, Players.ControllerOf(target("spell"))),
            targets = listOf(slot("spell", and(not(creature), spellFilter)))),
    )

    // CR 122.1d: a stun counter
    @Test
    fun tarnSteed() = card(
        name = "Tarn Steed",
        cost = "{1}{U}",
        type = "Creature — Horse Serpent",
        text = """
            This creature enters with three -1/-1 counters on it.
            {1}{U}, Remove a counter from this creature: Draw a card.
            {2}{U}, Remove two counters from this creature: Tap target creature. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
        pt = "4/5",
    ) parsesTo abilities(
        entersWithM1M1(3),
        activated(cost(mana("{1}{U}"), removeCounters(1)), draw(1)),
        activated(cost(mana("{2}{U}"), removeCounters(2)), *tapAndStun(target("creature")).toTypedArray(), targets = listOf(slot("creature", creature))),
    )

    // CR 603.4: both conditions checked as it triggers and as it resolves
    @Test
    fun groanbarkKeeper() = card(
        name = "Groanbark Keeper",
        cost = "{1}{B}",
        type = "Creature — Elf Warrior",
        text = """
            This creature enters with three -1/-1 counters on it.
            At the beginning of your end step, if there is an Elf card in your graveyard and this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        entersWithM1M1(3),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), removeOne, interveningIf = Condition.And(listOf(
            Condition.Exists(inYourGraveyard(sub("Elf")), atLeast = 1), Condition.ObjectMatches(Objects.Self, m1m1Counter)))),
    )

    @Test
    fun knotbarkOak() = card(
        name = "Knotbark Oak",
        cost = "{2}{B}",
        type = "Creature — Treefolk Warlock",
        text = """
            This creature enters with two -1/-1 counters on it.
            {2}{B}, Remove two counters from this creature: Target creature gets -2/-2 until end of turn. Activate only as a sorcery.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        entersWithM1M1(2),
        activated(cost(mana("{2}{B}"), removeCounters(2)), pump(target("creature"), -2, -2), targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    @Test
    fun keepsakeGranny() = card(
        name = "Keepsake Granny",
        cost = "{2}{B}",
        type = "Creature — Goblin Warlock",
        text = """
            This creature enters with two -1/-1 counters on it.
            Whenever another creature you control dies, surveil 1, then remove a -1/-1 counter from this creature. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        entersWithM1M1(2),
        triggered(on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            doing(Effect.Surveil(Players.You, n(1))), removeOne),
    )

    // CR 603.2c: once for each batch of cards put into your graveyard; tokens aren't cards
    @Test
    fun duskshade() = card(
        name = "Duskshade",
        cost = "{B}",
        type = "Creature — Elemental",
        text = """
            Menace
            This creature enters with six -1/-1 counters on it.
            Whenever one or more permanent cards are put into your graveyard from anywhere while this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Menace,
        entersWithM1M1(6),
        triggered(arcanum.api.Trigger.On(EventPattern.ZoneChange(and(permanentCard, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard), oneOrMore = true),
            removeOne, triggersOnlyIf = Condition.ObjectMatches(Objects.Self, m1m1Counter)),
    )

    // CR 700.2: one or both modes; the opponent chooses the cards they exile
    @Test
    fun flawlessMenace() = card(
        name = "Flawless Menace",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Choose one or both —
            • Target opponent exiles two cards from their hand.
            • Remove all counters from target creature.
        """,
    ) parsesTo abilities(
        arcanum.api.Ability.Spell(arcanum.api.Instructions(modes = Modes(listOf(
            mode(Step.Choose(arcanum.api.Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(2), n(2)),
                bind = "chosen", chooser = Players.Target("opponent")),
                move(Objects.Bound("chosen"), Zone.Exile), targets = listOf(targetOpponent)),
            mode(doing(Effect.RemoveCounters(target("creature"), null, Value.Of(NumericProperty.Counters(null), target("creature"), arcanum.api.Combine.Single))),
                targets = listOf(slot("creature", creature)))),
            min = 1, max = 2, chooser = Players.You))),
    )

    @Test
    fun thornhideBruiser() = card(
        name = "Thornhide Bruiser",
        cost = "{2}{R}",
        type = "Creature — Giant Warrior",
        text = """
            This creature enters with two -1/-1 counters on it.
            {1}{R}, Remove a counter from this creature: Target creature can't block this turn. Activate only as a sorcery.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        entersWithM1M1(2),
        activated(cost(mana("{1}{R}"), removeCounters(1)), untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    @Test
    fun spinebaneBrawler() = card(
        name = "Spinebane Brawler",
        cost = "{1}{G}",
        type = "Creature — Kithkin Soldier",
        text = """
            Trample, ward {2}
            This creature enters with five -1/-1 counters on it.
            Whenever another creature you control enters while this creature has a -1/-1 counter on it, remove a -1/-1 counter from this creature.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Ward(manaCost("{2}")),
        entersWithM1M1(5),
        triggered(anotherCreatureYouControlEnters, removeOne, triggersOnlyIf = Condition.ObjectMatches(Objects.Self, m1m1Counter)),
    )

    @Test
    fun shantySlinger() = card(
        name = "Shanty Slinger",
        cost = "{3}{R/W}{R/W}",
        type = "Creature — Giant Warrior",
        text = """
            This creature enters with two -1/-1 counters on it.
            {R/W}{R/W}, Remove a counter from this creature: Another target creature you control gets +1/+0 and gains flying until end of turn. Activate only as a sorcery.
        """,
        pt = "6/7",
    ) parsesTo abilities(
        entersWithM1M1(2),
        activated(cost(mana("{R/W}{R/W}"), removeCounters(1)), pump(target("creature"), 1, 0, has(Keyword.Flying)),
            targets = listOf(slot("creature", and(another, creature, you))), rules = asSorcery),
    )

    @Test
    fun harvestingBirch() = card(
        name = "Harvesting Birch",
        cost = "{1}{W/B}{W/B}{W/B}",
        type = "Creature — Treefolk Cleric",
        text = """
            Lifelink
            This creature enters with two -1/-1 counters on it.
            {1}{W/B}, Remove two counters from this creature: Return target creature card with mana value 3 or less from your graveyard to the battlefield. Activate only as a sorcery.
        """,
        pt = "3/6",
    ) parsesTo abilities(
        Keyword.Lifelink,
        entersWithM1M1(2),
        activated(cost(mana("{1}{W/B}"), removeCounters(2)), move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)))), rules = asSorcery),
    )
}
