package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/*
 * INR chunk 01: transforming double-faced cards, part 1 (CR 701.27, 712). The Innistrad werewolves transform at the beginning of each
 * upkeep by what was cast last turn (an intervening "if", CR 603.4); "a player cast two or more spells" is one player's casts.
 */
class InrChunk01TransformTest {
    // CR 606.4: loyalty abilities; CR 611.2a: until end of turn; CR 114.4: the emblem's abilities work from the command zone
    @Test
    fun brennaMoonkin() = cardFaces(Layout.Transform,
        card(
            name = "Brenna Moonkin",
            cost = "{2}{R}{G}",
            type = "Legendary Planeswalker — Arlinn",
            text = """
                [+1]: Until end of turn, up to one target creature gets +2/+2 and gains vigilance and haste.
                [0]: Create a 2/2 green Wolf creature token. Transform Brenna Moonkin.
            """,
            loyalty = "3",
        ) to abilities(
            loyalty(1, pump(target("creature"), 2, 2, has(Keyword.Vigilance, Keyword.Haste)), targets = listOf(slot("creature", creature, min = 0))),
            loyalty(0, create(wolf), transformSelf),
        ),
        card(
            name = "Brenna, Sworn to the Moon",
            cost = null,
            type = "Legendary Planeswalker — Arlinn",
            text = """
                [+1]: Creatures you control get +1/+1 and gain trample until end of turn.
                [−1]: Brenna deals 3 damage to any target. Transform Brenna.
                [−6]: You get an emblem with "Creatures you control have haste and '{T}: This creature deals damage equal to its power to any target.'"
            """,
            colorIndicator = "RG",
        ) to abilities(
            loyalty(1, pump(Objects.Each(and(creature, you)), 1, 1, has(Keyword.Trample))),
            loyalty(-1, damage(3, toAny), transformSelf, targets = listOf(anyTarget)),
            loyalty(-6, doing(Effect.CreateEmblem(listOf(static(
                has(Keyword.Haste, activated(cost(tap), damage(power(Objects.Self), toAny), targets = listOf(anyTarget))),
                affects = Objects.Each(and(creature, you)))), Players.You))),
        ),
    )

    // CR 603.7b: the delayed trigger triggers once, at the next main phase of this turn; CR 106.4: the mana is added when it resolves
    @Test
    fun stormroadChanneler() = cardFaces(Layout.Transform,
        card(
            name = "Stormroad Channeler",
            cost = "{2}{R}",
            type = "Creature — Werewolf Horror",
            text = """
                Whenever this creature attacks, add {R} at the beginning of your next main phase this turn.
                {3}{R}{R}: Transform this creature.
            """,
            pt = "2/3",
        ) to abilities(
            triggered(attacksSelf, nextMainPhaseThisTurn("{R}")),
            activated(manaCost("{3}{R}{R}"), transformSelf),
        ),
        card(
            name = "Channeler of the Void",
            cost = null,
            type = "Creature — Eldrazi Werewolf",
            text = "Whenever this creature attacks, add {C}{C} at the beginning of your next main phase this turn.",
            pt = "5/4",
            colorIndicator = "R",
        ) to abilities(
            triggered(attacksSelf, nextMainPhaseThisTurn("{C}{C}")),
        ),
    )

    // CR 603.4: the condition is checked when it triggers and again on resolution; "each player" is every player in the game
    @Test
    fun shardstoneRelic() = cardFaces(Layout.Transform,
        card(
            name = "Shardstone Relic",
            cost = "{3}",
            type = "Artifact",
            text = """
                This artifact enters tapped.
                {T}: Add one mana of any color. Each player loses 1 life.
                At the beginning of your upkeep, if each player has 10 or less life, transform this artifact.
            """,
        ) to abilities(
            entersTapped,
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)), loseLife(1, Players.Each(PlayerFilter.Any))),
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), transformSelf,
                interveningIf = Condition.Not(Condition.PlayerExists(PlayerFilter.Life(Comparison.Greater, n(10)), atLeast = 1))),
        ),
        card(
            name = "Aurora of the Deep",
            cost = null,
            type = "Creature — Eldrazi Reflection",
            text = """
                Flying, deathtouch
                Whenever this creature attacks, each opponent loses 3 life.
            """,
            pt = "1/4",
        ) to abilities(
            Keyword.Flying,
            Keyword.Deathtouch,
            triggered(attacksSelf, loseLife(3, Players.Each(PlayerFilter.Opponent))),
        ),
    )

    // CR 701.20e: looking shows the card only to you; CR 701.20a: revealing shows it to all players
    @Test
    fun tomesifterAdept() = cardFaces(Layout.Transform,
        card(
            name = "Tomesifter Adept",
            cost = "{U}",
            type = "Creature — Human Wizard",
            text = "At the beginning of your upkeep, look at the top card of your library. You may reveal that card. If an instant or sorcery card is revealed this way, transform this creature.",
            pt = "1/1",
        ) to abilities(
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You)),
                Step.May(listOf(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed"))), Players.You),
                Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), or(instant, sorcery)), then = listOf(transformSelf))),
        ),
        card(
            name = "Winged Tomesifter",
            cost = null,
            type = "Creature — Human Insect",
            text = "Flying",
            pt = "3/2",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Flying,
        ),
    )

    // CR 603.4; CR 601.2i: a spell is cast when it becomes cast, whoever's turn it is
    @Test
    fun gloamwatchScout() = cardFaces(Layout.Transform,
        card(
            name = "Gloamwatch Scout",
            cost = "{1}{G}",
            type = "Creature — Human Warrior Werewolf",
            text = """
                {2}{G}: Look at the top three cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in any order.
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            activated(manaCost("{2}{G}"),
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                doing(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Library, position = -1)),
            werewolfFront,
        ),
        card(
            name = "Gloampack Howler",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Creature spells you cast cost {1} less to cast.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "3/3",
            colorIndicator = "G",
        ) to abilities(
            static(Mechanic.CostChange(and(creature, spellFilter, you), arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
            werewolfBack,
        ),
    )

    // CR 603.8: a state trigger triggers once when the state is true, and again only after its ability has left the Stack
    @Test
    fun tovarUnchained() = cardFaces(Layout.Transform,
        card(
            name = "Tovar Unchained",
            cost = "{3}{G}",
            type = "Legendary Planeswalker — Garruk",
            text = """
                When Tovar has two or fewer loyalty counters on him, transform him.
                [0]: Tovar deals 3 damage to target creature. That creature deals damage equal to its power to him.
                [0]: Create a 2/2 green Wolf creature token.
            """,
            loyalty = "3",
        ) to abilities(
            Ability.Triggered(Trigger.State(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Loyalty, atLeast = 3)))),
                Instructions(steps = listOf(transformSelf))),
            // CR 120.3: the creature's damage to a planeswalker removes loyalty counters
            loyalty(0, damage(3, toObjects(target("creature"))), damage(power(target("creature")), toObjects(Objects.Self), source = target("creature")),
                targets = listOf(slot("creature", creature))),
            loyalty(0, create(wolf)),
        ),
        card(
            name = "Tovar, the Grave-Bound",
            cost = null,
            type = "Legendary Planeswalker — Garruk",
            text = """
                [+1]: Create a 1/1 black Wolf creature token with deathtouch.
                [−1]: Sacrifice a creature. If you do, search your library for a creature card, reveal it, put it into your hand, then shuffle.
                [−3]: Creatures you control gain trample and get +X/+X until end of turn, where X is the number of creature cards in your graveyard.
            """,
            colorIndicator = "BG",
        ) to abilities(
            loyalty(1, create(creatureToken("Wolf", listOf(Color.Black), 1, 1, abilities = listOf(Keyword.Deathtouch)))),
            // CR 701.21a: "If you do" holds only when a creature was sacrificed
            loyalty(-1,
                Step.Choose(Choice.Objects(and(creature, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"), postBind = "sacrificed")),
                Step.If(Condition.Compare(Value.Count(Objects.Bound("sacrificed")), Comparison.AtLeast, n(1)), then = listOf(
                    search(creature, reveal = true),
                    move(Objects.Bound("found"), Zone.Hand),
                    shuffle))),
            loyalty(-3, untilEndOfTurn(Objects.Each(and(creature, you)), Mechanic.GainAbilities(listOf(Keyword.Trample)),
                Mechanic.ModifyPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature))))),
        ),
    )

    @Test
    fun ridgepassBrigand() = cardFaces(Layout.Transform,
        card(
            name = "Ridgepass Brigand",
            cost = "{2}{R}",
            type = "Creature — Human Rogue Werewolf",
            text = """
                Haste
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "3/2",
        ) to abilities(
            Keyword.Haste,
            werewolfFront,
        ),
        card(
            name = "Ridgepack Alpha",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Whenever a Werewolf you control enters, you may transform it.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "4/3",
            colorIndicator = "R",
        ) to abilities(
            // CR 701.27c: a Werewolf without a back face doesn't transform
            triggered(enters(and(sub("Werewolf"), you)), Step.May(listOf(doing(Effect.Transform(Objects.EventObject(arcanum.api.EventRole.After)))), Players.You)),
            werewolfBack,
        ),
    )

    @Test
    fun wallwardSentry() = cardFaces(Layout.Transform,
        card(
            name = "Wallward Sentry",
            cost = "{2}{R}",
            type = "Creature — Human Warrior Werewolf",
            text = """
                Defender
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "1/5",
        ) to abilities(
            Keyword.Defender,
            werewolfFront,
        ),
        card(
            name = "Scourge of the Wall",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                This creature attacks each combat if able.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "5/5",
            colorIndicator = "R",
        ) to abilities(
            static(Mechanic.MustAttack()),
            werewolfBack,
        ),
    )

    @Test
    fun backwoodFeller() = cardFaces(Layout.Transform,
        card(
            name = "Backwood Feller",
            cost = "{1}{G}",
            type = "Creature — Human Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "2/1",
        ) to abilities(
            werewolfFront,
        ),
        card(
            name = "Bark Render",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Trample
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "4/2",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Trample,
            werewolfBack,
        ),
    )

    // CR 701.27e: "transforms into [name]"; CR 115.1: the creature target is chosen among the first target's player's creatures
    @Test
    fun packmasterOfTheMoors() = cardFaces(Layout.Transform,
        card(
            name = "Packmaster of the Moors",
            cost = "{2}{R}{G}",
            type = "Creature — Human Werewolf",
            text = """
                Whenever this creature enters or transforms into Packmaster of the Moors, create a 2/2 green Wolf creature token and you gain 2 life.
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Packmaster of the Moors"))))), create(wolf), gainLife(2)),
            werewolfFront,
        ),
        card(
            name = "Despoiler of the Moors",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Trample
                Whenever this creature transforms into Despoiler of the Moors, it deals 2 damage to target opponent or planeswalker and 2 damage to up to one target creature that player or that planeswalker's controller controls.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "4/4",
            colorIndicator = "RG",
        ) to abilities(
            Keyword.Trample,
            triggered(on(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named("Despoiler of the Moors")))),
                damage(2, Recipients(target = "opponent or planeswalker")),
                damage(2, toObjects(target("creature"))),
                targets = listOf(
                    TargetSlot("opponent or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Opponent), min = n(1), max = n(1)),
                    slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Or(listOf(
                        PlayerFilter.Is(Players.Target("opponent or planeswalker")),
                        PlayerFilter.Is(Players.ControllerOf(target("opponent or planeswalker"))))))), min = 0))),
            werewolfBack,
        ),
    )

    // CR 613.1f: the menace is granted in layer 6
    @Test
    fun cragPassOutlaw() = cardFaces(Layout.Transform,
        card(
            name = "Crag Pass Outlaw",
            cost = "{1}{R}{R}",
            type = "Creature — Human Rogue Werewolf",
            text = """
                First strike
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.FirstStrike,
            werewolfFront,
        ),
        card(
            name = "Horror of Crag Pass",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Double strike
                Werewolves you control have menace. (A creature with menace can't be blocked except by two or more creatures.)
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "3/3",
            colorIndicator = "R",
        ) to abilities(
            Keyword.DoubleStrike,
            static(has(Keyword.Menace), affects = Objects.Each(and(sub("Werewolf"), you))),
            werewolfBack,
        ),
    )

    @Test
    fun reeveOfThornwick() = cardFaces(Layout.Transform,
        card(
            name = "Reeve of Thornwick",
            cost = "{1}{G}",
            type = "Creature — Human Advisor Werewolf",
            text = """
                Other Human creatures you control get +1/+1.
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "1/1",
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(and(another, sub("Human"), creature, you))),
            werewolfFront,
        ),
        card(
            name = "Thornwick Pack Alpha",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Each other creature you control that's a Werewolf or a Wolf gets +1/+1.
                At the beginning of your end step, create a 2/2 green Wolf creature token.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "3/3",
            colorIndicator = "G",
        ) to abilities(
            static(pt(1, 1), affects = Objects.Each(and(another, creature, you, or(sub("Werewolf"), sub("Wolf"))))),
            triggered(stepBegins(TurnStep.End, PlayerFilter.You), create(wolf)),
            werewolfBack,
        ),
    )

    @Test
    fun shunnedFarmhand() = cardFaces(Layout.Transform,
        card(
            name = "Shunned Farmhand",
            cost = "{1}{G}",
            type = "Creature — Human Werewolf",
            text = """
                {T}: Add {G}.
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "1/1",
        ) to abilities(
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
            werewolfFront,
        ),
        card(
            name = "Moonstruck Farmhand",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Vigilance
                {T}: Add {G}{G}.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "2/2",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Vigilance,
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{G}")!!)), Players.You))),
            werewolfBack,
        ),
    )

    // CR 614.1c: "enters with" counters; CR 701.20e: only you look at the two cards; the other stays on top
    @Test
    fun spiritreaderBoard() = cardFaces(Layout.Transform,
        card(
            name = "Spiritreader Board",
            cost = "{1}{U}",
            type = "Artifact",
            text = """
                This artifact enters with three omen counters on it.
                {1}{U}, {T}: Look at the top two cards of your library. Put one of them into your graveyard.
                Whenever a creature card is put into your graveyard from anywhere, remove an omen counter from this artifact. Then if it has no omen counters on it, transform it.
            """,
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.Named("omen"), amount = n(3))))), affects = Affected.Objects(Objects.Self)),
            activated(cost(mana("{1}{U}"), tap),
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Graveyard)))),
            triggered(on(EventPattern.ZoneChange(and(creature, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard)),
                doing(Effect.RemoveCounters(Objects.Self, CounterKind.Named("omen"), n(1))),
                Step.If(Condition.ObjectMatches(Objects.Self, not(ObjectFilter.HasCounter(CounterKind.Named("omen"), atLeast = 1))),
                    then = listOf(transformSelf))),
        ),
        card(
            name = "Boardbound Spirit",
            cost = null,
            type = "Creature — Spirit",
            text = """
                Flying
                This creature can block only creatures with flying.
                {3}{U}: Draw two cards, then discard a card.
            """,
            pt = "3/2",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Flying,
            static(Mechanic.CanBlockOnly(and(creature, ObjectFilter.HasKeyword(Keyword.Flying)))),
            activated(manaCost("{3}{U}"), draw(2), discard(1)),
        ),
    )

    @Test
    fun hamletRunner() = cardFaces(Layout.Transform,
        card(
            name = "Hamlet Runner",
            cost = "{R}",
            type = "Creature — Human Werewolf",
            text = """
                Haste
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "1/1",
        ) to abilities(
            Keyword.Haste,
            werewolfFront,
        ),
        card(
            name = "Moonrise Runner",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Menace (This creature can't be blocked except by two or more creatures.)
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "2/2",
            colorIndicator = "R",
        ) to abilities(
            Keyword.Menace,
            werewolfBack,
        ),
    )

    @Test
    fun folkOfMarrowdale() = cardFaces(Layout.Transform,
        card(
            name = "Folk of Marrowdale",
            cost = "{2}{G}",
            type = "Creature — Human Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "2/3",
        ) to abilities(
            werewolfFront,
        ),
        card(
            name = "Pack of Marrowdale",
            cost = null,
            type = "Creature — Werewolf",
            text = "At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.",
            pt = "4/6",
            colorIndicator = "G",
        ) to abilities(
            werewolfBack,
        ),
    )

    /** "add [mana] at the beginning of your next main phase this turn" (a delayed trigger that ends with the turn) */
    private fun nextMainPhaseThisTurn(mana: String) = doing(Effect.CreateDelayedTrigger(
        Trigger.On(listOf(EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You), EventPattern.StepBegins(TurnStep.PostcombatMain, PlayerFilter.You))),
        Instructions(steps = listOf(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(mana)!!)), Players.You)))),
        duration = Duration.EndOfTurn))
}
