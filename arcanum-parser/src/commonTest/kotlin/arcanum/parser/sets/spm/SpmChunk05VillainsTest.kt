package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 05: Villains and connive (CR 701.50): Villains counted and pumped, tokens exiled when their maker leaves (CR 603.7), base power and toughness while the graveyard is full, maximum hand size and drawing to it.
 */
class SpmChunk05VillainsTest {

    @Test
    fun carapaxInheritedOutlaw() = card(
        name = "Carapax, Inherited Outlaw",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Rogue Villain",
        pt = "3/3",
        text = """
            Flying
            {1}{U}, Exile this card from your graveyard: Put a +1/+1 counter on target creature. It gains flying until end of turn. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(
            cost(mana("{1}{U}"), CostPart.Exile(what = CostObjects.Self, from = Zone.Graveyard)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            untilEndOfTurn(target("creature"), has(Keyword.Flying)),
            targets = listOf(slot("creature", creature)),
            rules = asSorcery,
        ),
    )

    @Test
    fun professorGrappleBalefulScholar() = card(
        name = "Professor Grapple, Baleful Scholar",
        cost = "{4}{U}",
        type = "Legendary Creature — Human Scientist Villain",
        pt = "4/5",
        text = """
            As long as there are eight or more cards in your graveyard, Professor Grapple has base power and toughness 8/8.
            As long as you control another Villain, Professor Grapple has hexproof. (He can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.SetPowerToughness(power = n(8), toughness = n(8)),
            condition = Condition.Exists(filter = and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 8),
        ),
        static(
            has(Keyword.Hexproof),
            condition = Condition.Exists(filter = and(another, sub("Villain"), you), atLeast = 1),
        ),
    )

    @Test
    fun grappleSLackeys() = card(
        name = "Grapple's Lackeys",
        cost = "{2}{U}",
        type = "Creature — Human Villain",
        pt = "2/1",
        text = """
            Flash
            Whenever this creature attacks, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            doing(Effect.Action(KeywordAction.Connive(objects = Objects.Self, count = n(1)))),
        ),
    )

    @Test
    fun hoveringClawbot() = card(
        name = "Hovering Clawbot",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Villain",
        pt = "1/1",
        text = """
            Flying
            Whenever another Villain you control enters, put a +1/+1 counter on this creature. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.ZoneChange(objects = and(another, sub("Villain"), you), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            perTurn = 1,
        ),
    )

    @Test
    fun phantasmoLordOfMirages() = card(
        name = "Phantasmo, Lord of Mirages",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Villain",
        pt = "3/3",
        text = "When Phantasmo enters, create a 3/3 blue Illusion Villain creature token for each nontoken Villain you control. Exile those tokens when Phantasmo leaves the battlefield.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Illusion Villain Token",
                            colorIndicator = listOf(Color.Blue),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Illusion"), Subtype("Villain")),
                            power = PrintedNumber.Fixed(3),
                            toughness = PrintedNumber.Fixed(3),
                        ),
                    ),
                    count = Value.Count(Objects.Each(and(not(ObjectFilter.Category(ObjectCategory.Token)), sub("Villain"), you))),
                    controller = Players.You,
                    bind = "tokens",
                )),
            doing(Effect.CreateDelayedTrigger(
                    trigger = on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
                    instructions = Instructions(steps = listOf(doing(Effect.Move(objects = Objects.Bound("tokens"), to = Destination(Zone.Exile))))),
                )),
        ),
    )

    @Test
    fun phantasmoSApparition() = card(
        name = "Phantasmo's Apparition",
        cost = "{1}{U}",
        type = "Creature — Illusion Villain",
        pt = "1/3",
        text = """
            Flying, vigilance
            Whenever this creature attacks, mill a card. (Put the top card of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            doing(Effect.Mill(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun stingerVenomousBrawler() = card(
        name = "Stinger, Venomous Brawler",
        cost = "{3}{B}",
        type = "Legendary Creature — Scorpion Human Villain",
        pt = "3/3",
        text = """
            Deathtouch
            At the beginning of your end step, if a creature died this turn, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Action(KeywordAction.Connive(objects = target("creature"), count = n(1)))),
            targets = listOf(slot("creature", and(creature, you))),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.ZoneChange(objects = creature, from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun gravelordLifelongFelon() = card(
        name = "Gravelord, Lifelong Felon",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Villain",
        pt = "2/2",
        text = """
            When Gravelord enters, return target Villain card from your graveyard to your hand.
            Villain spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(objects = target("card"), to = Destination(Zone.Hand))),
            targets = listOf(slot("card", and(sub("Villain"), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
        ),
        static(
            Mechanic.CostChange(
                what = and(sub("Villain"), ObjectFilter.Category(ObjectCategory.Spell), you),
                change = CostChange.Less(mana = ManaCost.parse("{1}")!!, times = n(1)),
            ),
        ),
    )

    @Test
    fun ravenousOoze() = card(
        name = "Ravenous Ooze",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            This spell costs {2} less to cast if you control a Villain.
            Destroy target creature. You gain 2 life.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1))),
            condition = Condition.Exists(filter = and(sub("Villain"), you), atLeast = 1),
        ),
        spell(
            doing(Effect.Destroy(target("creature"))),
            doing(Effect.GainLife(players = Players.You, amount = n(2))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun syndicateSentry() = card(
        name = "Syndicate Sentry",
        cost = "{1}{U/B}",
        type = "Creature — Human Rogue Villain",
        pt = "0/3",
        text = "When this creature enters, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Action(KeywordAction.Connive(objects = target("creature"), count = n(1)))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun nightprowlGauntletedBurglar() = card(
        name = "Nightprowl, Gauntleted Burglar",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Human Rogue Villain",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever another Villain you control enters, Nightprowl connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(
            on(EventPattern.ZoneChange(objects = and(another, sub("Villain"), you), to = Zone.Battlefield)),
            doing(Effect.Action(KeywordAction.Connive(objects = Objects.Self, count = n(1)))),
        ),
    )

    @Test
    fun carrionWingPlottingScrapper() = card(
        name = "Carrion Wing, Plotting Scrapper",
        cost = "{5}{U/B}",
        type = "Legendary Creature — Human Artificer Villain",
        pt = "4/6",
        text = """
            Flying
            Whenever Carrion Wing attacks, other Villains you control gain flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            untilEndOfTurn(Objects.Each(and(another, sub("Villain"), you)), has(Keyword.Flying)),
        ),
    )

    @Test
    fun volatileTrial() = card(
        name = "Volatile Trial",
        cost = "{1}{U}",
        type = "Instant",
        text = "Target player draws a card, then up to one target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Draw(players = Players.Target("player"), count = n(1))),
            doing(Effect.Action(KeywordAction.Connive(objects = target("creature"), count = n(1)))),
            targets = listOf(
                TargetSlot(name = "player", what = TargetKind.Players(PlayerFilter.Any), min = n(1), max = n(1)),
                slot("creature", and(creature, you), min = 0),
            ),
        ),
    )

    @Test
    fun clockworkGoon() = card(
        name = "Clockwork Goon",
        cost = "{3}",
        type = "Artifact Creature — Human Robot Villain",
        pt = "2/1",
        text = "When this creature enters, exile up to one target card from a graveyard. Target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(objects = target("card"), to = Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.Action(KeywordAction.Connive(objects = target("creature"), count = n(1)))),
            targets = listOf(
                slot("card", and(ObjectFilter.Category(ObjectCategory.Card), inZone(Zone.Graveyard)), min = 0),
                slot("creature", and(creature, you)),
            ),
        ),
    )

    @Test
    fun professorGrappleGrandSchemer() = card(
        name = "Professor Grapple, Grand Schemer",
        cost = "{5}{U}{B}",
        type = "Legendary Creature — Human Scientist Villain",
        pt = "4/8",
        text = """
            Other Villains you control get +2/+2.
            Your maximum hand size is eight.
            At the beginning of your end step, if you have fewer than eight cards in hand, draw cards equal to the difference.
        """,
    ) parsesTo abilities(
        static(pt(2, 2), affects = Objects.Each(and(another, sub("Villain"), you))),
        playerStatic(Mechanic.MaximumHandSize(n(8))),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Draw(players = Players.You, count = Value.Difference(from = n(8), minus = Value.HandSize(Players.You)))),
            interveningIf = Condition.Compare(left = Value.HandSize(Players.You), comparison = Comparison.Less, right = n(8)),
        ),
    )

    @Test
    fun covertOozeOperative() = card(
        name = "Covert Ooze Operative",
        cost = "{2}{B}",
        type = "Legendary Creature — Symbiote Soldier Hero",
        pt = "2/3",
        text = """
            Flash
            Menace
            Whenever another nontoken creature you control dies, you draw a card and lose 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Menace,
        triggered(
            on(EventPattern.ZoneChange(
                    objects = and(another, not(ObjectFilter.Category(ObjectCategory.Token)), creature, you),
                    from = Zone.Battlefield,
                    to = Zone.Graveyard,
                )),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.LoseLife(players = Players.You, amount = n(1))),
        ),
    )
}
