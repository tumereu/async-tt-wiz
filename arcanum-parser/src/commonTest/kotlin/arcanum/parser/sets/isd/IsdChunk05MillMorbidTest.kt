package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 05: mill (CR 701.17) and morbid (CR 207.2c ability word; "a creature died this turn": one went from the battlefield to a
 * graveyard this turn, CR 700.4). A milled card can be found in the zone it went to (CR 701.17c).
 */
class IsdChunk05MillMorbidTest {
    private val morbid = Condition.HappenedThisTurn(
        EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
        atLeast = 1,
        measure = Measure.Events,
    )
    private val milled = Objects.Bound("milled")

    // CR 701.17a: every player mills; CR 605.1a: not a mana ability
    @Test
    fun gravecallersChime() = card(
        name = "Gravecaller's Chime",
        cost = "{1}",
        type = "Artifact",
        text = "{T}: Each player mills a card.",
    ) parsesTo abilities(
        activated(cost(tap), mill(1, players = Players.Each(PlayerFilter.Any))),
    )

    // CR 115.4 (any target), 701.17c (the milled cards are found in the graveyard), 208.1, 202.3: the greatest mana value among them
    @Test
    fun heathensPenance() = card(
        name = "Heathen's Penance",
        cost = "{4}{R}",
        type = "Enchantment",
        text = "{3}{R}: Choose any target, then mill three cards. This enchantment deals damage to that permanent or player equal to the greatest mana value among the milled cards.",
    ) parsesTo abilities(
        activated(
            cost(mana("{3}{R}")),
            mill(3, postBind = "milled"),
            damage(Value.Of(NumericProperty.ManaValue, milled, Combine.Greatest), toAny),
            targets = listOf(anyTarget),
        ),
    )

    // CR 701.17c, d (the milled card's mana value: their total if a replacement milled several), 608.2h (X fixed on resolution), 613.4c
    @Test
    fun mindwailer() = card(
        name = "Mindwailer",
        cost = "{1}{U}",
        type = "Creature — Spirit Bird",
        pt = "1/1",
        text = """
            Flying
            {2}: Target player mills a card. This creature gets +X/+X until end of turn, where X is the milled card's mana value.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(
            cost(mana("{2}")),
            mill(1, players = Players.Target("player"), postBind = "milled"),
            untilEndOfTurn(Objects.Self, Value.Of(NumericProperty.ManaValue, milled, Combine.Total).let { Mechanic.ModifyPowerToughness(it, it) }),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 614.1a (the damage is replaced: never dealt), 510.2; CR 603.2, 400.7 (a card put from an opponent's library into their graveyard;
    // "that card" is the card in the graveyard)
    @Test
    fun unlivingDistiller() = card(
        name = "Unliving Distiller",
        cost = "{3}{U}",
        type = "Creature — Zombie",
        pt = "4/2",
        text = """
            If a Zombie you control would deal combat damage to a player, instead that player mills that many cards.
            Whenever a creature card is put into an opponent's graveyard from their library, exile that card and create a 2/2 black Zombie creature token.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(
            EventPattern.Damage(and(sub("Zombie"), you), RecipientFilter(players = PlayerFilter.Any), combat = true),
            instead = Instructions(steps = listOf(doing(Effect.Mill(Players.EventPlayer, Value.EventAmount)))),
        )),
        triggered(
            on(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.Opponent)), from = Zone.Library, to = Zone.Graveyard)),
            move(Objects.EventObject(EventRole.After), Zone.Exile),
            create(zombie),
        ),
    )

    // CR 605.1a (mana ability), 701.17a
    @Test
    fun drownmarshShoals() = card(
        name = "Drownmarsh Shoals",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}{U}{B}, {T}: Target player mills three cards.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}{U}{B}"), tap), mill(3, players = Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 700.4 (dies), 603.10a (leaves-the-battlefield trigger looks back: itself too)
    @Test
    fun selmontOccultist() = card(
        name = "Selmont Occultist",
        cost = "{2}{U}",
        type = "Creature — Human Rogue",
        pt = "2/3",
        text = "Whenever this creature or another creature dies, target player mills a card.",
    ) parsesTo abilities(
        triggered(
            dies(or(ObjectFilter.Is(Objects.Self), and(another, creature))),
            mill(1, players = Players.Target("player")),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 701.23 (search), 608.2c: with a creature died this turn you choose to put the card onto the battlefield, else it goes to your hand
    @Test
    fun wagonVigil() = card(
        name = "Wagon Vigil",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Morbid — You may put that card onto the battlefield instead of putting it into your hand if a creature died this turn.
        """,
    ) parsesTo abilities(
        spell(
            search(basicLand(), reveal = true),
            Step.If(morbid,
                then = listOf(
                    Step.Pay(steps = listOf(move(Objects.Bound("found"), Zone.Battlefield)), payer = Players.You, bind = "put"),
                    Step.If(Condition.Not(Condition.Did("put")), then = listOf(move(Objects.Bound("found"), Zone.Hand))),
                ),
                otherwise = listOf(move(Objects.Bound("found"), Zone.Hand)),
            ),
            shuffle,
        ).copy(abilityWord = "Morbid"),
    )

    // CR 603.4 (intervening if), 701.20c-style random choice: no player chooses the card
    @Test
    fun thicketSleuth() = card(
        name = "Thicket Sleuth",
        cost = "{3}{G}",
        type = "Creature — Human Scout",
        pt = "2/3",
        text = "Morbid — When this creature enters, if a creature died this turn, return a creature card at random from your graveyard to your hand.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Bind(Objects.Random(Objects.Each(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), n(1)), "chosen"),
            move(Objects.Bound("chosen"), Zone.Hand),
            interveningIf = morbid,
            abilityWord = "Morbid",
        ),
    )

    // CR 115.4, 608.2c: 5 damage instead of 3 if a creature died this turn (checked on resolution)
    @Test
    fun sulfurBarrage() = card(
        name = "Sulfur Barrage",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Sulfur Barrage deals 3 damage to any target.
            Morbid — Sulfur Barrage deals 5 damage instead if a creature died this turn.
        """,
    ) parsesTo abilities(
        spell(
            Step.If(morbid, then = listOf(damage(5, toAny)), otherwise = listOf(damage(3, toAny))),
            targets = listOf(anyTarget),
        ).copy(abilityWord = "Morbid"),
    )

    // CR 603.4 (intervening if), 119.3
    @Test
    fun hollowfenScavenger() = card(
        name = "Hollowfen Scavenger",
        cost = "{3}{G}{G}",
        type = "Creature — Elemental",
        pt = "4/5",
        text = "Morbid — When this creature enters, if a creature died this turn, you gain 5 life.",
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(5), interveningIf = morbid, abilityWord = "Morbid"),
    )

    // CR 513.1 (each end step, every player's), 603.4, 701.8
    @Test
    fun harvesterFromThePit() = card(
        name = "Harvester from the Pit",
        cost = "{3}{B}{B}{B}",
        type = "Creature — Demon",
        pt = "6/6",
        text = """
            Flying
            Morbid — At the beginning of each end step, if a creature died this turn, destroy target non-Demon creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            stepBegins(TurnStep.End, PlayerFilter.Any),
            destroy(target("creature")),
            targets = listOf(slot("creature", and(not(sub("Demon")), creature))),
            interveningIf = morbid,
            abilityWord = "Morbid",
        ),
    )
}
