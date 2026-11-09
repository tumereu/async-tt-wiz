package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 05: more waterbend (CR 701.67): a ward cost (CR 702.21a), "unless you waterbend" (CR 118.12a), an
 * alternative cost of a card exiled from an opponent's graveyard (CR 118.9), X that can't be 0 (CR 107.3); and "whenever you waterbend,
 * earthbend, firebend, or airbend" with all four this turn (CR 701.65b, 701.66b, 701.67c, 702.189b).
 */
class TlaChunk05WaterbendMoreTest {
    // CR 702.21a: a ward cost that is a waterbend cost
    @Test
    fun theLeviathanOfReedIsle() = card(
        name = "The Leviathan of Reed Isle",
        cost = "{3}{U}{U}",
        type = "Legendary Creature — Serpent",
        text = """
            Flash
            Ward—Waterbend {4}. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {4}. They can tap their artifacts and creatures to help. Each one pays for {1}.)
            Whenever an opponent draws their second card each turn, you draw two cards.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Ward(cost(waterbend("{4}"))),
        triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.Opponent)), nthThisTurn = 2), draw(2)),
    )

    @Test
    fun tideshaperAscension() = card(
        name = "Tideshaper Ascension",
        cost = "{1}{U}",
        type = "Enchantment",
        text = """
            Whenever a creature you control deals combat damage to a player, put a quest counter on this enchantment. Then if it has four or more quest counters on it, draw a card.
            Waterbend {4}: Target creature can't be blocked this turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(creatureYouControl, arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true)),
            counters(Objects.Self, 1, quest),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(quest, atLeast = 4)), then = listOf(draw(1)))),
        activated(cost(waterbend("{4}")), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", creature))),
    )

    // CR 118.12a: "unless you waterbend"
    @Test
    fun tideshapingLesson() = card(
        name = "Tideshaping Lesson",
        cost = "{3}{U}",
        type = "Sorcery — Lesson",
        text = "Draw three cards. Then discard a card unless you waterbend {2}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)",
    ) parsesTo abilities(
        spell(draw(3),
            Step.Pay(cost(waterbend("{2}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(discard(1)))),
    )

    // CR 701.24 (shuffled into the owner's library)
    @Test
    fun drowningGrip() = card(
        name = "Drowning Grip",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature doesn't untap during its controller's untap step.
            Waterbend {5}: Enchanted creature's owner shuffles it into their library. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.DoesntUntap, affects = Objects.AttachedTo(Objects.Self)),
        activated(cost(waterbend("{5}")),
            doing(Effect.Move(Objects.AttachedTo(Objects.Self), Destination(Zone.Library), preBind = "shuffled")),
            doing(Effect.Shuffle(Players.OwnerOf(Objects.Bound("shuffled"))))),
    )

    // CR 608.2g: casting a spell while the ability resolves
    @Test
    fun selvaTheMoonSpirit() = card(
        name = "Selva, the Moon Spirit",
        cost = "{3}{U}",
        type = "Legendary Creature — Spirit Ally",
        text = """
            Flying, vigilance
            Waterbend {5}, {T}: You may cast a noncreature spell from your hand without paying its mana cost. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        activated(cost(waterbend("{5}"), tap),
            Step.Choose(Choice.Objects(and(noncreature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "card", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
    )

    // CR 107.3, 707.2 (tokens copying the exiled cards), 603.7
    @Test
    fun mistyBogVisions() = card(
        name = "Misty Bog Visions",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, waterbend {X}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Exile X target creature cards from graveyards. For each creature card exiled this way, create a token that's a copy of it. At the beginning of your next end step, sacrifice those tokens.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(waterbend("{X}")))),
        spell(
            move(target("cards"), Zone.Exile, postBind = "exiled"),
            Step.ForEach(Each.Objects(Objects.Where(Objects.Bound("exiled"), creature)), bind = "card", steps = listOf(
                doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("card")), n(1), Players.You, bind = "tokens")))),
            delayed(yourEndStep, doing(Effect.Sacrifice(Objects.Bound("tokens")))),
            targets = listOf(TargetSlot("cards", TargetKind.Objects(and(creature, inZone(Zone.Graveyard))), min = x, max = x)),
            announced = listOf("X")),
    )

    // CR 601.2b, 603.7b (a delayed trigger for the rest of the turn)
    @Test
    fun ruinousTideshaping() = card(
        name = "Ruinous Tideshaping",
        cost = "{1}{B}{B}",
        type = "Sorcery — Lesson",
        text = """
            As an additional cost to cast this spell, you may waterbend {4}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            All creatures get -2/-2 until end of turn. If this spell's additional cost was paid, whenever a creature dies this turn, you gain 1 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(waterbend("{4}")), optional = true)),
        spell(pump(Objects.Each(creature), -2, -2),
            Step.If(additionalPaid, then = listOf(doing(Effect.CreateDelayedTrigger(dies(creature), Instructions(steps = listOf(gainLife(1))),
                duration = Duration.EndOfTurn, repeats = true))))),
    )

    // CR 509.1b
    @Test
    fun mistyBogVineshaper() = card(
        name = "Misty Bog Vineshaper",
        cost = "{3}{G}",
        type = "Creature — Human Plant Ally",
        text = """
            This creature can't be blocked by creatures with power 2 or less.
            Waterbend {5}: Put a +1/+1 counter on this creature. Activate only during your turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
        activated(cost(waterbend("{5}")), counters(Objects.Self), rules = duringYourTurn),
    )

    // CR 118.9: casting the exiled card for a waterbend cost of X (its mana value) rather than its mana cost, during your turn only
    @Test
    fun griselTheVeinshaper() = card(
        name = "Grisel, the Veinshaper",
        cost = "{2}{U/B}{U/B}{U/B}",
        type = "Legendary Creature — Human Warlock",
        text = "When Grisel enters, target opponent mills three cards. Exile up to one noncreature, nonland card from that player's graveyard. For as long as you control Grisel, you may cast the exiled card during your turn by waterbending {X} rather than paying its mana cost, where X is its mana value. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Mill(Players.Target("opponent"), n(3))),
            Step.Choose(Choice.Objects(and(noncreature, nonland, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast,
                cost = Cost(listOf(CostPart.Waterbend(ManaCost.parse("{X}")!!, x = Value.Of(NumericProperty.ManaValue, Objects.Affected, arcanum.api.Combine.Single)))),
                holder = PlayerFilter.You, condition = Condition.YourTurn)), Affected.Objects(Objects.Bound("exiled")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(targetOpponent)),
    )

    // CR 107.3: X can't be 0; 613.4b
    @Test
    fun nerysTideclansHope() = card(
        name = "Nerys, Tideclan's Hope",
        cost = "{2}{W}{U}{U}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            Vigilance
            When Nerys enters, create a 1/1 white Ally creature token.
            Waterbend {X}: Creatures you control have base power and toughness X/X until end of turn. X can't be 0. Activate only during your turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, create(allyToken)),
        Ability.Activated(cost(waterbend("{X}")), Instructions(steps = listOf(untilEndOfTurn(Objects.Each(creatureYouControl), Mechanic.SetPowerToughness(x, x))),
            announced = listOf("X")), duringYourTurn.copy(minimumX = 1)),
    )

    // CR 701.65b, 701.66b, 701.67c, 702.189b; 701.27; CR 118.7 (a reduction by coloured mana reduces generic mana the cost lacks)
    @Test
    fun heraldKiro() = cardFaces(Layout.Transform,
        card(
            name = "Herald Kiro",
            cost = "{R}{G}{W}{U}",
            type = "Legendary Creature — Human Avatar Ally",
            text = """
                Flying, firebending 2
                Whenever you waterbend, earthbend, firebend, or airbend, draw a card. Then if you've done all four this turn, transform Herald Kiro.
            """,
            pt = "4/4",
        ) to abilities(
            Keyword.Flying,
            firebending(2),
            triggered(Trigger.On(listOf(EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Waterbend, PlayerFilter.You), EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Earthbend, PlayerFilter.You), EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Firebend, PlayerFilter.You),
                    EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Airbend, PlayerFilter.You))),
                draw(1),
                Step.If(Condition.And(listOf(EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Waterbend, PlayerFilter.You), EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Earthbend, PlayerFilter.You), EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Firebend, PlayerFilter.You),
                    EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Airbend, PlayerFilter.You)).map { Condition.HappenedThisTurn(it, atLeast = 1, measure = Measure.Events) }),
                    then = listOf(doing(Effect.Transform(Objects.Self))))),
        ),
        card(
            name = "Kiro, Master of the Four",
            cost = null,
            type = "Legendary Creature — Avatar Ally",
            text = """
                Flying
                Spells you cast cost {W}{U}{B}{R}{G} less to cast. (This can reduce generic costs.)
                At the beginning of each upkeep, you may transform Kiro, Master of the Four. If you do, you gain 4 life, draw four cards, put four +1/+1 counters on him, and he deals 4 damage to each opponent.
            """,
            pt = "6/6",
        ) to abilities(
            Keyword.Flying,
            static(Mechanic.CostChange(and(spellFilter, you), CostChange.Less(ManaCost.parse("{W}{U}{B}{R}{G}")!!, n(1)))),
            triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Any),
                Step.Pay(steps = listOf(doing(Effect.Transform(Objects.Self))), payer = Players.You, bind = "transformed"),
                Step.If(Condition.Did("transformed"), then = listOf(gainLife(4), draw(4), counters(Objects.Self, 4), damage(4, eachOpponent)))),
        ),
    )
}
