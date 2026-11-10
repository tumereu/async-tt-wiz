package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Gifted
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 09: white cards. A tapped Treasure; a library search put on top after shuffling (CR 701.24b); one search
 * splitting its cards between the battlefield and the hand; "the first/second/third time this ability has resolved this turn"; a
 * gift (CR 702.174) whose promise stops all players casting spells; "for each other creature you control"; kicked or not
 * choosing between two target slots (CR 601.2c, 702.33d) and a delayed trigger at the next upkeep (CR 603.7); "if you've drawn
 * two or more cards this turn" (CR 603.4); a cost reduction by the target's status (CR 601.2f); flashback's "cast from a
 * graveyard"; a player searching their own library for the exiled attackers (CR 701.23, D50); modes (CR 700.2).
 */
class HobChunk09WhiteTest {
    @Test
    fun lankyGreyHound() = card(
        name = "Lanky Grey Hound",
        cost = "{3}",
        type = "Creature — Dog",
        text = """
            Flash
            Reach
            When this creature enters, create a tapped Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Reach,
        triggered(entersSelf, createTreasure(tapped = true)),
    )

    // CR 701.24b: shuffled leaving the found card out, then put on top
    @Test
    fun ancientThrush() = card(
        name = "Ancient Thrush",
        cost = "{2}",
        type = "Creature — Bird",
        text = """
            Flying
            When this creature enters, you gain 2 life. You may search your library for a basic land card, reveal it, then shuffle and put that card on top.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, gainLife(2), Step.May(listOf(
            search(basicLand(), reveal = true),
            Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0)), player = Players.You)),
    )

    // CR 701.23: one found card onto the battlefield tapped, the other into the hand
    @Test
    fun herdOfPonies() = card(
        name = "Herd of Ponies",
        cost = "{2}",
        type = "Creature — Horse",
        text = "{2}, {T}, Sacrifice this creature: Search your library for up to two basic land cards, reveal them, put one onto the battlefield tapped and the other into your hand, then shuffle.",
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, sacrificeSelf),
            search(basicLand(), max = 2, reveal = true),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Zone.Hand),
            shuffle),
    )

    // CR 603.4: which resolution of this ability it is this turn, counting this one
    @Test
    fun camelliaTuck() = card(
        name = "Camellia Tuck",
        cost = "{1}{W}",
        type = "Legendary Creature — Halfling Citizen",
        text = "Whenever a token you control enters, you gain 1 life if this is the first time this ability has resolved this turn. If it's the second time, draw a card. If it's the third time, put a +1/+1 counter on each creature you control.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(enters(and(token, you)),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(1)), then = listOf(gainLife(1))),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(2)), then = listOf(draw(1))),
            Step.If(Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, n(3)), then = listOf(counters(Objects.Each(creatureYouControl))))),
    )

    // CR 702.174, 101.2: if the gift was promised, no player can cast spells this turn
    @Test
    fun tobbosPloy() = card(
        name = "Tobbo's Ploy",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Gift a Treasure (You may promise an opponent a gift as you cast this spell. If you do, they create a Treasure token before its other effects. It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Return target spell to its owner's hand. If the gift was promised, players can't cast spells this turn.
        """,
    ) parsesTo abilities(
        Keyword.Gift(Gifted.Treasure),
        spell(move(target("spell"), Zone.Hand),
            Step.If(Condition.Paid(Keyword.Gift.Kind, times = 1, of = Objects.Self), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(ObjectFilter.Any)),
                Affected.Players(Players.Each(PlayerFilter.Any)), Duration.EndOfTurn)))),
            targets = listOf(slot("spell", spellFilter))),
    )

    @Test
    fun dwarvenQuartermaster() = card(
        name = "Dwarven Quartermaster",
        cost = "{1}{W}",
        type = "Creature — Dwarf Citizen",
        text = "{3}{W}: Creatures you control get +1/+1 until end of turn.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{3}{W}")), pump(Objects.Each(creatureYouControl), 1, 1)),
    )

    // CR 608.2h: counted as it resolves
    @Test
    fun eagleOfTheHighLedge() = card(
        name = "Eagle of the High Ledge",
        cost = "{4}{W}",
        type = "Creature — Bird Soldier",
        text = """
            Flying
            Whenever this creature attacks, it gets +1/+1 until end of turn for each other creature you control.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(and(another, creature, you)), count(and(another, creature, you))))),
    )

    /** Kicked, any number of targets; otherwise exactly one (CR 601.2c: only the slot that applies exists). The birds are counted
     * from the creatures that reached the hand. */
    // CR 702.33d, 603.7
    @Test
    fun theHawksAreHere() = card(
        name = "The Hawks Are Here!",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Kicker {2}{W}{W} (You may pay an additional {2}{W}{W} as you cast this spell.)
            Choose target creature you own. If this spell was kicked, instead choose any number of target creatures you own. Return each chosen creature to your hand. At the beginning of the next upkeep, create a 4/4 white Bird Soldier creature token with flying for each creature returned to your hand this way.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}{W}{W}"))),
        spell(
            Step.Do(Effect.Move(Objects.Union(listOf(target("creature"), target("creatures"))), Destination(Zone.Hand), postBind = "returned")),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), Instructions(steps = listOf(
                create(creatureToken("Bird Soldier", listOf(Color.White), 4, 4, abilities = listOf(Keyword.Flying)), Value.Count(Objects.Bound("returned"))))))),
            targets = listOf(
                slot("creature", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You))).copy(onlyIf = Condition.Not(Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self))),
                slot("creatures", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0, max = null).copy(onlyIf = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)))),
    )

    /** The same wording creating two tokens for each creature returned: twice as many (a misread found by swapping wordings: the
     * number was dropped). */
    // CR 603.7, 111.1
    @Test
    fun theHawksAreHereTwice() = card(
        name = "The Hawks Are Here Twice!",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Kicker {2}{W}{W} (You may pay an additional {2}{W}{W} as you cast this spell.)
            Choose target creature you own. If this spell was kicked, instead choose any number of target creatures you own. Return each chosen creature to your hand. At the beginning of the next upkeep, create two 1/1 white Bird creature tokens with flying for each creature returned to your hand this way.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}{W}{W}"))),
        spell(
            Step.Do(Effect.Move(Objects.Union(listOf(target("creature"), target("creatures"))), Destination(Zone.Hand), postBind = "returned")),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.Upkeep, PlayerFilter.Any), Instructions(steps = listOf(
                create(creatureToken("Bird", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying)), Value.Times(Value.Count(Objects.Bound("returned")), 2)))))),
            targets = listOf(
                slot("creature", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You))).copy(onlyIf = Condition.Not(Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self))),
                slot("creatures", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0, max = null).copy(onlyIf = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)))),
    )

    // CR 603.4: "if you've drawn two or more cards this turn"
    @Test
    fun mereTownTinker() = card(
        name = "Mere-town Tinker",
        cost = "{3}{W}",
        type = "Creature — Human Artificer",
        text = "At the beginning of combat on your turn, if you've drawn two or more cards this turn, another target creature you control gets +3/+0 and gains first strike until end of turn.",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 3, 0, has(Keyword.FirstStrike)), targets = listOf(slot("creature", and(another, creature, you))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events)),
    )

    // CR 601.2f: the cost reduction reads the spell's own target
    @Test
    fun gloriousFinale() = card(
        name = "Glorious Finale",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped creature.
            Glorious Finale deals 5 damage to target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.ObjectMatches(target("creature"), tapped)),
        spell(damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 702.34a
    @Test
    fun instantOfTriumph() = card(
        name = "Instant of Triumph",
        cost = "{W}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on target creature you control. If this spell was cast from a graveyard, also put a +1/+1 counter on each other creature you control.
            Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(counters(target("creature")),
            // "other": other than the target (the spell is never a creature)
            Step.If(Condition.CastFrom(Zone.Graveyard, Objects.Self),
                then = listOf(counters(Objects.Each(and(ObjectFilter.Not(ObjectFilter.Is(target("creature"))), creature, you))))),
            targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Flashback(manaCost("{4}{W}")),
    )

    // CR 701.23, D50: that player searches their own library and the lands enter under their control
    @Test
    fun calmTheChaos() = card(
        name = "Calm the Chaos",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = "Exile all attacking creatures target player controls. That player may search their library for that many basic land cards, put those cards onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            move(Objects.Each(and(creature, attacking, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile, postBind = "exiled"),
            Step.May(listOf(
                Step.Do(Effect.Search(Players.Target("player"), basicLand(), max = Value.Count(Objects.Bound("exiled")), zones = listOf(Zone.Library), bind = "found",
                    searcher = Players.Target("player"))),
                move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
                Step.Do(Effect.Shuffle(Players.Target("player")))), player = Players.Target("player")),
            targets = listOf(targetPlayer)),
    )

    // CR 700.2; 205.1b: an artifact in addition to its other types
    @Test
    fun rockAtDaybreak() = card(
        name = "Rock at Daybreak",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with power 4 or greater.
            • Until end of turn, target creature becomes an artifact in addition to its other types and gains indestructible. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, power(Comparison.AtLeast, 4))))),
            mode(untilEndOfTurn(target("creature"), Mechanic.SetTypes(add = listOf(CardType.Artifact)), has(Keyword.Indestructible)), targets = listOf(slot("creature", creature)))))),
    )

    // CR 700.2
    @Test
    fun tharnsFinalStand() = card(
        name = "Tharn's Final Stand",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Creatures you control get +2/+1 until end of turn.
            • Destroy target artifact or enchantment. You gain 2 life.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(pump(Objects.Each(creatureYouControl), 2, 1)),
            mode(destroy(target("permanent")), gainLife(2), targets = listOf(slot("permanent", or(artifact, enchantment))))))),
    )
}
