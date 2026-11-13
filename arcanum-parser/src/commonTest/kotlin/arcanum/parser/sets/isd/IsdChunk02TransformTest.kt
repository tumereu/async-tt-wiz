package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 02: transforming double-faced cards (CR 701.27, 712). The werewolves transform at each upkeep by what was cast last turn
 * (intervening "if", CR 603.4); the others by a counter count, a loot, a look, a payment or a creature dying.
 */
class IsdChunk02TransformTest {
    private val hatchling = CounterKind.Named("hatchling")
    private fun attackedThisTurn() = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Is(Objects.Self))))

    // CR 701.9a: only cards actually discarded count; CR 701.27a: transform; CR 603.4: "if" checked when the end step begins
    @Test
    fun lanternScribe() = cardFaces(Layout.Transform,
        card(
            name = "Lantern Scribe",
            cost = "{2}{U}",
            type = "Creature — Human Advisor",
            text = "{T}: Draw a card, then discard a card. If a creature card is discarded this way, untap this creature, then transform it.",
            pt = "0/1",
        ) to abilities(
            activated(cost(tap), draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, postBind = "discarded")),
                ifThen(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("discarded"), creature)), Comparison.AtLeast, n(1)),
                    untapIt(Objects.Self), transformSelf)),
        ),
        card(
            name = "Frenzied Scribe",
            cost = null,
            type = "Creature — Human Mutant",
            text = "At the beginning of your end step, if this creature didn't attack this turn, tap this creature, then transform it.",
            pt = "5/1",
            colorIndicator = "U",
        ) to abilities(
            triggered(yourEndStep, tapIt(Objects.Self), transformSelf, interveningIf = Condition.Not(attackedThisTurn())),
        ),
    )

    // CR 122.1: counters; CR 608.2c: the condition is checked as the ability resolves
    @Test
    fun hatchlingVessel() = cardFaces(Layout.Transform,
        card(
            name = "Hatchling Vessel",
            cost = "{1}{U}",
            type = "Creature — Lizard Egg",
            text = """
                Defender
                {1}{U}: Put a hatchling counter on this creature. Then if there are five or more hatchling counters on it, remove all of them and transform it.
            """,
            pt = "0/3",
        ) to abilities(
            Keyword.Defender,
            activated(manaCost("{1}{U}"), counters(Objects.Self, 1, hatchling),
                ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(hatchling, atLeast = 5)),
                    Step.Do(Effect.RemoveCounters(Objects.Self, hatchling, Value.Of(NumericProperty.Counters(hatchling), Objects.Self, Combine.Single))),
                    transformSelf)),
        ),
        card(
            name = "Hatched Horror",
            cost = null,
            type = "Creature — Lizard Horror",
            text = "Trample",
            pt = "13/13",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Trample,
        ),
    )

    // CR 701.27c: only a double-faced permanent transforms; CR 615.1: a prevention effect for this turn's combat damage
    @Test
    fun lunarHaze() = card(
        name = "Lunar Haze",
        cost = "{1}{G}",
        type = "Instant",
        text = "Transform all Humans. Prevent all combat damage that would be dealt this turn by creatures other than Werewolves and Wolves. (Only double-faced cards can be transformed.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Transform(Objects.Each(and(sub("Human"), permanentFilter)))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(and(creature, not(or(sub("Werewolf"), sub("Wolf")))), RecipientFilter(), combat = true))),
                null, Duration.EndOfTurn)),
        ),
    )

    // CR 613.4c: the lord bonus; CR 602.5b: "Activate only if"; CR 111.10: tokens
    @Test
    fun cryptMatriarch() = cardFaces(Layout.Transform,
        card(
            name = "Crypt Matriarch",
            cost = "{2}{B}{B}",
            type = "Creature — Vampire",
            text = """
                Flying
                {T}: Create a 2/2 black Vampire creature token with flying.
                {B}: Transform this creature. Activate only if you control five or more Vampires.
            """,
            pt = "3/3",
        ) to abilities(
            Keyword.Flying,
            activated(cost(tap), create(vampire2)),
            activated(manaCost("{B}"), transformSelf, rules = anyTime.copy(condition = youControl(sub("Vampire"), 5))),
        ),
        card(
            name = "Crypt Sovereign",
            cost = null,
            type = "Creature — Vampire",
            text = """
                Flying
                Other Vampire creatures you control get +2/+2.
                {T}: Create a 2/2 black Vampire creature token with flying.
            """,
            pt = "5/5",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            static(pt(2, 2), affects = Objects.Each(and(another, sub("Vampire"), creature, you))),
            activated(cost(tap), create(vampire2)),
        ),
    )

    // CR 603.4: the "may" is made on resolution
    @Test
    fun chapelNovice() = cardFaces(Layout.Transform,
        card(
            name = "Chapel Novice",
            cost = "{1}{W}",
            type = "Creature — Human",
            text = "At the beginning of your upkeep, you may transform this creature.",
            pt = "1/1",
        ) to abilities(
            triggered(yourUpkeep, Step.May(listOf(transformSelf), Players.You)),
        ),
        card(
            name = "Chapel Fiend",
            cost = null,
            type = "Creature — Horror",
            text = "At the beginning of your end step, you lose 1 life.",
            pt = "3/3",
            colorIndicator = "W",
        ) to abilities(
            triggered(yourEndStep, loseLife(1)),
        ),
    )

    // CR 701.20e: looking shows the card only to you; CR 701.20a: revealing shows it to all players
    @Test
    fun peekerOfPages() = cardFaces(Layout.Transform,
        card(
            name = "Peeker of Pages",
            cost = "{U}",
            type = "Creature — Human Wizard",
            text = "At the beginning of your upkeep, look at the top card of your library. You may reveal that card. If an instant or sorcery card is revealed this way, transform this creature.",
            pt = "1/1",
        ) to abilities(
            triggered(yourUpkeep,
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You)),
                Step.May(listOf(doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed"))), Players.You),
                Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), or(instant, sorcery)), then = listOf(transformSelf))),
        ),
        card(
            name = "Winged Peeker",
            cost = null,
            type = "Creature — Human Insect",
            text = "Flying",
            pt = "3/2",
            colorIndicator = "U",
        ) to abilities(
            Keyword.Flying,
        ),
    )

    // CR 306.5b: loyalty counters; CR 603.8: a state trigger; CR 120.3: damage to a planeswalker removes loyalty counters
    @Test
    fun hunterRelentless() = cardFaces(Layout.Transform,
        card(
            name = "Hunter Relentless",
            cost = "{3}{G}",
            type = "Legendary Planeswalker — Garruk",
            text = """
                When this planeswalker has two or fewer loyalty counters on him, transform him.
                [0]: This planeswalker deals 3 damage to target creature. That creature deals damage equal to its power to him.
                [0]: Create a 2/2 green Wolf creature token.
            """,
            loyalty = "3",
        ) to abilities(
            Ability.Triggered(Trigger.State(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Loyalty, atLeast = 3)))),
                Instructions(steps = listOf(transformSelf))),
            loyalty(0, damage(3, toObjects(target("creature"))), damage(power(target("creature")), toObjects(Objects.Self), source = target("creature")),
                targets = listOf(slot("creature", creature))),
            loyalty(0, create(wolf)),
        ),
        card(
            name = "Hunter, the Cursed",
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

    // CR 603.4, 601.2i: "cast last turn" counts spells cast by any player
    @Test
    fun fenwoodShepherd() = cardFaces(Layout.Transform,
        card(
            name = "Fenwood Shepherd",
            cost = "{1}{G}",
            type = "Creature — Human Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "2/2",
        ) to abilities(
            werewolfFront,
        ),
        card(
            name = "Fenwood Howler",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Intimidate (This creature can't be blocked except by artifact creatures and/or creatures that share a color with it.)
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "3/3",
            colorIndicator = "G",
        ) to abilities(
            Keyword.Intimidate,
            werewolfBack,
        ),
    )

    @Test
    fun weatheredExiles() = cardFaces(Layout.Transform,
        card(
            name = "Weathered Exiles",
            cost = "{4}{G}",
            type = "Creature — Human Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "4/4",
        ) to abilities(werewolfFront),
        card(
            name = "Hardened Exiles",
            cost = null,
            type = "Creature — Werewolf",
            text = "At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.",
            pt = "7/7",
            colorIndicator = "G",
        ) to abilities(werewolfBack),
    )

    // CR 508.1d: attacks each combat if able
    @Test
    fun cragWatcher() = cardFaces(Layout.Transform,
        card(
            name = "Crag Watcher",
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
            name = "Ridge Terror",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                This creature attacks each combat if able.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "5/5",
            colorIndicator = "R",
        ) to abilities(
            Ability.Static(listOf(Mechanic.MustAttack()), affects = Affected.Objects(Objects.Self)),
            werewolfBack,
        ),
    )

    // CR 613.4c: layer 7c bonus to creatures that are attacking
    @Test
    fun packAgitators() = cardFaces(Layout.Transform,
        card(
            name = "Pack Agitators",
            cost = "{3}{R}",
            type = "Creature — Human Werewolf",
            text = """
                Attacking creatures you control get +1/+0.
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "2/3",
        ) to abilities(
            static(pt(1, 0), affects = Objects.Each(and(attacking, creature, you))),
            werewolfFront,
        ),
        card(
            name = "Frenzied Pack",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                Trample
                Attacking creatures you control get +3/+0.
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "5/5",
            colorIndicator = "R",
        ) to abilities(
            Keyword.Trample,
            static(pt(3, 0), affects = Objects.Each(and(attacking, creature, you))),
            werewolfBack,
        ),
    )

    // CR 613.1f: the menace is granted in layer 6
    @Test
    fun mountOutlaw() = cardFaces(Layout.Transform,
        card(
            name = "Mount Outlaw",
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
            name = "Terror of Mount Pass",
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
    fun reeveOfDunmoor() = cardFaces(Layout.Transform,
        card(
            name = "Reeve of Dunmoor",
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
            name = "Packlord of Dunmoor",
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
            triggered(yourEndStep, create(wolf)),
            werewolfBack,
        ),
    )

    @Test
    fun rashUrchin() = cardFaces(Layout.Transform,
        card(
            name = "Rash Urchin",
            cost = "{R}",
            type = "Creature — Human Rogue Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "1/1",
        ) to abilities(werewolfFront),
        card(
            name = "Savage Hunter",
            cost = null,
            type = "Creature — Werewolf",
            text = "At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.",
            pt = "3/2",
            colorIndicator = "R",
        ) to abilities(werewolfBack),
    )

    // CR 603.4: "you may pay" on resolution, then "if you do"; both faces pay {2}{B}{B}
    @Test
    fun shriekingFlitter() = cardFaces(Layout.Transform,
        card(
            name = "Shrieking Flitter",
            cost = "{2}{B}",
            type = "Creature — Bat",
            text = """
                Flying
                At the beginning of your upkeep, you may pay {2}{B}{B}. If you do, transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.Flying,
            triggered(yourUpkeep, Step.Pay(cost(mana("{2}{B}{B}")), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), transformSelf)),
        ),
        card(
            name = "Stalking Fiend",
            cost = null,
            type = "Creature — Vampire",
            text = "At the beginning of your upkeep, you may pay {2}{B}{B}. If you do, transform this creature.",
            pt = "5/5",
            colorIndicator = "B",
        ) to abilities(
            triggered(yourUpkeep, Step.Pay(cost(mana("{2}{B}{B}")), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), transformSelf)),
        ),
    )

    // CR 700.4: dies; CR 603.10a: leaves-the-battlefield triggers look back
    @Test
    fun gatewatchGuard() = cardFaces(Layout.Transform,
        card(
            name = "Gatewatch Guard",
            cost = "{3}{W}",
            type = "Creature — Human Soldier",
            text = """
                Vigilance
                Whenever another creature you control dies, you may transform this creature.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.Vigilance,
            triggered(dies(and(another, creature, you)), Step.May(listOf(transformSelf), Players.You)),
        ),
        card(
            name = "Gatewatch Veteran",
            cost = null,
            type = "Creature — Human Soldier",
            text = "Trample",
            pt = "5/4",
            colorIndicator = "W",
        ) to abilities(
            Keyword.Trample,
        ),
    )

    @Test
    fun hauntedOutcast() = cardFaces(Layout.Transform,
        card(
            name = "Haunted Outcast",
            cost = "{3}{R}",
            type = "Creature — Human Warrior Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "3/2",
        ) to abilities(werewolfFront),
        card(
            name = "Raging Beast",
            cost = null,
            type = "Creature — Werewolf",
            text = "At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.",
            pt = "6/4",
            colorIndicator = "R",
        ) to abilities(werewolfBack),
    )

    @Test
    fun villageForger() = cardFaces(Layout.Transform,
        card(
            name = "Village Forger",
            cost = "{1}{R}",
            type = "Creature — Human Werewolf",
            text = """
                First strike
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
            pt = "1/1",
        ) to abilities(
            Keyword.FirstStrike,
            werewolfFront,
        ),
        card(
            name = "Ironjaw",
            cost = null,
            type = "Creature — Werewolf",
            text = """
                First strike
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
            pt = "3/1",
            colorIndicator = "R",
        ) to abilities(
            Keyword.FirstStrike,
            werewolfBack,
        ),
    )

    @Test
    fun settlersOfMarrow() = cardFaces(Layout.Transform,
        card(
            name = "Settlers of Marrow",
            cost = "{2}{G}",
            type = "Creature — Human Werewolf",
            text = "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.",
            pt = "2/3",
        ) to abilities(werewolfFront),
        card(
            name = "Howlpack of Marrow",
            cost = null,
            type = "Creature — Werewolf",
            text = "At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.",
            pt = "4/6",
            colorIndicator = "G",
        ) to abilities(werewolfBack),
    )
}
