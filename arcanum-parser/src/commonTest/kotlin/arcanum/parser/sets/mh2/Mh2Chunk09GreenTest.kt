package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 09: green cards, first part: a chosen kind for revealing until (CR 701.20), a token copy that isn't legendary
 * (CR 205.4) with counters for other Oozes, reinforce with scavenge (CR 702.77, 702.97), a shuffled graveyard with a fog (CR 615), token
 * doubling with Squirrels (CR 614.1a), storm, acorn counters, an activation cost reduced per +1/+1 counter, trample for creatures with
 * counters, an end-step condition on two spells cast, entwine, evoke, morbid, playing from the graveyard with a graveyard exile
 * replacement for the turn, converge counters and a counters-put trigger making tokens.
 */
class Mh2Chunk09GreenTest {
    // CR 701.20a: revealed one by one until a card of the chosen kind
    @Test
    fun bountifulReaping() = card(
        name = "Bountiful Reaping",
        cost = "{G}",
        type = "Sorcery",
        text = "Choose land or nonland. Reveal cards from the top of your library until you reveal a card of the chosen kind. Put that card into your hand and the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("land", "nonland")), bind = "kind", chooser = Players.You),
            Step.If(Condition.Chose("kind", "land"),
                then = listOf(doing(Effect.RevealUntil(land, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest"))),
                otherwise = listOf(doing(Effect.RevealUntil(nonland, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")))),
            move(Objects.Bound("found"), Zone.Hand),
            doing(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 205.4: a token copy isn't legendary; CR 614.1c: counters for each other Ooze as it enters
    @Test
    fun avieprimordialOoze() = card(
        name = "Avie, Primordial Ooze",
        cost = "{2}{G}{G}{G}",
        type = "Legendary Creature — Ooze",
        text = """
            Storm (When you cast this spell, copy it for each spell cast before it this turn. Copies become tokens.)
            Avie isn't legendary if it's a token.
            Avie enters with a +1/+1 counter on it for each other Ooze you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Storm,
        static(Mechanic.SetTypes(removeSupertypes = listOf(Supertype.Legendary)), condition = Condition.ObjectMatches(Objects.Self, token)),
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(p1p1, count(and(another, sub("Ooze"), you)))))),
    )

    // CR 702.77a reinforce; CR 702.97a scavenge
    @Test
    fun flaghideKrushok() = card(
        name = "Flaghide Krushok",
        cost = "{3}{G}",
        type = "Creature — Beast",
        text = """
            Trample
            Reinforce 2—{1}{G} ({1}{G}, Discard this card: Put two +1/+1 counters on target creature.)
            Scavenge {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Trample, Keyword.Reinforce(2, manaCost("{1}{G}")), Keyword.Scavenge(manaCost("{5}{G}{G}")))

    // CR 701.24a: its graveyard shuffled into its library; CR 615.1: all combat damage prevented this turn
    @Test
    fun hallowedReprieve() = card(
        name = "Hallowed Reprieve",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target player shuffles their graveyard into their library. Prevent all combat damage that would be dealt this turn.",
    ) parsesTo abilities(
        spell(
            move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Library),
            doing(Effect.Shuffle(Players.Target("player"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(), combat = true))), null, Duration.EndOfTurn)),
            targets = listOf(targetPlayer)),
    )

    // CR 614.1a: one creation event, with as many Squirrels added
    @Test
    fun chitterjawSquirrelCaptain() = card(
        name = "Chitterjaw, Squirrel Captain",
        cost = "{2}{G}",
        type = "Legendary Creature — Squirrel Warrior",
        text = """
            Forestwalk (This creature can't be blocked as long as defending player controls a Forest.)
            If one or more tokens would be created under your control, those tokens plus that many 1/1 green Squirrel creature tokens are created instead.
            {B}, Sacrifice X Squirrels: Target creature gets +X/-X until end of turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Landwalk(sub("Forest"), "Forestwalk"),
        static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.AlsoCreate(squirrel, count = Value.EventAmount)))),
        Ability.Activated(
            cost(mana("{B}"), CostPart.Sacrifice(CostObjects.Choose(sub("Squirrel"), count = x))),
            Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(x, Value.Times(x, -1)))), announced = listOf("X")),
            anyTime,
        ),
    )

    // CR 702.40a storm
    @Test
    fun squirrelstorm() = card(
        name = "Squirrelstorm",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Create a 1/1 green Squirrel creature token.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(spell(create(squirrel)), Keyword.Storm)

    // CR 122.1: acorn counters have no rules meaning of their own
    @Test
    fun acornspitter() = card(
        name = "Acornspitter",
        cost = "{2}{G}",
        type = "Artifact",
        text = """
            At the beginning of your upkeep, you may sacrifice a token. If you do, put an acorn counter on this artifact.
            Squirrels you control get +1/+1 for each acorn counter on this artifact.
            {G}, {T}: Create a 1/1 green Squirrel creature token.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep,
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(token, count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self, 1, CounterKind.Named("acorn"))))),
        static(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Counters(CounterKind.Named("acorn")), Objects.Self, Combine.Single),
            Value.Of(NumericProperty.Counters(CounterKind.Named("acorn")), Objects.Self, Combine.Single)), affects = Objects.Each(and(sub("Squirrel"), you))),
        activated(cost(mana("{G}"), tap), create(squirrel)),
    )

    @Test
    fun pryOpen() = card(
        name = "Pry Open",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Destroy target artifact or enchantment. Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(spell(destroy(target("permanent")), createTreasure, targets = listOf(slot("permanent", or(artifact, enchantment)))))

    // CR 601.2f, 602.2b: {1} less for each +1/+1 counter on creatures you control
    @Test
    fun deepwoodDweller() = card(
        name = "Deepwood Dweller",
        cost = "{2}{G}",
        type = "Creature — Elf Warrior",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            {5}{G}, {T}: Draw a card. This ability costs {1} less to activate for each +1/+1 counter on creatures you control.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(mana("{5}{G}"), tap), draw(1), rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Of(NumericProperty.Counters(p1p1), Objects.Each(creatureYouControl), Combine.Total)))),
    )

    @Test
    fun duskshellCreeper() = card(
        name = "Duskshell Creeper",
        cost = "{1}{G}",
        type = "Creature — Insect",
        text = """
            When this creature enters, put a +1/+1 counter on target creature.
            Each creature you control with a +1/+1 counter on it has trample. (It can deal excess combat damage to the player or planeswalker it's attacking.)
        """,
        pt = "0/3",
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creature))),
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
    )

    @Test
    fun doggedness() = card(
        name = "Doggedness",
        cost = "{1}{G}{G}",
        type = "Creature — Elemental Incarnation",
        text = """
            Flash
            Reach
            When this creature enters, up to one target player puts all the cards from their graveyard on the bottom of their library in a random order.
            Evoke—Exile a green card from your hand.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Reach,
        triggered(entersSelf,
            doing(Effect.Move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = n(0), max = n(1)))),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(Color.Green), count = n(1)), from = Zone.Hand)))),
    )

    // CR 603.4: both kinds of spell cast this turn, checked at the end step
    @Test
    fun fairyTribute() = card(
        name = "Fairy Tribute",
        cost = "{2}{G}",
        type = "Enchantment",
        text = "At the beginning of each end step, if you've cast both a creature spell and a noncreature spell this turn, create a Clue token, a Food token, and a Treasure token.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any), create(clue), create(food), createTreasure,
            interveningIf = Condition.And(listOf(
                Condition.HappenedThisTurn(EventPattern.Cast(and(creature, spellFilter), PlayerFilter.You), atLeast = 1, measure = Measure.Events),
                Condition.HappenedThisTurn(EventPattern.Cast(and(noncreature, spellFilter), PlayerFilter.You), atLeast = 1, measure = Measure.Events)))),
    )

    @Test
    fun thrivingBlow() = card(
        name = "Thriving Blow",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Thriving Blow deals 5 damage to target creature with flying.
            • Target creature gets +3/+3 until end of turn.
            Entwine {2}{G} (Choose both if you pay the entwine cost.)
        """,
    ) parsesTo abilities(
        modal(
            mode(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", and(creature, kw(Keyword.Flying))))),
            mode(pump(target("creature"), 3, 3), targets = listOf(slot("creature", creature))),
        ),
        Keyword.Entwine(manaCost("{2}{G}")),
    )

    @Test
    fun bedrockBreaker() = card(
        name = "Bedrock Breaker",
        cost = "{3}{G}",
        type = "Creature — Elemental",
        text = """
            When this creature enters, you may destroy target artifact or enchantment.
            Evoke {1}{G} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(destroy(target("permanent"))), Players.You), targets = listOf(slot("permanent", or(artifact, enchantment)))),
        Keyword.Evoke(manaCost("{1}{G}")),
    )

    // CR 700.4: a creature died this turn
    @Test
    fun funnelwebHermit() = card(
        name = "Funnelweb Hermit",
        cost = "{4}{G}",
        type = "Creature — Spider",
        text = """
            Reach
            Morbid — When this creature enters, if a creature died this turn, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, investigate, abilityWord = "Morbid", interveningIf = Condition.HappenedThisTurn(
            EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)),
    )

    // CR 601.2a, 305.1: permissions from the graveyard; CR 614.1a: exiled instead, for the rest of the turn
    @Test
    fun gaeasWish() = card(
        name = "Gaea's Wish",
        cost = null,
        type = "Sorcery",
        text = """
            Suspend 4—{G}
            Until end of turn, you may play lands and cast spells from your graveyard.
            If a card would be put into your graveyard from anywhere this turn, exile that card instead.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(4, manaCost("{G}")),
        spell(
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You),
            ), Affected.Objects(Objects.Each(ObjectFilter.And(yourGraveyard))), Duration.EndOfTurn)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
        ),
    )

    @Test
    fun shimmerBairn() = card(
        name = "Shimmer Bairn",
        cost = "{G}",
        type = "Creature — Ouphe",
        text = "Sacrifice a token: This creature gets +2/+2 until end of turn.",
        pt = "1/2",
    ) parsesTo abilities(activated(cost(sacrificeA(token)), pump(Objects.Self, 2, 2)))

    // CR 601.2h: two counters for each colour of mana spent to cast it
    @Test
    fun gleamingCreeper() = card(
        name = "Gleaming Creeper",
        cost = "{4}{G}",
        type = "Creature — Plant",
        text = """
            Converge — This creature enters with two +1/+1 counters on it for each color of mana spent to cast it.
            This creature can't be blocked by creatures with power 2 or less.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(p1p1, Value.Times(Value.ManaSpent(distinctColors = true, of = Objects.Self), 2))))).copy(abilityWord = "Converge"),
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
    )

    @Test
    fun droveBaloth() = card(
        name = "Drove Baloth",
        cost = "{3}{G}{G}",
        type = "Creature — Beast",
        text = "Whenever one or more +1/+1 counters are put on this creature, you may create a 4/4 green Beast creature token.",
        pt = "4/4",
    ) parsesTo abilities(triggered(countersPutOnSelf, Step.May(listOf(create(creatureToken("Beast", listOf(Color.Green), 4, 4))), Players.You)))
}
