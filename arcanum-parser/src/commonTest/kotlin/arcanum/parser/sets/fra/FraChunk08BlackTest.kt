package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetOpponent
import kotlin.test.Test

/**
 * Reality Fracture chunk 08: black cards. Enters-or-dies triggers; an opponent sacrificing one of the creatures and
 * planeswalkers with the greatest mana value; "+2/+0 for every seven cards in your graveyard" (rounded down); a return from
 * the graveyard at the beginning of combat after two creatures died; damage to each player and opponent; revealing two cards
 * from outside the game with different names (CR 400.11) and an opponent choosing one; a reflexive trigger after a payment
 * destroying one target for each opponent (CR 603.12, 601.2c); drawing as many cards as the greatest power and losing that
 * much life; an additional cost of sacrificing or paying (CR 601.2f); base power and toughness 0/0.
 */
class FraChunk08BlackTest {
    @Test
    fun pinnacleHexstalker() = card(
        name = "Pinnacle Hexstalker",
        cost = "{4}{B}{B}",
        type = "Creature — Wolf",
        pt = "6/4",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters or dies, you gain 2 life.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
            Keyword.Menace,
            Ability.Triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))), Instructions(steps = listOf(gainLife(2)))),
            Keyword.Typecycling(and(ObjectFilter.Super(Supertype.Basic), land), "Basic landcycling", cost(mana("{2}"))),
        )

    // CR 701.21a
    @Test
    fun crackUnderStrain() = card(
        name = "Crack Under Strain",
        cost = "{2}{B}",
        type = "Instant",
        text = "Target opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and planeswalkers they control. You gain 2 life.",
    ) parsesTo abilities(spell(
            Step.Choose(Choice.Objects(and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Is(Players.Target("opponent"))), ObjectFilter.ManaValue(Comparison.Equal,
                Value.Of(NumericProperty.ManaValue, Objects.Each(and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Is(Players.Target("opponent"))))), Combine.Greatest))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("opponent")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            gainLife(2),
            targets = listOf(targetOpponent),
        ))

    @Test
    fun shedAllHesitation() = card(
        name = "Shed All Hesitation",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Draw two cards. Shed All Hesitation deals 2 damage to each player.",
    ) parsesTo abilities(spell(draw(2), damage(2, Recipients(players = Players.Each(PlayerFilter.Any)))))

    @Test
    fun voidMatterShaper() = card(
        name = "Void Matter Shaper",
        cost = "{B}",
        type = "Creature — Human Warlock",
        pt = "1/2",
        text = """
            When this creature enters, mill three cards.
            This creature gets +2/+0 for every seven cards in your graveyard.
        """,
    ) parsesTo abilities(triggered(entersSelf, mill(3)), static(Mechanic.ModifyPowerToughness(Value.Times(Value.Quotient(cardsInYourGraveyard, 7), 2), n(0))))

    // CR 603.4, 113.6b
    @Test
    fun duskflarePhoenix() = card(
        name = "Duskflare Phoenix",
        cost = "{3}{B}",
        type = "Creature — Phoenix",
        pt = "3/2",
        text = """
            Flying, haste
            At the beginning of combat on your turn, if two or more creatures died this turn, return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Haste, Ability.Triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You),
            Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield), postBind = "returned")))), interveningIf = thisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), 2, Measure.Objects), functionsIn = listOf(Zone.Graveyard)))

    @Test
    fun prolongedDeparture() = card(
        name = "Prolonged Departure",
        cost = "{3}{B}",
        type = "Instant",
        text = "Exile target creature or planeswalker. Prolonged Departure deals 1 damage to each opponent and you gain 1 life.",
    ) parsesTo abilities(spell(move(target("permanent"), Zone.Exile), damage(1, eachOpponent), gainLife(1), targets = listOf(slot("permanent", creatureOrPlaneswalker))))

    // CR 400.11, 701.20
    @Test
    fun inferTheUnthinkable() = card(
        name = "Infer the Unthinkable",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "You may reveal exactly two cards you own with different names from outside the game. An opponent chooses one of them. You put that card into your hand.",
    ) parsesTo abilities(spell(
            Step.May(listOf(
                Step.Choose(Choice.Objects(and(ObjectFilter.InZone(Zone.Outside), ObjectFilter.OwnedBy(PlayerFilter.You)), n(2), n(2), distinct = SharedQuality.Name),
                    bind = "revealed", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("revealed")))), player = Players.You),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.Each(PlayerFilter.Opponent)),
            move(Objects.Bound("chosen"), Zone.Hand),
        ))

    @Test
    fun finalWheeze() = card(
        name = "Final Wheeze",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -3/-3 until end of turn.",
    ) parsesTo abilities(spell(pump(target("creature"), -3, -3), targets = listOf(slot("creature", creature))))

    // CR 603.12, 601.2c
    @Test
    fun lichwardenSCharm() = card(
        name = "Lichwarden's Charm",
        cost = "{B}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, you may pay {2}. When you do, for each opponent, destroy up to one target creature or planeswalker that player controls.
            Equipped creature gets +2/+1.
            Equip {2}
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Pay(cost(mana("{2}")), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(
                Step.Do(Effect.ReflexiveTrigger(Instructions(targets = listOf(TargetSlot("permanent",
                    TargetKind.Objects(and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Is(Players.SlotPlayer)))), min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent)),
                    steps = listOf(Step.Do(Effect.Destroy(target("permanent")))))))))),
            static(Mechanic.ModifyPowerToughness(n(2), n(1)), affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(cost(mana("{2}"))),
        )

    // CR 613.4b
    @Test
    fun timesNothing() = card(
        name = "Times Nothing",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature has base power and toughness 0/0 until end of turn.",
    ) parsesTo abilities(spell(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(n(0), n(0))), targets = listOf(slot("creature", creature))))

    @Test
    fun bulwarkStalker() = card(
        name = "Bulwark Stalker",
        cost = "{3}{B}",
        type = "Creature — Horror",
        pt = "3/3",
        text = """
            Deathtouch
            When this creature enters, target creature gets +2/+2 and gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(Keyword.Deathtouch, triggered(entersSelf, pump(target("creature"), 2, 2, Mechanic.GainAbilities(listOf(Keyword.Deathtouch))), targets = listOf(slot("creature", creature))))

    @Test
    fun foulVermin() = card(
        name = "Foul Vermin",
        cost = "{1}{B}",
        type = "Creature — Zombie Rat",
        pt = "1/1",
        text = "When this creature enters, each opponent discards a card.",
    ) parsesTo abilities(triggered(entersSelf, Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), n(1), ObjectFilter.Any))))

    @Test
    fun ascentOfTheDoombringer() = card(
        name = "Ascent of the Doombringer",
        cost = "{4}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Draw cards equal to the greatest power among creatures you control. You lose life equal to the number of cards drawn this way.
            • All creatures get -3/-3 until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(
                Step.Do(Effect.Draw(Players.You, Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest), postBind = "drawn")),
                Step.Do(Effect.LoseLife(Players.You, Value.Count(Objects.Bound("drawn"))))))),
            Mode(Instructions(steps = listOf(pump(Objects.Each(creature), -3, -3)))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun shriekingSpiritcrusher() = card(
        name = "Shrieking Spiritcrusher",
        cost = "{2}{B}",
        type = "Creature — Siren Bard",
        pt = "1/4",
        text = """
            Flying
            Whenever this creature attacks, it deals 1 damage to each opponent and you gain 1 life.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(attacksSelf, damage(1, eachOpponent), gainLife(1)))

    // CR 601.2f
    @Test
    fun hushTheReverberation() = card(
        name = "Hush the Reverberation",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or planeswalker or pay {3}.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
            static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(
                cost(CostPart.Sacrifice(CostObjects.Choose(creatureOrPlaneswalker, n(1)))),
                cost(mana("{3}")),
            ))))),
            spell(Step.Do(Effect.Destroy(target("permanent"))), targets = listOf(slot("permanent", creatureOrPlaneswalker))),
        )

    @Test
    fun fatalCritique() = card(
        name = "Fatal Critique",
        cost = "{1}{B}",
        type = "Instant",
        text = "Destroy target creature or planeswalker that's blue or red. You gain 1 life.",
    ) parsesTo abilities(spell(Step.Do(Effect.Destroy(target("permanent"))), gainLife(1),
            targets = listOf(slot("permanent", and(creatureOrPlaneswalker, or(ObjectFilter.OfColor(Color.Blue), ObjectFilter.OfColor(Color.Red)))))))

    @Test
    fun hypotheticalGravecaller() = card(
        name = "Hypothetical Gravecaller",
        cost = "{2}{B}",
        type = "Creature — Vampire Warlock",
        pt = "4/1",
        text = "{3}{B}, Exile this card from your graveyard: Return another target creature card from your graveyard to your hand.",
    ) parsesTo abilities(activated(cost(mana("{3}{B}"), exileSelfFromGraveyard), move(target("card"), Zone.Hand),
            targets = listOf(slot("card", and(anotherObject, creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))))
}
