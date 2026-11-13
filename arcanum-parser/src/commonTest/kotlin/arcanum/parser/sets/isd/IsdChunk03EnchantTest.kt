package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 03: Auras (CR 303.4). The Curses enchant a player (CR 303.4e): "enchanted player" is the player the Aura is attached to,
 * and "Creatures enchanted player controls" are that player's creatures.
 */
class IsdChunk03EnchantTest {
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val enchantedPlayer = Players.AttachedTo(Objects.Self)
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val enchantPlayer = Keyword.Enchant(TargetKind.Players(PlayerFilter.Any))
    private val enchantedPlayersUpkeep = on(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.Is(enchantedPlayer)))
    private val enchantedPlayersCreatures = Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(enchantedPlayer))))

    // CR 611.3a: each static ability applies while its condition holds; "Otherwise" is the opposite condition
    @Test
    fun vowOfKinship() = card(
        name = "Vow of Kinship",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 as long as it's a Human. Otherwise, it can't attack or block.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(2, 2), affects = enchanted, condition = Condition.ObjectMatches(enchanted, sub("Human"))),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = enchanted,
            condition = Condition.Not(Condition.ObjectMatches(enchanted, sub("Human")))),
    )

    // CR 303.4e: an Aura that enchants a player; CR 613.4c: the -1/-1 applies to that player's creatures
    @Test
    fun hexOfWiltingRanks() = card(
        name = "Hex of Wilting Ranks",
        cost = "{3}{B}{B}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            Creatures enchanted player controls get -1/-1.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        static(pt(-1, -1), affects = enchantedPlayersCreatures),
    )

    // CR 503.1a: the upkeep of the enchanted player; CR 609.3: with fewer than two cards there, they exile what they have
    @Test
    fun hexOfForgottenGraves() = card(
        name = "Hex of Forgotten Graves",
        cost = "{3}{B}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            At the beginning of enchanted player's upkeep, that player exiles two cards from their graveyard.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        triggered(enchantedPlayersUpkeep,
            Step.Choose(Choice.Objects(and(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer))), n(2), n(2)),
                bind = "chosen", chooser = Players.EventPlayer),
            move(Objects.Bound("chosen"), Zone.Exile)),
    )

    // CR 510.2: combat damage; "that creature" is the creature that dealt it
    @Test
    fun hexOfTheHunted() = card(
        name = "Hex of the Hunted",
        cost = "{1}{R}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            Whenever a creature deals combat damage to enchanted player, put a +1/+1 counter on that creature.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        triggered(on(EventPattern.Damage(creature, RecipientFilter(players = PlayerFilter.Is(enchantedPlayer)), combat = true)),
            counters(Objects.EventObject(EventRole.Source))),
    )

    // CR 110.2: "that player" is the entering creature's controller (the enchanted player), not its owner
    @Test
    fun hexOfUnwelcomeSteps() = card(
        name = "Hex of Unwelcome Steps",
        cost = "{2}{B}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            Whenever a creature enchanted player controls enters, that player loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        triggered(enters(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(enchantedPlayer)))),
            loseLife(1, Players.ControllerOf(Objects.EventObject(EventRole.After))), gainLife(1)),
    )

    // CR 701.17a: mill
    @Test
    fun hexOfTheWeepingPage() = card(
        name = "Hex of the Weeping Page",
        cost = "{2}{U}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            At the beginning of enchanted player's upkeep, that player mills two cards.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        triggered(enchantedPlayersUpkeep, mill(2, Players.EventPlayer)),
    )

    // CR 508.1d: an attack requirement on that player's creatures
    @Test
    fun hexOfTheMoonlitChase() = card(
        name = "Hex of the Moonlit Chase",
        cost = "{2}{R}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            Creatures enchanted player controls attack each combat if able.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        static(Mechanic.MustAttack(), affects = enchantedPlayersCreatures),
    )

    // CR 608.2d: not targeted; the Aura's controller chooses on resolution between the player and one of their planeswalkers
    @Test
    fun hexOfTheNeedledHeart() = card(
        name = "Hex of the Needled Heart",
        cost = "{1}{R}",
        type = "Enchantment — Aura Curse",
        text = """
            Enchant player
            At the beginning of enchanted player's upkeep, this Aura deals 1 damage to that player or a planeswalker that player controls.
        """,
    ) parsesTo abilities(
        enchantPlayer,
        triggered(enchantedPlayersUpkeep,
            Step.Choose(Choice.Objects(and(ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))), n(0), n(1)),
                bind = "planeswalker", chooser = Players.You),
            ifThen(Condition.NonEmpty("planeswalker"), damage(1, Recipients(objects = Objects.Bound("planeswalker"))),
                otherwise = listOf(damage(1, Recipients(players = Players.EventPlayer))))),
    )

    // CR 615.1: two preventions, of combat damage to and by the enchanted creature
    @Test
    fun phantomEmbrace() = card(
        name = "Phantom Embrace",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has flying.
            Prevent all combat damage that would be dealt to and dealt by enchanted creature.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(has(Keyword.Flying), affects = enchanted),
        static(
            Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(enchanted)), combat = true)),
            Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Is(enchanted), RecipientFilter(objects = ObjectFilter.Any, players = PlayerFilter.Any), combat = true)),
        ),
    )

    // CR 208.1, 613.4c: X counted continuously, the creature cards in the Aura controller's graveyard
    @Test
    fun garlandOfShades() = card(
        name = "Garland of Shades",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +X/+X, where X is the number of creature cards in your graveyard.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(Mechanic.ModifyPowerToughness(count(and(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
            count(and(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), affects = enchanted),
    )

    // CR 502.3: it doesn't untap in its controller's untap step
    @Test
    fun tighteningWalls() = card(
        name = "Tightening Walls",
        cost = "{1}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(entersSelf, tapIt(enchanted)),
        static(Mechanic.DoesntUntap, affects = enchanted),
    )

    // CR 120.3: any damage to an opponent, combat or not
    @Test
    fun inquisitiveSpark() = card(
        name = "Inquisitive Spark",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Whenever enchanted creature deals damage to an opponent, you may draw a card.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(on(EventPattern.Damage(ObjectFilter.Is(enchanted), RecipientFilter(players = PlayerFilter.Opponent))),
            Step.May(listOf(draw(1)), player = Players.You)),
    )

    // CR 704.5f: a creature with toughness 0 or less is put into its owner's graveyard
    @Test
    fun leadenBurden() = card(
        name = "Leaden Burden",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -2/-2.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(-2, -2), affects = enchanted),
    )

    // CR 508.1d
    @Test
    fun rageOfTheFanged() = card(
        name = "Rage of the Fanged",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and attacks each combat if able.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(2, 2), Mechanic.MustAttack(), affects = enchanted),
    )

    // CR 702.13b: intimidate
    @Test
    fun twistedVisage() = card(
        name = "Twisted Visage",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has intimidate. (It can't be blocked except by artifact creatures and/or creatures that share a color with it.)
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(has(Keyword.Intimidate), affects = enchanted),
    )

    // CR 613.4c
    @Test
    fun dulledSenses() = card(
        name = "Dulled Senses",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -3/-0.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(-3, 0), affects = enchanted),
    )

    // CR 701.19a: regenerate; the granted ability is the creature's own ("this creature")
    @Test
    fun bonyLeer() = card(
        name = "Bony Leer",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 and has "{B}: Regenerate this creature."
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(1, 1), has(activated(cost(mana("{B}")), Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Self))))), affects = enchanted),
    )

    // CR 702.9a: flying
    @Test
    fun wraithwingGift() = card(
        name = "Wraithwing Gift",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has flying.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(2, 2), has(Keyword.Flying), affects = enchanted),
    )
}
