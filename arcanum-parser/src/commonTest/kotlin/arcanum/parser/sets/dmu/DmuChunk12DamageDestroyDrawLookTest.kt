package arcanum.parser.sets.dmu

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 12: a sacrifice-outlet pinger, destroy modes, an edict planeswalker, looting and drawing triggers, tap-creatures
 * costs, land creatures and Equipment/Vehicle grants, death and draw drains, affinity, and look-at-the-top effects.
 */
class DmuChunk12DamageDestroyDrawLookTest {
    // CR 602.1, 701.21a: sacrificing another creature as a cost
    @Test
    fun slingmaulCyclops() = card(
        name = "Slingmaul Cyclops", cost = "{3}{R}{R}", type = "Creature — Cyclops", pt = "5/4",
        text = "{1}, Sacrifice another creature: This creature deals 1 damage to any target.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(and(another, creature))), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 700.2: modes, each with its own target; CR 701.8
    @Test
    fun rendWickedness() = card(
        name = "Rend Wickedness", cost = "{1}{W}", type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with toughness 4 or greater.
            • Destroy target enchantment.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtLeast, n(4)))))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
        ))),
    )

    // CR 701.8, 608.2h (last known information: the creature's mana value as it last existed), 119.1
    @Test
    fun snuffTheLantern() = card(
        name = "Snuff the Lantern", cost = "{2}{B}{B}", type = "Instant",
        text = "Destroy target creature or planeswalker. If its mana value was 3 or less, you gain 3 life.",
    ) parsesTo abilities(
        spell(
            destroy(target("permanent")),
            Step.If(Condition.ObjectMatches(target("permanent"), manaValue(Comparison.AtMost, 3)), listOf(gainLife(3))),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )

    // CR 700.2, 701.8, 120.3
    @Test
    fun grindToGravel() = card(
        name = "Grind to Gravel", cost = "{1}{R}", type = "Sorcery",
        text = """
            Choose one —
            • Destroy target artifact.
            • Destroy target creature with defender.
            • Grind to Gravel deals 1 damage to each creature your opponents control.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, kw(Keyword.Defender.kind))))),
            mode(damage(1, toObjects(Objects.Each(creatureOpponentControls)))),
        ))),
    )

    // CR 306.5, 701.21a, 700.3
    @Test
    fun nerissaOfTheShroud() = card(
        name = "Nerissa of the Shroud", cost = "{1}{B}{B}", type = "Legendary Planeswalker — Nerissa", loyalty = "3",
        text = """
            [+1]: Each player discards a card.
            [−2]: Target player sacrifices a creature.
            [−6]: Separate all permanents target player controls into two piles. That player sacrifices all permanents in the pile of their choice.
        """,
    ) parsesTo abilities(
        loyalty(1, doing(Effect.Discard(Players.Each(PlayerFilter.Any), n(1), filter = ObjectFilter.Any))),
        loyalty(-2,
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetPlayer)),
        loyalty(-6,
            doing(Effect.SeparatePiles(Objects.Each(and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))),
                separator = Players.You, chooser = Players.Target("player"), chosenBind = "chosen pile")),
            doing(Effect.Sacrifice(Objects.Bound("chosen pile"))),
            targets = listOf(targetPlayer)),
    )

    // CR 603.2, 603.4 (once each turn), 701.9 (discard), 121.1
    @Test
    fun lectureRampart() = card(
        name = "Lecture Rampart", cost = "{2}{U}", type = "Creature — Wall", pt = "0/5",
        text = """
            Defender
            Whenever you cast an instant or sorcery spell, you may draw a card. If you do, discard a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Ability.Triggered(youCast(or(instant, sorcery)), Instructions(steps = listOf(
            Step.Pay(null, listOf(draw(1)), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(discard(1))),
        )), perTurn = 1),
    )

    // CR 121.1, 109.3
    @Test
    fun astralRevelation() = card(
        name = "Astral Revelation", cost = "{4}{U}{U}", type = "Sorcery",
        text = "Draw cards equal to the number of instant and sorcery cards in your graveyard.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Draw(Players.You, count(inYourGraveyard(or(instant, sorcery)))))),
    )

    // CR 603.6a, 121.1, 119.3
    @Test
    fun gloomhallRager() = card(
        name = "Gloomhall Rager", cost = "{2}{B}", type = "Creature — Phyrexian Horror", pt = "2/2",
        text = "When this creature enters, you draw a card and you lose 1 life.",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), loseLife(1)),
    )

    // CR 701.26a (tap), 118.12 optional, 603.2, 702.20
    @Test
    fun roffStormwatchWarden() = card(
        name = "Roff, Stormwatch Warden", cost = "{W}{U}", type = "Legendary Creature — Human Wizard", pt = "1/3",
        text = """
            Whenever you cast an instant or sorcery spell, you may tap two untapped creatures you control. If you do, draw a card.
            {3}{W}{W}: Creatures you control get +1/+1 and gain vigilance until end of turn.
        """,
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)),
            Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(creature, n(2)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(draw(1)))),
        activated(cost(mana("{3}{W}{W}")), untilEndOfTurn(Objects.Each(creatureYouControl), pt(1, 1), has(Keyword.Vigilance))),
    )

    // CR 205.3, 613.1, 702.9 (flying), 305.7
    @Test
    fun tavinaWardenOfWaves() = card(
        name = "Tavina, Warden of Waves", cost = "{G}{G}{U}", type = "Legendary Creature — Merfolk Druid", pt = "3/3",
        text = """
            Land creatures you control have flying.
            Whenever a land you control enters, if you control seven or more lands, up to one target land you control becomes a 3/3 Elemental creature with haste. It's still a land.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), affects = Objects.Each(and(land, creature, you))),
        triggered(on(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield)),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                Mechanic.SetPowerToughness(n(3), n(3)),
                has(Keyword.Haste),
            ), Affected.Objects(Objects.Target("land")), Duration.Indefinitely)),
            targets = listOf(slot("land", and(land, you), min = 0)),
            interveningIf = Condition.Exists(and(land, you), atLeast = 7)),
    )

    // CR 700.4, 603.10a (leaves-the-battlefield look back), 119.3
    @Test
    fun elkaUlKarMercilessPilgrim() = card(
        name = "Elka ul-Kar, Merciless Pilgrim", cost = "{W}{B}", type = "Legendary Creature — Phyrexian Kor Cleric", pt = "2/2",
        text = """
            Deathtouch
            Whenever another creature you control enters, you gain 1 life.
            Whenever another creature you control dies, each opponent loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.ZoneChange(and(another, creature, you), to = Zone.Battlefield)), gainLife(1)),
        triggered(on(EventPattern.ZoneChange(and(another, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)), loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    // CR 121.1, 119.3, 119.1
    @Test
    fun shaldredTheAftermath() = card(
        name = "Shaldred, the Aftermath", cost = "{2}{B}{B}", type = "Legendary Creature — Phyrexian Praetor", pt = "4/5",
        text = """
            Deathtouch
            Whenever you draw a card, you gain 2 life.
            Whenever an opponent draws a card, they lose 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.Draw(PlayerFilter.You)), gainLife(2)),
        triggered(on(EventPattern.Draw(PlayerFilter.Opponent)), loseLife(2, Players.EventPlayer)),
    )

    // CR 702.41a: affinity; CR 702.20a: vigilance
    @Test
    fun argolanPhalanx() = card(
        name = "Argolan Phalanx", cost = "{5}{W}", type = "Creature — Human Kor Soldier", pt = "4/4",
        text = """
            Affinity for creatures (This spell costs {1} less to cast for each creature you control.)
            Vigilance (Attacking doesn't cause this creature to tap.)
        """,
    ) parsesTo abilities(
        Keyword.Affinity(forWhat = creature, printed = "creatures"),
        Keyword.Vigilance,
    )

    // CR 701.20a (look), 701.20e, 400.7, 702.6a (equip), 702.122a (crew)
    @Test
    fun astarBearerOfBanners() = card(
        name = "Astar, Bearer of Banners", cost = "{2}{R}{W}", type = "Legendary Creature — Human Warrior", pt = "4/4",
        text = """
            When Astar enters, look at the top seven cards of your library. You may reveal an Equipment or Vehicle card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Equipment you control have equip {1}.
            Vehicles you control have crew 1.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(sub("Equipment"), sub("Vehicle")), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        static(has(equip("{1}")), affects = Objects.Each(and(sub("Equipment"), you))),
        static(has(Keyword.Crew(1)), affects = Objects.Each(and(sub("Vehicle"), you))),
    )

    // CR 701.20a, 401.4 (bottom in random order)
    @Test
    fun blossomingHedgewall() = card(
        name = "Blossoming Hedgewall", cost = "{1}{G}", type = "Creature — Plant Wall", pt = "0/2",
        text = """
            Defender
            When this creature enters, look at the top six cards of your library. You may reveal a land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 700.4, 603.10a, 701.20a
    @Test
    fun tarsaLookout() = card(
        name = "Tarsa Lookout", cost = "{2}{U}{U}", type = "Creature — Human Pirate", pt = "3/2",
        text = """
            Flying
            When this creature dies, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Zone.Graveyard)),
    )
}
