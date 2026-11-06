package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 05: black cards, first part: unearth (CR 702.84), an edict with a discard and life in one sentence, an
 * additional cost of one of two kinds (CR 601.2b), "snow or could produce {C}" with overload (CR 106.7, 702.96), threshold, a return from
 * the graveyard to the hand (CR 113.6m), regeneration ruled out (CR 701.19c), a void-counter exile replacement and playing a chosen
 * exiled card (CR 614.1a, 601.2a), looking at cards and splitting them, cards with the same name, life equal to counters as it last
 * existed (CR 608.2h), madness (CR 702.35), explore (CR 701.44), exploit's "exploits a creature" (CR 702.110b) and creatures put into
 * a graveyard.
 */
class Mh2Chunk05BlackTest {
    // CR 702.84a unearth
    @Test
    fun archdemonOfWoe() = card(
        name = "Archdemon of Woe",
        cost = "{5}{B}{B}",
        type = "Creature — Demon",
        text = """
            Flying
            When this creature enters, creatures your opponents control get -2/-2 until end of turn.
            Unearth {3}{B}{B} ({3}{B}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, pump(Objects.Each(creatureOpponentControls), -2, -2)),
        Keyword.Unearth(manaCost("{3}{B}{B}")),
    )

    // CR 608.2c: the steps happen in the order written
    @Test
    fun archonOfMalice() = card(
        name = "Archon of Malice",
        cost = "{6}{B}{B}",
        type = "Creature — Archon",
        text = """
            Flying
            Whenever this creature enters or attacks, target opponent sacrifices a creature or planeswalker of their choice, discards a card, and loses 3 life. You draw a card and gain 3 life.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersOrAttacksSelf,
            Step.Choose(Choice.Objects(and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            discard(1, Players.Target("opponent")),
            loseLife(3, Players.Target("opponent")),
            draw(1),
            gainLife(3),
            targets = listOf(targetOpponent)),
    )

    // CR 601.2b: an additional cost of one of two kinds
    @Test
    fun boneSlivers() = card(
        name = "Bone Slivers",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or discard a card.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(
            Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creature, count = n(1))))),
            Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))),
        )))))),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 106.7: a land that could produce {C}; CR 702.96b overload
    @Test
    fun shatterTheFrost() = card(
        name = "Shatter the Frost",
        cost = "{B}{B}",
        type = "Sorcery",
        text = """
            Destroy target land that is snow or could produce {C}.
            Overload {4}{B}{B} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", and(land, or(ObjectFilter.Super(Supertype.Snow), ObjectFilter.CouldProduce(ManaCost.parse("{C}")!!)))))),
        Keyword.Overload(manaCost("{4}{B}{B}"), Instructions(steps = listOf(
            destroy(Objects.Each(and(land, or(ObjectFilter.Super(Supertype.Snow), ObjectFilter.CouldProduce(ManaCost.parse("{C}")!!)))))))),
    )

    // CR 207.2c threshold
    @Test
    fun cabalNovice() = card(
        name = "Cabal Novice",
        cost = "{1}{B}",
        type = "Creature — Human Warlock",
        text = """
            Discard a card: This creature gains lifelink until end of turn.
            Threshold — This creature gets +1/+2 as long as there are seven or more cards in your graveyard.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(discardACard), gains(Objects.Self, Keyword.Lifelink)),
        static(pt(1, 2), condition = threshold).copy(abilityWord = "Threshold"),
    )

    // CR 113.6m: the return from the graveyard works there
    @Test
    fun rattlingSoothsayer() = card(
        name = "Rattling Soothsayer",
        cost = "{1}{B}",
        type = "Creature — Skeleton Shaman",
        text = """
            This creature can't block.
            When this creature enters, you draw a card and you lose 1 life.
            {2}{B}{B}: Return this card from your graveyard to your hand.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(Mechanic.CantBlock(ObjectFilter.Any)),
        triggered(entersSelf, draw(1), loseLife(1)),
        activated(cost(mana("{2}{B}{B}")), move(Objects.Self, Zone.Hand)).copy(functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 701.19c: can't be regenerated
    @Test
    fun curseItAll() = card(
        name = "Curse It All",
        cost = "{B}{B}",
        type = "Sorcery",
        text = """
            Destroy target creature. A creature destroyed this way can't be regenerated.
            Overload {2}{W}{W} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Destroy(target("creature"), noRegeneration = true)), targets = listOf(slot("creature", creature))),
        Keyword.Overload(manaCost("{2}{W}{W}"), Instructions(steps = listOf(doing(Effect.Destroy(Objects.Each(creature), noRegeneration = true))))),
    )

    // CR 702.28 shadow; CR 614.1a: exiled instead, with a void counter; CR 601.2a: playing the chosen card
    @Test
    fun dauthiNullwalker() = card(
        name = "Dauthi Nullwalker",
        cost = "{B}{B}",
        type = "Creature — Dauthi Rogue",
        text = """
            Shadow (This creature can block or be blocked by only creatures with shadow.)
            If a card would be put into an opponent's graveyard from anywhere, instead exile it with a void counter on it.
            {T}, Sacrifice this creature: Choose an exiled card an opponent owns with a void counter on it. You may play it this turn without paying its mana cost.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Shadow,
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(cardFilter, ObjectFilter.OwnedBy(PlayerFilter.Opponent)), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile, counters = listOf(CountersToPut(CounterKind.Named("void"), n(1)))))))),
        activated(cost(tap, sacrificeSelf),
            Step.Choose(Choice.Objects(and(cardFilter, inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.Opponent), ObjectFilter.HasCounter(CounterKind.Named("void"), atLeast = 1)),
                n(1), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, withoutPaying = true, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("chosen")), Duration.EndOfTurn))),
    )

    // CR 608.2h: the greatest power among the creature cards put there
    @Test
    fun refinedPalate() = card(
        name = "Refined Palate",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard. You gain life equal to the greatest power among creature cards put into your graveyard this way.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Graveyard, postBind = "milled"),
            doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.Power, Objects.Where(Objects.Bound("milled"), creature), Combine.Greatest))),
        ),
    )

    // CR 201.2: cards with the same name
    @Test
    fun resoundingReturn() = card(
        name = "Resounding Return",
        cost = "{B}",
        type = "Sorcery",
        text = "Return target creature card and all other cards with the same name as that card from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(
            move(Objects.Union(listOf(target("card"), Objects.Each(inYourGraveyard(cardFilter, ObjectFilter.Not(ObjectFilter.Is(target("card"))),
                ObjectFilter.SameNameAs(target("card")))))), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun banquetOfReason() = card(
        name = "Banquet of Reason",
        cost = "{3}{B}",
        type = "Enchantment",
        text = "Whenever you discard a card, this enchantment deals 1 damage to any target and you gain 1 life.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)), damage(1, toAny), gainLife(1), targets = listOf(anyTarget)),
    )

    // CR 608.2h: the counters it had as it last existed on the battlefield
    @Test
    fun stripTheEssence() = card(
        name = "Strip the Essence",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = "Exile target creature or planeswalker. You gain life equal to the number of counters on it.",
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Exile),
            doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.Counters(null), target("permanent"), Combine.Single))),
            targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    @Test
    fun giltEdgeProwler() = card(
        name = "Gilt-Edge Prowler",
        cost = "{2}{B}",
        type = "Creature — Human Rogue",
        text = "{1}, {T}, Pay 1 life: Draw a card. Activate only if you've discarded a card this turn.",
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, CostPart.PayLife(n(1))), draw(1), rules = anyTime.copy(
            condition = Condition.HappenedThisTurn(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), atLeast = 1, measure = Measure.Events))),
    )

    // CR 701.20a: the revealed hand; the chosen card is discarded
    @Test
    fun lament() = card(
        name = "Lament",
        cost = "{2}{B}{B}",
        type = "Creature — Elemental Incarnation",
        text = """
            Menace
            When this creature enters, target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
            Evoke—Exile a black card from your hand.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf,
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(nonland, n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetOpponent)),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(arcanum.api.Color.Black), count = n(1)), from = Zone.Hand)))),
    )

    // CR 702.35a madness
    @Test
    fun pitMongrel() = card(
        name = "Pit Mongrel",
        cost = "{3}{B}",
        type = "Creature — Nightmare Dog",
        text = """
            Discard a card: This creature gets +1/+1 until end of turn.
            Madness {2}{B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "4/3",
    ) parsesTo abilities(activated(cost(discardACard), pump(Objects.Self, 1, 1)), Keyword.Madness(manaCost("{2}{B}")))

    @Test
    fun sculleryImp() = card(
        name = "Scullery Imp",
        cost = "{3}{B}",
        type = "Creature — Imp",
        text = """
            Flying, haste
            Madness {B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Flying, Keyword.Haste, Keyword.Madness(manaCost("{B}")))

    // CR 701.44a explore
    @Test
    fun legionOutrider() = card(
        name = "Legion Outrider",
        cost = "{1}{B}",
        type = "Creature — Vampire Soldier",
        text = "{1}, Sacrifice another creature: This creature explores. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(and(another, creature))), doing(Effect.Action(KeywordAction.Explore(Objects.Self)))),
    )

    // CR 702.110b: it exploits a creature when its exploit ability sacrifices one
    @Test
    fun repugnantArchivist() = card(
        name = "Repugnant Archivist",
        cost = "{4}{B}",
        type = "Creature — Gorgon Wizard",
        text = """
            Exploit (When this creature enters, you may sacrifice a creature.)
            Menace
            When this creature exploits a creature, destroy target creature you don't control with mana value 3 or less.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Exploit,
        Keyword.Menace,
        triggered(on(EventPattern.Exploits(isSelf)), destroy(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)), manaValue(Comparison.AtMost, 3))))),
    )

    // CR 700.4: "put into a graveyard from the battlefield" is dying; "your graveyard": its owner is you
    @Test
    fun magusOfTheSpan() = card(
        name = "Magus of the Span",
        cost = "{B}{B}{B}",
        type = "Creature — Human Wizard",
        text = """
            Whenever a nontoken creature is put into your graveyard from the battlefield, create a 2/2 black Zombie creature token.
            When a creature is put into an opponent's graveyard from the battlefield, exile this creature.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(nontoken, creature, ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Battlefield, to = Zone.Graveyard)), create(zombie)),
        triggered(on(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(PlayerFilter.Opponent)), from = Zone.Battlefield, to = Zone.Graveyard)),
            move(Objects.Self, Zone.Exile)),
    )
}
