package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.targetOpponent
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 12: multicoloured cards, R to W. Counters on the Cadets just created for every three cards in your
 * graveyard when cast from a graveyard; spells an opponent casts by mana value; exile until it leaves (CR 610.3); a charm whose
 * Cadet gains haste; reveal-and-discard after damage; sacrificing a land for tapped Heartwood tokens and tapping two artifacts
 * as a cost; a counter unless paid (CR 118.12a); "if this spell wasn't cast from your hand"; flashback with a discard
 * (CR 702.34a); copies of up to one card of each card type cast for free within a total mana value (CR 707.12, 707.10f);
 * temporary theft of a small permanent; equip that costs less for each +1/+1 counter on its target (CR 601.2f, 602.2b).
 */
class FraChunk12MulticoloredBTest {
    // CR 702.34a
    @Test
    fun recurringEnlistment() = card(
        name = "Recurring Enlistment",
        cost = "{2}{U}{B}",
        type = "Sorcery",
        text = """
            Create two 2/2 colorless Wizard Soldier creature tokens named Cadet. If this spell was cast from a graveyard, put a +1/+1 counter on each of them for every three cards in your graveyard.
            Flashback {6}{U}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
            spell(createCadets(2), Step.If(Condition.CastFrom(Zone.Graveyard, Objects.Self),
                then = listOf(Step.Do(Effect.PutCounters(Objects.Bound("created"), p1p1, Value.Quotient(cardsInYourGraveyard, 3)))))),
            Keyword.Flashback(cost(mana("{6}{U}{B}"))),
        )

    @Test
    fun sunroomSentinel() = card(
        name = "Sunroom Sentinel",
        cost = "{G}{W}",
        type = "Creature — Cat Soldier",
        pt = "3/3",
        text = "Whenever an opponent casts a spell with mana value 2 or less, you gain 2 life.",
    ) parsesTo abilities(triggered(on(EventPattern.Cast(and(spellFilter, manaValue(Comparison.AtMost, 2)), PlayerFilter.Opponent)), gainLife(2)))

    // CR 610.3
    @Test
    fun isolationChamber() = card(
        name = "Isolation Chamber",
        cost = "{R}{W}",
        type = "Artifact",
        text = """
            When this artifact enters, exile target nonland permanent an opponent controls with mana value 3 or less until this artifact leaves the battlefield.
            {1}, {T}, Discard a legendary card: Draw a card.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
                targets = listOf(slot("permanent", and(nonland, permanentFilter, controlledBy(PlayerFilter.Opponent), manaValue(Comparison.AtMost, 3))))),
            activated(cost(mana("{1}"), tap, CostPart.Discard(CostObjects.Choose(ObjectFilter.Super(Supertype.Legendary), n(1)))), draw(1)),
        )

    @Test
    fun barbquillCharm() = card(
        name = "Barbquill Charm",
        cost = "{B}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Barbquill Charm deals 3 damage to any target.
            • Target creature gains first strike and deathtouch until end of turn.
            • Create a 2/2 colorless Wizard Soldier creature token named Cadet. It gains haste until end of turn.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(3), Objects.Self))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(gains(target("creature"), Keyword.FirstStrike, Keyword.Deathtouch)))),
            Mode(Instructions(steps = listOf(createCadets(1), gains(Objects.Bound("created"), Keyword.Haste)))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun searingVenom() = card(
        name = "Searing Venom",
        cost = "{B}{R}",
        type = "Sorcery",
        text = "Searing Venom deals 2 damage to target opponent. That player reveals their hand. You choose a nonland card from it. They discard that card.",
    ) parsesTo abilities(spell(
            damage(2, Recipients(players = Players.Target("opponent"))),
            Step.Do(Effect.Reveal(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetOpponent),
        ))

    // CR 118.12
    @Test
    fun veteranTetherwright() = card(
        name = "Veteran Tetherwright",
        cost = "{1}{R}{G}",
        type = "Creature — Human Artificer",
        pt = "1/1",
        text = """
            When this creature enters, you may sacrifice a land. If you do, create two tapped Heartwood tokens. (They're red and green artifacts with "{T}: Add {R} or {G}.")
            Tap two untapped artifacts you control: Put two +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(land, n(1)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Heartwood"), n(2), Players.You, tapped = true))))),
            activated(cost(CostPart.TapObjects(CostObjects.Choose(artifact, n(2)))), counters(Objects.Self, 2)),
        )

    // CR 118.12a
    @Test
    fun hypothesisCharm() = card(
        name = "Hypothesis Charm",
        cost = "{U}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target noncreature spell unless its controller pays {2}.
            • Target creature gets -2/-2 until end of turn.
            • Mill three cards, then draw a card. (To mill three cards, put the top three cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("spell", and(not(creature), spellFilter))), steps = listOf(
                Step.Pay(cost(mana("{2}")), payer = Players.ControllerOf(target("spell")), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(target("spell")))))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(pump(target("creature"), -2, -2)))),
            Mode(Instructions(steps = listOf(mill(3), draw(1)))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 702.34a
    @Test
    fun doubledSight() = card(
        name = "Doubled Sight",
        cost = "{1}{U/R}",
        type = "Instant",
        text = """
            Draw a card. If this spell wasn't cast from your hand, draw two cards instead.
            Flashback—{1}{U/R}{U/R}, Discard a card. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
            spell(Step.If(Condition.Not(Condition.CastFrom(Zone.Hand, Objects.Self)), then = listOf(draw(2)), otherwise = listOf(draw(1)))),
            Keyword.Flashback(cost(mana("{1}{U/R}{U/R}"), discardACard)),
        )

    @Test
    fun contortedDestinies() = card(
        name = "Contorted Destinies",
        cost = "{2}{W}{W}{B}",
        type = "Sorcery",
        text = "Destroy target nonland permanent. Put a +1/+1 counter on each creature target player controls.",
    ) parsesTo abilities(spell(
            Step.Do(Effect.Destroy(target("permanent"))),
            counters(Objects.Each(and(creature, controlledBy(PlayerFilter.Is(Players.Target("player")))))),
            targets = listOf(slot("permanent", and(nonland, permanentFilter)), targetPlayer),
        ))

    // CR 707.12, 707.10f, 601.2c
    @Test
    fun vexmorthQuillon() = card(
        name = "Vexmorth Quillon",
        cost = "{3}{U}{B}{B}",
        type = "Legendary Creature — Elder Sphinx",
        pt = "5/5",
        text = """
            Flying
            When Vexmorth Quillon enters, if you cast him, exile up to one target nonland card of each card type from your graveyard. Copy those cards. You may cast any number of spells with total mana value 6 or less from among the copies without paying their mana costs. (Permanent spells cast this way become tokens.)
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf,
            Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.CopyCard(Objects.Bound("exiled"), Players.You, bind = "copies")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Bound("copies"),
                totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(6))), bind = "toCast", chooser = Players.You),
            Step.Do(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
            targets = listOf(TargetSlot("card", TargetKind.Objects(and(nonland, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), min = n(0), max = null,
                onePer = SharedQuality.CardType)),
            interveningIf = Condition.CastFrom(null, Objects.Self)))

    @Test
    fun vigorleafCharm() = card(
        name = "Vigorleaf Charm",
        cost = "{G}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Target permanent you control gains hexproof and indestructible until end of turn.
            • You draw a card and gain 3 life.
            • Put a +1/+1 counter on target creature you control. Then it fights target creature an opponent controls. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("permanent", and(permanentFilter, you))), steps = listOf(gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible)))),
            Mode(Instructions(steps = listOf(draw(1), gainLife(3)))),
            Mode(Instructions(targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls)),
                steps = listOf(counters(target("creature")), Step.Do(Effect.Fight(target("creature"), target("second creature")))))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 608.2h, 603.7
    @Test
    fun spitefulVictory() = card(
        name = "Spiteful Victory",
        cost = "{W}{B}{B}",
        type = "Instant",
        text = "Exile target creature or planeswalker. If that permanent's mana value was 3 or less, return it to the battlefield tapped under your control. Exile it at the beginning of the next end step.",
    ) parsesTo abilities(spell(
            Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), preBind = "exiledBefore", postBind = "exiled")),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiledBefore"), manaValue(Comparison.AtMost, 3)), then = listOf(
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, tapped = true, controller = Players.You), postBind = "returned")),
                Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any), Instructions(steps = listOf(move(Objects.Bound("returned"), Zone.Exile))))))),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ))

    // CR 702.6a, 601.2f
    @Test
    fun duelistSEdges() = card(
        name = "Duelist's Edges",
        cost = "{2}{R}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, it deals 3 damage to any target and you gain 3 life.
            Equipped creature gets +2/+1.
            Equip {3}. This ability costs {1} less to activate for each +1/+1 counter on the creature it targets.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, Step.Do(Effect.DealDamage(Recipients(target = "any"), n(3), Objects.Self)), gainLife(3), targets = listOf(anyTarget)),
            static(Mechanic.ModifyPowerToughness(n(2), n(1)), affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(cost(mana("{3}")), costChange = CostChange.Less(ManaCost.parse("{1}")!!,
                Value.Of(NumericProperty.Counters(p1p1), target("creature"), Combine.Single))),
        )
}
