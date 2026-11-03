package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 03: warp (CR 702.185a) on white and blue cards, with the enters abilities warped creatures use: exile until it leaves, mana spent to cast it, look-and-pick, drawing one more card, entering triggers happening twice.
 */
class EoeChunk03WarpWhiteBlueTest {

    @Test
    fun everyPathProwler() = card(
        name = "Every-Path Prowler",
        cost = "{3}{W}",
        type = "Creature — Drix Assassin",
        pt = "2/3",
        text = """
            When this creature enters, exile up to one target non-Assassin creature until this creature leaves the battlefield.
            Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("creature"), to = Destination(zone = Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("creature", and(not(sub("Assassin")), creature), min = 0)),
        ),
        warp("{1}{W}"),
    )

    @Test
    fun celestineRetriever() = card(
        name = "Celestine Retriever",
        cost = "{3}{W}{W}",
        type = "Creature — Angel Warrior",
        pt = "5/4",
        text = """
            Flying
            When this creature enters, return target noncreature, nonland permanent card with mana value X or less from your graveyard to the battlefield, where X is the amount of mana spent to cast this creature.
            Warp {2}{W}
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield))),
            targets = listOf(
                slot(
                    "card",
                    and(
                        not(creature),
                        not(land),
                        or(
                            artifact,
                            ObjectFilter.Type(CardType.Battle),
                            creature,
                            enchantment,
                            land,
                            ObjectFilter.Type(CardType.Planeswalker),
                        ),
                        ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = Value.ManaSpent(of = Objects.Self)),
                        inZone(Zone.Graveyard),
                        ownedByYou,
                    ),
                ),
            ),
        ),
        warp("{2}{W}"),
    )

    @Test
    fun cipherbreakerMutt() = card(
        name = "Cipherbreaker Mutt",
        cost = "{2}{U}",
        type = "Creature — Dog",
        pt = "2/1",
        text = """
            When this creature enters, look at the top two cards of your library. Put one into your hand and the other into your graveyard.
            Warp {2}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true),
                    players = Players.You,
                    bind = "looked",
                ),
            ),
            Step.Choose(
                choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = bound("looked")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Hand))),
            doing(
                Effect.Move(
                    objects = Objects.Except(base = bound("looked"), minus = bound("chosen")),
                    to = Destination(zone = Zone.Graveyard),
                ),
            ),
        ),
        warp("{2}{U}"),
    )

    @Test
    fun glorifiedDawnchild() = card(
        name = "Glorified Dawnchild",
        cost = "{3}{W}{W}",
        type = "Creature — Angel Wizard",
        pt = "4/5",
        text = """
            Flying, lifelink
            If one or more tokens would be created under your control, twice that many of those tokens are created instead.
            Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        static(
            Mechanic.Replacement(event = EventPattern.CreateToken(tokens = you), modify = listOf(Modification.MultiplyAmount(factor = 2))),
        ),
        warp("{1}{W}"),
    )

    @Test
    fun orlenaLedByStarlight() = card(
        name = "Orlena, Led by Starlight",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/3",
        text = """
            Whenever Orlena or another creature or artifact you control enters, you gain 1 life.
            At the beginning of your end step, draw a card if you've gained 3 or more life this turn.
            Warp {W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = or(isSelf, and(not(isSelf), or(creature, artifact), you)), to = Zone.Battlefield)),
            doing(Effect.GainLife(players = Players.You, amount = n(1))),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            Step.If(
                condition = Condition.HappenedThisTurn(
                    event = EventPattern.GainLife(players = PlayerFilter.You),
                    atLeast = 3,
                    measure = Measure.Amount,
                ),
                then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
            ),
        ),
        warp("{W}"),
    )

    @Test
    fun radiantSquireCommander() = card(
        name = "Radiant Squire-Commander",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "3/2",
        text = """
            When this creature enters, create a 1/1 white Human Soldier creature token.
            Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = soldier, count = n(1), controller = Players.You))),
        warp("{1}{W}"),
    )

    @Test
    fun clockworkMedusoid() = card(
        name = "Clockwork Medusoid",
        cost = "{4}{U}{U}",
        type = "Artifact Creature — Robot Jellyfish",
        pt = "5/5",
        text = """
            When this creature enters, tap target artifact or creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Warp {2}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Tap(objects = target("permanent"))),
            doing(Effect.PutCounters(objects = target("permanent"), kind = CounterKind.Stun, amount = n(1))),
            targets = listOf(slot("permanent", and(or(artifact, creature), opponents))),
        ),
        warp("{2}{U}"),
    )

    @Test
    fun probabilitySphinx() = card(
        name = "Probability Sphinx",
        cost = "{3}{U}{U}",
        type = "Creature — Sphinx",
        pt = "4/6",
        text = """
            Flying
            When this creature enters, draw a card.
            As long as you have one or fewer cards in hand, if you would draw one or more cards, you draw that many cards plus one instead.
            Warp {1}{U}
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Draw(players = Players.You, count = n(1)))),
        static(
            Mechanic.Replacement(
                event = EventPattern.Draw(players = PlayerFilter.You),
                modify = listOf(Modification.AddAmount(amount = n(1))),
            ),
            condition = Condition.Not(condition = Condition.Exists(filter = and(inZone(Zone.Hand), ownedByYou), atLeast = 2)),
        ),
        warp("{1}{U}"),
    )

    @Test
    fun beamswordTrooper() = card(
        name = "Beamsword Trooper",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            When this creature enters, put a +1/+1 counter on target creature you control.
            Whenever a nontoken creature you control with a +1/+1 counter on it dies, create a 1/1 white Human Soldier creature token.
            Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        triggered(
            on(
                EventPattern.ZoneChange(
                    objects = and(not(token), creature, you, ObjectFilter.HasCounter(kind = p1p1, atLeast = 1)),
                    from = Zone.Battlefield,
                    to = Zone.Graveyard,
                ),
            ),
            doing(Effect.CreateToken(token = soldier, count = n(1), controller = Players.You)),
        ),
        warp("{1}{W}"),
    )

    @Test
    fun malignFrostwright() = card(
        name = "Malign Frostwright",
        cost = "{2}{U}",
        type = "Creature — Jellyfish Wizard",
        pt = "2/3",
        text = """
            When this creature enters, target creature an opponent controls gets -3/-0 until end of turn.
            Warp {U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, untilEndOfTurn(target("creature"), pt(-3, 0)), targets = listOf(slot("creature", and(creature, opponents)))),
        warp("{U}"),
    )

    @Test
    fun riftsurgeLeviathan() = card(
        name = "Riftsurge Leviathan",
        cost = "{4}{U}",
        type = "Creature — Whale",
        pt = "3/5",
        text = """
            Flying
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            Warp {1}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.Surveil(players = Players.You, count = n(2)))),
        warp("{1}{U}"),
    )

    @Test
    fun nebulaHerdwarden() = card(
        name = "Nebula Herdwarden",
        cost = "{3}{W}{W}",
        type = "Creature — Angel",
        pt = "3/2",
        text = """
            Flying
            When this creature enters, search your library for a basic Plains card or a creature card with mana value 1 or less, reveal it, put it into your hand, then shuffle.
            Warp {1}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            doing(
                Effect.Search(
                    library = Players.You,
                    filter = and(
                        or(and(ObjectFilter.Super(supertype = Supertype.Basic), sub("Plains")), creature),
                        ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(1)),
                    ),
                    max = n(1),
                    reveal = true,
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                ),
            ),
            doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Hand))),
            doing(Effect.Shuffle(library = Players.You)),
        ),
        warp("{1}{W}"),
    )

    @Test
    fun nebulaChorister() = card(
        name = "Nebula Chorister",
        cost = "{3}{U}",
        type = "Creature — Human Bard",
        pt = "3/4",
        text = """
            If a permanent entering the battlefield causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
            Warp {1}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.TriggersAdditionalTime(
                event = EventPattern.ZoneChange(objects = permanentFilter, to = Zone.Battlefield),
                sources = and(permanentFilter, you),
            ),
        ),
        warp("{1}{U}"),
    )

    @Test
    fun cometcoiler() = card(
        name = "Cometcoiler",
        cost = "{5}{U}{U}",
        type = "Creature — Leviathan",
        pt = "7/7",
        text = """
            Whenever a creature you control deals combat damage to a player, you may draw that many cards.
            Warp {2}{U}{U} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Damage(source = and(creature, you), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            Step.May(steps = listOf(doing(Effect.Draw(players = Players.You, count = Value.EventAmount))), player = Players.You),
        ),
        warp("{2}{U}{U}"),
    )

    @Test
    fun loombladeAugmenter() = card(
        name = "Loomblade Augmenter",
        cost = "{5}{W}",
        type = "Creature — Drix Artificer",
        pt = "3/4",
        text = """
            When this creature enters, put a +1/+1 counter on each of up to two target creatures.
            Warp {2}{W} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.PutCounters(objects = target("creatures"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creatures", creature, min = 0, max = 2)),
        ),
        warp("{2}{W}"),
    )
}
