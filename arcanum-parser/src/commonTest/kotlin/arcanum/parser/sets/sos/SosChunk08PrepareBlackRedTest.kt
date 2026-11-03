package arcanum.parser.sets.sos

import arcanum.api.Condition
import arcanum.api.CostPart
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
import arcanum.api.Measure
import arcanum.api.NumericProperty
import arcanum.api.Combine
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 08: preparation cards (CR 722) in black and red: becoming prepared at your upkeep after surveilling, at
 * the end step after two creatures died, the first time you gain life each turn, at your first and second main phases (CR 505.1a, b),
 * on your third spell each turn; the spells: reanimation with life loss by mana value (CR 608.2h), a tutor, Seething Song mana, a
 * delayed "when you next cast" copy (CR 603.7, 707.10), nonbasic land destruction and Spirit tokens.
 */
class SosChunk08PrepareBlackRedTest {

    @Test
    fun daringGourmand() = cardFaces(
        Layout.Prepare,
        card(
            name = "Daring Gourmand",
            cost = "{2}{B}",
            type = "Creature — Human Warlock",
            pt = "3/2",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Take a Nibble",
            cost = "{B}",
            type = "Sorcery",
            text = "Put a +1/+1 counter on target creature. You gain 1 life.",
        ) to abilities(
            spell(counters(target("creature")), gainLife(1), targets = listOf(slot("creature", creature))),
        ),
    )

    @Test
    fun jovialBonecaller() = cardFaces(
        Layout.Prepare,
        card(
            name = "Jovial Bonecaller",
            cost = "{3}{B}",
            type = "Creature — Orc Warlock",
            pt = "4/2",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Rouse the Fallen",
            cost = "{B}",
            type = "Sorcery",
            text = "Return target creature card from your graveyard to your hand.",
        ) to abilities(
            spell(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature)))),
        ),
    )

    // CR 700.4: each creature that died counts
    @Test
    fun emeritusOfSorrow() = cardFaces(
        Layout.Prepare,
        card(
            name = "Emeritus of Sorrow",
            cost = "{3}{B}",
            type = "Creature — Vampire Warlock",
            pt = "5/4",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                At the beginning of your end step, if two or more creatures died this turn, this creature becomes prepared.
            """,
        ) to abilities(
            entersPrepared,
            triggered(stepBegins(TurnStep.End, PlayerFilter.You), becomesPrepared(), interveningIf = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 2, measure = Measure.Objects)),
        ),
        card(
            name = "Fiendish Mentor",
            cost = "{1}{B}",
            type = "Sorcery",
            text = "Search your library for a card, put that card into your hand, then shuffle.",
        ) to abilities(
            spell(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
        ),
    )

    // CR 603.4: not an intervening if; checked after surveilling as it resolves
    @Test
    fun cryptScholar() = cardFaces(
        Layout.Prepare,
        card(
            name = "Crypt Scholar",
            cost = "{2}{B}",
            type = "Creature — Troll Warlock",
            pt = "3/3",
            text = "At the beginning of your upkeep, surveil 1. Then if there are three or more creature cards in your graveyard, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), doing(Effect.Surveil(Players.You, n(1))),
                ifThen(Condition.Exists(inYourGraveyard(creature), atLeast = 3), becomesPrepared())),
        ),
        card(
            name = "Raise the Buried",
            cost = "{B}",
            type = "Sorcery",
            text = "Put target creature card from a graveyard onto the battlefield under your control. You lose life equal to that card's mana value.",
        ) to abilities(
            // CR 608.2h: the card's mana value as it last existed in the graveyard
            spell(
                doing(Effect.Move(target("card"), Destination(Zone.Battlefield, controller = Players.You), preBind = "card")),
                doing(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, Objects.Bound("card"), Combine.Single))),
                targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard)))),
            ),
        ),
    )

    @Test
    fun leechkeeper() = cardFaces(
        Layout.Prepare,
        card(
            name = "Leechkeeper",
            cost = "{1}{B}",
            type = "Creature — Human Warlock",
            pt = "2/2",
            text = "Whenever you gain life for the first time each turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(Trigger.On(listOf(EventPattern.GainLife(PlayerFilter.You)), nthThisTurn = 1), becomesPrepared()),
        ),
        card(
            name = "Vein Draw",
            cost = "{B}",
            type = "Sorcery",
            text = "Each opponent loses 2 life.",
        ) to abilities(
            spell(loseLife(2, Players.Each(PlayerFilter.Opponent))),
        ),
    )

    // CR 505.1a: the first main phase is the precombat main phase
    @Test
    fun searingShadecoil() = cardFaces(
        Layout.Prepare,
        card(
            name = "Searing Shadecoil",
            cost = "{4}{B}",
            type = "Creature — Snake Warlock",
            pt = "4/6",
            text = "At the beginning of your first main phase, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), becomesPrepared()),
        ),
        card(
            name = "Toxic Retort",
            cost = "{B}",
            type = "Sorcery",
            text = "Target creature you control gets +2/+0 and gains deathtouch until end of turn.",
        ) to abilities(
            spell(pump(target("creature"), 2, 0, has(Keyword.Deathtouch)), targets = listOf(slot("creature", creatureYouControl))),
        ),
    )

    // CR 505.1b: the second main phase is the first postcombat main phase of the turn
    @Test
    fun plottingGlibtongue() = cardFaces(
        Layout.Prepare,
        card(
            name = "Plotting Glibtongue",
            cost = "{1}{B}",
            type = "Creature — Vampire Warlock",
            pt = "1/3",
            text = """
                Flying, lifelink
                At the beginning of your second main phase, if you gained 2 or more life this turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Lifelink,
            triggered(Trigger.On(listOf(EventPattern.StepBegins(TurnStep.PostcombatMain, PlayerFilter.You)), nthThisTurn = 1), becomesPrepared(),
                interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 2, measure = Measure.Amount)),
        ),
        card(
            name = "Blood Signature",
            cost = "{B}{B}",
            type = "Sorcery",
            text = "Target player draws two cards and loses 2 life.",
        ) to abilities(
            spell(draw(2, Players.Target("player")), loseLife(2, Players.Target("player")), targets = listOf(targetPlayer)),
        ),
    )

    @Test
    fun flaringFirechanter() = cardFaces(
        Layout.Prepare,
        card(
            name = "Flaring Firechanter",
            cost = "{2}{R}",
            type = "Creature — Dwarf Bard",
            pt = "2/3",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Boiling Chant",
            cost = "{2}{R}",
            type = "Instant",
            text = "Add {R}{R}{R}{R}{R}.",
        ) to abilities(
            spell(doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}{R}{R}{R}")!!)), Players.You))),
        ),
    )

    // CR 603.2: the third spell you cast this turn
    @Test
    fun emeritusOfStrife() = cardFaces(
        Layout.Prepare,
        card(
            name = "Emeritus of Strife",
            cost = "{1}{R}",
            type = "Creature — Human Wizard",
            pt = "2/2",
            text = """
                First strike
                Whenever you cast your third spell each turn, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.FirstStrike,
            triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 3), becomesPrepared()),
        ),
        card(
            name = "Skyfire Jolt",
            cost = "{R}",
            type = "Instant",
            text = "Skyfire Jolt deals 3 damage to any target.",
        ) to abilities(
            spell(damage(3, toAny), targets = listOf(anyTarget)),
        ),
    )

    @Test
    fun goblinGlassblower() = cardFaces(
        Layout.Prepare,
        card(
            name = "Goblin Glassblower",
            cost = "{1}{R}",
            type = "Creature — Goblin Sorcerer",
            pt = "2/2",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Proud Handiwork",
            cost = "{R}",
            type = "Sorcery",
            text = "Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
        ) to abilities(
            spell(createTreasure()),
        ),
    )

    @Test
    fun vortexArtisan() = cardFaces(
        Layout.Prepare,
        card(
            name = "Vortex Artisan",
            cost = "{1}{R}{R}",
            type = "Creature — Minotaur Sorcerer",
            pt = "3/2",
            text = """
                Haste
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Haste, entersPrepared,
        ),
        card(
            name = "Flare Salvo",
            cost = "{1}{R}",
            type = "Sorcery",
            text = "Destroy target nonbasic land.",
        ) to abilities(
            spell(destroy(target("land")), targets = listOf(slot("land", and(not(ObjectFilter.Super(Supertype.Basic)), land)))),
        ),
    )

    // CR 603.7: a delayed trigger until the end of the turn
    @Test
    fun pigmentHerder() = cardFaces(
        Layout.Prepare,
        card(
            name = "Pigment Herder",
            cost = "{4}{R}",
            type = "Creature — Orc Sorcerer",
            pt = "4/4",
            text = """
                Flying
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Flying, entersPrepared,
        ),
        card(
            name = "Vivid Stroke",
            cost = "{R}",
            type = "Sorcery",
            text = "When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.",
        ) to abilities(
            spell(doing(Effect.CreateDelayedTrigger(youCast(instantOrSorcerySpell), Instructions(steps = listOf(
                doing(Effect.CopySpell(thatSpell, Players.You, newTargets = true)))), duration = Duration.EndOfTurn))),
        ),
    )

    // CR 702.21a: ward with a life payment
    @Test
    fun discordScholar() = cardFaces(
        Layout.Prepare,
        card(
            name = "Discord Scholar",
            cost = "{2}{R}",
            type = "Creature — Orc Sorcerer",
            pt = "3/2",
            text = """
                Ward—Pay 2 life.
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Ward(cost(CostPart.PayLife(n(2)))), entersPrepared,
        ),
        card(
            name = "Rouse the Eras",
            cost = "{5}{R}",
            type = "Sorcery",
            text = "Create two 2/2 red and white Spirit creature tokens.",
        ) to abilities(
            spell(create(spirit, n(2))),
        ),
    )
}
