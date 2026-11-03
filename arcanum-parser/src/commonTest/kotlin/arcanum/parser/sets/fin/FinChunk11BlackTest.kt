package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
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
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Final Fantasy chunk 11: black cards: a copy of a creature card made a 5/5 black Demon (CR 707.9b), dying creatures exiled instead with
 * life gained and returned from exile later (linked abilities, CR 607.2a), attacking despite defender while it has a counter, returning with
 * additional counters (CR 122.6), more tokens when cast from a graveyard, giving control of this creature away (CR 613.1b) with "that many"
 * from the damage, exiling the top of each opponent's library, a Vehicle becoming a Spirit artifact creature, playing a card exiled from an
 * opponent with mana of any type, each player sacrificing half their creatures (CR 107.1a, rounded down), sacrifice triggers, creatures
 * that died this turn counted.
 */
class FinChunk11BlackTest {
    // CR 707.9b
    @Test
    fun ardaanTheUsurper() = card(
        name = "Ardaan, the Usurper",
        cost = "{5}{B}{B}{B}",
        type = "Legendary Creature — Elder Human Noble",
        pt = "4/4",
        text = """
            Demons you control have menace, lifelink, and haste.
            Starscourge — At the beginning of combat on your turn, exile up to one target creature card from a graveyard. If you exiled a card this way, create a token that's a copy of that card, except it's a 5/5 black Demon.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Menace, Keyword.Lifelink, Keyword.Haste), affects = Objects.Each(and(sub("Demon"), you))),
        triggered(beginningOfCombatOnYourTurn, move(target("card"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.NonEmpty("exiled"), doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("exiled"), except = listOf(
                CopyException.PowerToughness(5, 5), CopyException.Colors(listOf(ColorRef.Literal(Color.Black))),
                CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Demon"))), replaceSubtypes = true))), n(1), Players.You))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard)), min = 0)), abilityWord = "Starscourge"),
    )

    /** "If a nontoken creature an opponent controls would die, instead exile it and you gain 2 life." with the cards "exiled with" it. */
    // CR 614.1a, 607.2a
    @Test
    fun theShadowCrystal() = card(
        name = "The Shadow Crystal",
        cost = "{2}{B}{B}",
        type = "Legendary Artifact",
        text = """
            Black spells you cast cost {1} less to cast.
            If a nontoken creature an opponent controls would die, instead exile it and you gain 2 life.
            {4}{B}{B}, {T}: Put target creature card exiled with The Shadow Crystal onto the battlefield tapped under your control with two additional +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.OfColor(ColorRef.Literal(Color.Black)), spellFilter, you), arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(not(token), creature, opponents), from = Zone.Battlefield, to = Zone.Graveyard),
            instead = Instructions(steps = listOf(
                doing(Effect.Move(Objects.EventObject(EventRole.Before), Destination(Zone.Exile), postBind = "exiled")),
                doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
                gainLife(2))))),
        activated(cost(mana("{4}{B}{B}"), tap),
            doing(Effect.Move(target("card"), Destination(Zone.Battlefield, tapped = true, controller = Players.You, counters = listOf(CountersToPut(p1p1, n(2)))))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Exile), ObjectFilter.Is(Objects.Remembered("exiled")))))),
    )

    // CR 702.3b
    @Test
    fun wardingWall() = card(
        name = "Warding Wall",
        cost = "{1}{B}",
        type = "Artifact Creature — Demon Wall",
        pt = "3/3",
        text = """
            Defender
            Menace (This creature can't be blocked except by two or more creatures.)
            As long as this creature has a counter on it, it can attack as though it didn't have defender.
            {5}{B}: Put two +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Keyword.Menace,
        static(Mechanic.CanAttackDespiteDefender, condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(null, atLeast = 1))),
        activated(manaCost("{5}{B}"), counters(Objects.Self, 2)),
    )

    // CR 122.6
    @Test
    fun wickedReawakening() = card(
        name = "Wicked Reawakening",
        cost = "{4}{B}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to the battlefield with two additional +1/+1 counters on it.",
    ) parsesTo abilities(
        spell(doing(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(p1p1, n(2)))))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    @Test
    fun theLastDays() = card(
        name = "The Last Days",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = """
            Create two tapped 2/2 black Horror creature tokens. If this spell was cast from a graveyard, instead create X of those tokens, where X is the number of creature cards in your graveyard.
            Flashback {4}{B}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(ifThen(Condition.CastFrom(Zone.Graveyard, Objects.Self), create(horror, count(inYourGraveyard(creature)), tapped = true),
            otherwise = listOf(create(horror, n(2), tapped = true)))),
        Keyword.Flashback(manaCost("{4}{B}{B}")),
    )

    /** "that player gains control of Kain. If they do, you draw that many cards …": the combat damage dealt (CR 120.3). */
    @Test
    fun kaelTraitorousLancer() = card(
        name = "Kael, Traitorous Lancer",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Knight",
        pt = "2/4",
        text = """
            Jump — During your turn, Kael has flying.
            Whenever Kael deals combat damage to a player, that player gains control of Kael. If they do, you draw that many cards, create that many tapped Treasure tokens, then lose that much life.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.YourTurn).copy(abilityWord = "Jump"),
        triggered(combatDamageToAPlayer,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.EventPlayer)), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))),
                doing(Effect.Draw(Players.You, Value.EventAmount)),
                create(treasure, Value.EventAmount, tapped = true),
                doing(Effect.LoseLife(Players.You, Value.EventAmount)))),
    )

    @Test
    fun rottingBreath() = card(
        name = "Rotmaw",
        cost = "{4}{B}{B}",
        type = "Creature — Plant Horror",
        pt = "4/4",
        text = """
            Bad Breath — When this creature enters, each opponent discards a card, loses 2 life, and exiles the top three cards of their library.
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, discard(1, Players.Each(PlayerFilter.Opponent)), loseLife(2, Players.Each(PlayerFilter.Opponent)),
            move(Objects.LibraryCards(Players.Each(PlayerFilter.Opponent), n(3), fromTop = true), Zone.Exile), abilityWord = "Bad Breath"),
        Keyword.Typecycling(sub("Swamp"), "Swampcycling", manaCost("{2}")),
    )

    // CR 205.1b, 301.7b (a Vehicle that's a creature)
    @Test
    fun ghostlyLocomotive() = card(
        name = "Ghostly Locomotive",
        cost = "{3}{B}",
        type = "Artifact — Vehicle",
        pt = "4/4",
        text = """
            Trample
            Sacrifice another artifact or creature: Put a +1/+1 counter on this Vehicle. It becomes a Spirit artifact creature in addition to its other types until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(sacrificeA(and(another, or(artifact, creature)))), counters(Objects.Self),
            untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))))),
    )

    /** "you may play the exiled card this turn, and mana of any type can be spent to cast it" (CR 601.2h). */
    @Test
    fun renzAndRook() = card(
        name = "Renz and Rook",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Assassin",
        pt = "2/1",
        text = """
            Menace
            Whenever Renz and Rook deals combat damage to a player, exile the top card of that player's library. Then you may sacrifice another creature or artifact. If you do, you may play the exiled card this turn, and mana of any type can be spent to cast it.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(combatDamageToAPlayer,
            move(Objects.LibraryCards(Players.EventPlayer, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Pay(cost(sacrificeA(and(another, or(creature, artifact)))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, manaAsAnyColor = true, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)))),
    )

    /** "each player sacrifices half the non-God creatures they control of their choice, rounded down" (CR 107.1a). */
    @Test
    fun zodrakUmbralGod() = card(
        name = "Zodrak, Umbral God",
        cost = "{B}{B}{B}{B}{B}",
        type = "Legendary Creature — God",
        pt = "5/5",
        text = """
            Indestructible
            When Zodrak enters, each player sacrifices half the non-God creatures they control of their choice, rounded down.
            Whenever a player sacrifices another creature, put a +1/+1 counter on Zodrak.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        triggered(entersSelf, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", steps = listOf(
            Step.Choose(Choice.Objects(nonGodTheirs, Value.Half(Value.Count(Objects.Each(nonGodTheirs)), roundUp = false),
                Value.Half(Value.Count(Objects.Each(nonGodTheirs)), roundUp = false)), bind = "sacrificed", chooser = Players.Bound("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ), together = true)),
        triggered(on(EventPattern.Sacrifice(and(another, creature))), counters(Objects.Self)),
    )

    @Test
    fun fatalEmbrace() = card(
        name = "Fatal Embrace",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = "Destroy target creature an opponent controls. Then draw a card for each creature that died this turn.",
    ) parsesTo abilities(
        spell(destroy(target("creature")),
            doing(Effect.Draw(Players.You, Value.EventsThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), Measure.Objects))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 702.15b (a keyword during your turn)
    @Test
    fun knifeGoblin() = card(
        name = "Knife Goblin",
        cost = "{B}",
        type = "Creature — Salamander Horror",
        pt = "2/1",
        text = """
            This creature enters tapped with a stun counter on it. (If it would become untapped, remove a stun counter from it instead.)
            Chef's Knife — During your turn, this creature has first strike and deathtouch.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(arcanum.api.Modification.EntersTapped, arcanum.api.Modification.EntersWithCounters(stun, n(1))))),
        static(has(Keyword.FirstStrike, Keyword.Deathtouch), condition = Condition.YourTurn).copy(abilityWord = "Chef's Knife"),
    )

    @Test
    fun underwayRat() = card(
        name = "Underway Rat",
        cost = "{1}{B}",
        type = "Creature — Rat",
        pt = "2/2",
        text = "Rat Tail — When this creature dies, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(triggered(diesSelf, createTreasure, abilityWord = "Rat Tail"))

    private val horror = creatureToken("Horror", listOf(Color.Black), 2, 2)
    private val nonGodTheirs = and(not(sub("God")), creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))
}
