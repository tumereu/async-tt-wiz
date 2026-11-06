package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 06: black cards, second part: a power counting cards in all graveyards (CR 604.3) with each player's upkeep
 * discard, hellbent, a return with a -1/-1 counter (CR 122.6), suspend on a tutor, converge in an enters trigger, split second (CR 702.61),
 * escaping with a chosen counter (CR 702.138c), discards at random, devotion (CR 700.5), a nonlegendary card put into the graveyard,
 * an Aura with landcycling and a reflexive trigger after exiling two cards (CR 603.12).
 */
class Mh2Chunk06BlackTest {
    // CR 604.3: a characteristic-defining power; "that player" is the player whose upkeep it is
    @Test
    fun mortgoyf() = card(
        name = "Mortgoyf",
        cost = "{3}{B}{B}",
        type = "Creature — Lhurgoyf",
        text = """
            Mortgoyf's power is equal to the number of creature cards in all graveyards.
            At the beginning of each player's upkeep, that player discards a card.
            Madness {1}{B}{B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "*/4",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(creature, inZone(Zone.Graveyard))), null), characteristicDefining = true),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), discard(1, Players.Active)),
        Keyword.Madness(manaCost("{1}{B}{B}")),
    )

    @Test
    fun gravecallersFamiliar() = card(
        name = "Gravecaller's Familiar",
        cost = "{3}{B}",
        type = "Creature — Bird Spirit",
        text = """
            Flying
            Hellbent — This creature has lifelink as long as you have no cards in hand.
            {B}, Discard a card: This creature gains indestructible until end of turn. Tap it.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.Lifelink), condition = hellbent).copy(abilityWord = "Hellbent"),
        activated(cost(mana("{B}"), discardACard), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
    )

    // CR 603.10a: its power as it last existed on the battlefield
    @Test
    fun huddledShambler() = card(
        name = "Huddled Shambler",
        cost = "{B}",
        type = "Creature — Zombie",
        text = "When this creature dies, create X tapped 1/1 green Squirrel creature tokens, where X is this creature's power.",
        pt = "1/1",
    ) parsesTo abilities(triggered(diesSelf, create(squirrel, count = powerOf(Objects.Self), tapped = true)))

    // CR 122.6: it enters with the counter
    @Test
    fun endureBeyond() = card(
        name = "Endure Beyond",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Return target nonlegendary creature card from your graveyard to the battlefield with a -1/-1 counter on it.",
    ) parsesTo abilities(
        spell(doing(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.PowerToughness(-1, -1), n(1)))))),
            targets = listOf(slot("card", inYourGraveyard(not(ObjectFilter.Super(Supertype.Legendary)), creature)))),
    )

    @Test
    fun unholyTutor() = card(
        name = "Unholy Tutor",
        cost = null,
        type = "Sorcery",
        text = """
            Suspend 2—{1}{B} (Rather than cast this card from your hand, pay {1}{B} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)
            Search your library for a card, put that card into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(2, manaCost("{1}{B}")),
        spell(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    // CR 601.2h: the colours of mana spent to cast it
    @Test
    fun prismaticGourmand() = card(
        name = "Prismatic Gourmand",
        cost = "{4}{B}",
        type = "Creature — Vampire Wizard",
        text = "Converge — When this creature enters, each opponent loses X life and you gain X life, where X is the number of colors of mana spent to cast this spell.",
        pt = "5/5",
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.LoseLife(eachOpponentPlayers, Value.ManaSpent(distinctColors = true, of = Objects.Self))),
            doing(Effect.GainLife(Players.You, Value.ManaSpent(distinctColors = true, of = Objects.Self))),
            abilityWord = "Converge"),
    )

    @Test
    fun sinisterSeaStar() = card(
        name = "Sinister Sea Star",
        cost = "{1}{B}",
        type = "Creature — Starfish",
        text = "{T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
        pt = "0/3",
    ) parsesTo abilities(activated(cost(tap), surveil(1)))

    // CR 702.61a split second
    @Test
    fun abruptDecree() = card(
        name = "Abrupt Decree",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Split second (As long as this spell is on the stack, players can't cast spells or activate abilities that aren't mana abilities.)
            Target player sacrifices a creature of their choice.
        """,
    ) parsesTo abilities(
        Keyword.SplitSecond,
        spell(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetPlayer)),
    )

    // CR 702.138c: "escapes with" applies only if it escaped; CR 122.1b: a flying counter
    @Test
    fun tizerusCourser() = card(
        name = "Tizerus Courser",
        cost = "{2}{B}",
        type = "Creature — Pegasus",
        text = """
            Escape—{4}{B}, Exile five other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)
            This creature escapes with your choice of a +1/+1 counter or a flying counter on it.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Escape(Cost(listOf(mana("{4}{B}"), CostPart.Exile(CostObjects.Choose(and(another, cardFilter), count = n(5)), from = Zone.Graveyard)))),
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Option(listOf("a +1/+1 counter", "a flying counter")), bind = "counter", chooser = Players.You),
            Step.If(Condition.Chose("counter", "a +1/+1 counter"), then = listOf(Step.Modify(Modification.EntersWithCounters(p1p1, n(1)))),
                otherwise = listOf(Step.Modify(Modification.EntersWithCounters(CounterKind.Keyword(Keyword.Flying), n(1))))),
        )))), condition = Condition.Paid(Keyword.Escape.Kind, times = 1, of = Objects.Self)),
    )

    // CR 701.9b: discarding at random
    @Test
    fun vourachDreadChanter() = card(
        name = "Vourach, Dread Chanter",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Cleric",
        text = """
            Kicker {B}{B} (You may pay an additional {B}{B} as you cast this spell.)
            Protection from white
            Whenever an opponent discards a card, put a +1/+1 counter on Vourach.
            When Vourach enters, if it was kicked, target opponent discards two cards at random.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{B}{B}"))),
        Keyword.Protection(ObjectFilter.OfColor(Color.White)),
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.Opponent)), counters(Objects.Self)),
        triggered(entersSelf, doing(Effect.Discard(Players.Target("opponent"), n(2), ObjectFilter.Any, random = true)),
            targets = listOf(targetOpponent), interveningIf = kicked),
    )

    @Test
    fun vourachsHymnal() = card(
        name = "Vourach's Hymnal",
        cost = "{3}{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You choose a card from it. That player discards that card, then discards a card at random.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, random = true)),
            targets = listOf(targetOpponent)),
    )

    // CR 608.2c: "instead if" picks one of the two
    @Test
    fun woefulPlunge() = card(
        name = "Woeful Plunge",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Target creature gets -3/-3 until end of turn.
            Hellbent — That creature gets -13/-13 until end of turn instead if you have no cards in hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creature)),
            steps = listOf(Step.If(hellbent, then = listOf(pump(target("creature"), -13, -13)), otherwise = listOf(pump(target("creature"), -3, -3)))),
        ), abilityWord = "Hellbent"),
    )

    // CR 700.5: devotion to black
    @Test
    fun netherworldRecluse() = card(
        name = "Netherworld Recluse",
        cost = "{4}{B}{B}",
        type = "Creature — Human Peasant",
        text = "When this creature enters, create a number of 1/1 green Squirrel creature tokens equal to your devotion to black. (Each {B} in the mana costs of permanents you control counts toward your devotion to black.)",
        pt = "3/3",
    ) parsesTo abilities(triggered(entersSelf, create(squirrel, count = Value.Devotion(listOf(Color.Black), Players.You))))

    @Test
    fun namelessGrave() = card(
        name = "Nameless Grave",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Search your library for a nonlegendary card, put that card into your graveyard, then shuffle.",
    ) parsesTo abilities(
        spell(search(not(ObjectFilter.Super(Supertype.Legendary))), move(Objects.Bound("found"), Zone.Graveyard), shuffle),
    )

    @Test
    fun verminGlutton() = card(
        name = "Vermin Glutton",
        cost = "{1}{B}",
        type = "Creature — Vampire",
        text = "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life.",
        pt = "2/2",
    ) parsesTo abilities(activated(cost(tap, sacrificeA(and(another, creature))), loseLife(2, eachOpponentPlayers), gainLife(2)))

    @Test
    fun foulInterrer() = card(
        name = "Foul Interrer",
        cost = "{2}{B}{B}",
        type = "Creature — Zombie Warlock",
        text = """
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            When this creature enters, search your library for a card, put that card into your graveyard, then shuffle.
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Deathtouch, triggered(entersSelf, search(cardFilter), move(Objects.Bound("found"), Zone.Graveyard), shuffle))

    @Test
    fun wearyOfWorlds() = card(
        name = "Weary of Worlds",
        cost = "{3}{B}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -4/-4.
            Basic landcycling {1}{B} ({1}{B}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(-4, -4), affects = equipped),
        basicLandcycling("{1}{B}"),
    )

    // CR 603.12: "When you do" is a reflexive trigger, its target chosen then
    @Test
    fun fledglingNecromancer() = card(
        name = "Fledgling Necromancer",
        cost = "{4}{B}",
        type = "Creature — Human Warlock",
        text = "When this creature enters, you may exile two cards from your graveyard. When you do, return target creature card from your graveyard to the battlefield.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Choose(cardFilter, count = n(2)), from = Zone.Graveyard))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))))),
        ),
    )
}
