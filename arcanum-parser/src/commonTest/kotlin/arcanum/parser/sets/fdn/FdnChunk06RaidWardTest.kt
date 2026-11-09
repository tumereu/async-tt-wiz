package arcanum.parser.sets.fdn

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
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
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
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN raid and ward cards, and the counter-count static (chunk 06). The invented names carry the oracle text of the real cards. */
class FdnChunk06RaidWardTest {
    /** CR 207.2c, 603.4: raid asks whether you attacked with a creature this turn. */
    private val raid = Condition.HappenedThisTurn(EventPattern.Attack(you), atLeast = 1, measure = Measure.Events)
    private val yourEndStep = stepBegins(TurnStep.End, PlayerFilter.You)

    // CR 603.4 (checked when it triggers and resolves), CR 608.2h (her power when it resolves), CR 115.1 (the card is targeted)
    @Test
    fun vorna() = card(
        name = "Vorna, Who Mocks Doom",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — Human Warrior",
        pt = "2/2",
        text = """
            First strike
            Whenever Vorna attacks, put a +1/+1 counter on it.
            Raid — At the beginning of your end step, if you attacked this turn, return target creature card with mana value less than or equal to Vorna's power from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(attacksSelf, counters(Objects.Self)),
        triggered(
            yourEndStep,
            move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature, ObjectFilter.ManaValue(Comparison.AtMost, power(Objects.Self))))),
            interveningIf = raid,
            abilityWord = "Raid",
        ),
    )

    // CR 614.1c (an "enters with" replacement), CR 122.1
    @Test
    fun gangplankGoblin() = card(
        name = "Gangplank Goblin",
        cost = "{2}{R}",
        type = "Creature — Goblin Pirate",
        pt = "3/2",
        text = "Raid — This creature enters with a +1/+1 counter on it if you attacked this turn.",
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(1))),
            )),
            affects = Affected.Objects(Objects.Self),
            condition = raid,
            abilityWord = "Raid",
        ),
    )

    // CR 603.4
    @Test
    fun gorehideMarauder() = card(
        name = "Gorehide Marauder",
        cost = "{4}{R}",
        type = "Creature — Minotaur Pirate",
        pt = "4/4",
        text = "Raid — When this creature enters, if you attacked this turn, this creature deals 2 damage to any target.",
    ) parsesTo abilities(
        triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget), interveningIf = raid, abilityWord = "Raid"),
    )

    // CR 603.4, CR 401.5 (the card left on top stays there)
    @Test
    fun ribcagePlunderer() = card(
        name = "Ribcage Plunderer",
        cost = "{2}{B}",
        type = "Creature — Skeleton Pirate",
        pt = "2/2",
        text = """
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            Raid — When this creature enters, if you attacked this turn, look at the top three cards of your library. You may put one of those cards back on top of your library. Put the rest into your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("looked")), bind = "kept", chooser = Players.You),
            move(Objects.Except(Objects.Bound("looked"), Objects.Bound("kept")), Zone.Graveyard),
            interveningIf = raid,
            abilityWord = "Raid",
        ),
    )

    // CR 118.12 ("unless": the player may pay either cost), CR 701.9a (a discard is from the hand), CR 701.21a
    @Test
    fun puncturingVirtuoso() = card(
        name = "Puncturing Virtuoso",
        cost = "{1}{B}{R}",
        type = "Creature — Devil",
        pt = "3/2",
        text = """
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            Raid — At the beginning of your end step, if you attacked this turn, each opponent loses 3 life unless that player sacrifices a nonland permanent of their choice or discards a card.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            yourEndStep,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Pay(
                    Cost(listOf(CostPart.OneOf(listOf(
                        cost(CostPart.Sacrifice(CostObjects.Choose(and(nonland, permanentFilter), count = n(1)))),
                        cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))),
                    )))),
                    payer = Players.Bound("opponent"),
                    bind = "paid",
                ),
                Step.If(Condition.Not(Condition.Did("paid")), listOf(loseLife(3, Players.Bound("opponent")))),
            ), together = true),
            interveningIf = raid,
            abilityWord = "Raid",
        ),
    )

    // CR 603.4, CR 111.4
    @Test
    fun scorchcutterGoblin() = card(
        name = "Scorchcutter Goblin",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        pt = "2/1",
        text = "Raid — At the beginning of your end step, if you attacked this turn, create a 1/1 red Goblin creature token.",
    ) parsesTo abilities(
        triggered(yourEndStep, create(creatureToken("Goblin", listOf(Color.Red), 1, 1)), interveningIf = raid, abilityWord = "Raid"),
    )

    // CR 603.4
    @Test
    fun cloudhullCorsair() = card(
        name = "Cloudhull Corsair",
        cost = "{3}{U}{U}",
        type = "Creature — Human Pirate",
        pt = "4/3",
        text = """
            Flying
            Raid — When this creature enters, if you attacked this turn, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, draw(1), interveningIf = raid, abilityWord = "Raid"),
    )

    // CR 603.4
    @Test
    fun galefleetInformant() = card(
        name = "Galefleet Informant",
        cost = "{2}{U}",
        type = "Creature — Human Pirate",
        pt = "2/2",
        text = "Raid — When this creature enters, if you attacked this turn, draw a card.",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), interveningIf = raid, abilityWord = "Raid"),
    )

    // CR 603.4, CR 305.1 / 601.2 (play: a land or a spell), CR 611.2a
    @Test
    fun lockboxReaver() = card(
        name = "Lockbox Reaver",
        cost = "{2}{R}{R}",
        type = "Creature — Orc Pirate",
        pt = "5/2",
        text = "Raid — When this creature enters, if you attacked this turn, exile the top two cards of your library. Choose one of them. Until the end of your next turn, you may play that card.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("chosen")), Duration.UntilEndOfYourNextTurn)),
            interveningIf = raid,
            abilityWord = "Raid",
        ),
    )

    // CR 702.21a (ward with two costs, both paid), CR 107.3c ("where X is"), CR 112.7a (the spell's mana value as it last existed)
    @Test
    fun zeruvo() = card(
        name = "Zeruvo, Riddle Colossus",
        cost = "{5}{U}{R}",
        type = "Legendary Creature — Phyrexian Nightmare",
        pt = "6/6",
        text = """
            Flying
            Ward—{3}, Pay 3 life.
            Whenever you cast a noncreature spell, create X 1/1 red Phyrexian Goblin creature tokens, where X is the mana value of that spell. They gain haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(cost(mana("{3}"), CostPart.PayLife(n(3)))),
        triggered(
            youCast(noncreatureSpell),
            create(
                creatureToken("Phyrexian Goblin", listOf(Color.Red), 1, 1),
                count = Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single),
                bind = "tokens",
            ),
            untilEndOfTurn(Objects.Bound("tokens"), has(Keyword.Haste)),
        ),
    )

    // CR 101.2 (can't be countered), CR 702.21a, CR 111.4 (the token's name)
    @Test
    fun vorga() = card(
        name = "Vorga, Deep Devourer",
        cost = "{3}{G}{G}{U}{U}",
        type = "Legendary Creature — Serpent",
        pt = "8/12",
        text = """
            This spell can't be countered.
            Trample, ward {4}
            Whenever Vorga deals combat damage to a player, create four 3/3 blue Serpent creature tokens named Vorga's Coil.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Trample,
        Keyword.Ward(manaCost("{4}")),
        triggered(combatDamageToAPlayer, create(creatureToken("Serpent", listOf(Color.Blue), 3, 3, name = "Vorga's Coil"), count = n(4), bind = "created")),
    )

    // CR 702.21a (ward—pay life), CR 702.2, 702.7, 702.20, 702.110, 702.19, 702.15
    @Test
    fun progenitorOfSevenEnds() = card(
        name = "Progenitor of Seven Ends",
        cost = "{7}",
        type = "Creature — Eldrazi",
        pt = "7/7",
        text = """
            Reach, first strike
            Vigilance, menace
            Trample, lifelink
            Ward—Pay 7 life.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.FirstStrike,
        Keyword.Vigilance,
        Keyword.Menace,
        Keyword.Trample,
        Keyword.Lifelink,
        Keyword.Ward(cost(CostPart.PayLife(n(7)))),
    )

    // CR 601.2f (cost reduction), CR 702.21a
    @Test
    fun archiveLeviathan() = card(
        name = "Archive Leviathan",
        cost = "{6}{U}",
        type = "Creature — Serpent",
        pt = "5/5",
        text = """
            This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(or(instant, sorcery)))))),
        Keyword.Ward(manaCost("{2}")),
    )

    // CR 702.21a, CR 601.2a (cast from the graveyard this turn)
    @Test
    fun kezmarAshgrave() = card(
        name = "Kezmar Ashgrave, Crypt Lord",
        cost = "{1}{B}",
        type = "Legendary Creature — Zombie Warlock",
        pt = "2/2",
        text = """
            Ward—Pay 2 life. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays 2 life.)
            {T}: You may cast target Zombie creature card from your graveyard this turn.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost(CostPart.PayLife(n(2)))),
        activated(
            cost(tap),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)),
                Affected.Objects(target("card")), Duration.EndOfTurn)),
            targets = listOf(slot("card", inYourGraveyard(sub("Zombie"), creature))),
        ),
    )

    // CR 613.1d, 613.1f (the type and the ability as long as the counters are there), CR 205.1b ("in addition to its other types")
    @Test
    fun cloudriderPage() = card(
        name = "Cloudrider Page",
        cost = "{1}{W}",
        type = "Creature — Cat Scout",
        pt = "1/1",
        text = """
            Whenever another creature you control enters, put a +1/+1 counter on this creature.
            As long as this creature has three or more +1/+1 counters on it, it has flying and is a Knight in addition to its other types.
        """,
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you)), counters(Objects.Self)),
        static(
            has(Keyword.Flying),
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Knight")))),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 3)),
        ),
    )
}
